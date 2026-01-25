package dev.openrune.central.packet

import dev.openrune.central.AppState
import dev.openrune.central.crypto.Ed25519
import dev.openrune.central.packet.PacketProtocol.MAX_FRAME_BYTES
import dev.openrune.central.packet.PacketProtocol.TYPE_AUTH
import dev.openrune.central.packet.PacketProtocol.TYPE_AUTH_FAIL
import dev.openrune.central.packet.PacketProtocol.TYPE_AUTH_OK
import dev.openrune.central.packet.PacketProtocol.TYPE_DATA
import dev.openrune.central.packet.PacketProtocol.TYPE_PUSH
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class CentralReceivedPacket(
    val worldId: Int,
    val opcode: Int,
    val payload: ByteArray,
    val seq: Long
)

class CentralPacketServerHandle internal constructor(
    private val boss: EventLoopGroup,
    private val worker: EventLoopGroup,
    private val channel: Channel,
) {
    fun close() {
        try {
            channel.close().syncUninterruptibly()
        } finally {
            boss.shutdownGracefully().syncUninterruptibly()
            worker.shutdownGracefully().syncUninterruptibly()
        }
    }
}

/**
 * Packet-based Netty server for world servers.
 *
 * - Each connection must AUTH with the world's Ed25519 key.
 * - Each DATA packet is signed with the world's key and must have monotonic seq.
 */
object CentralPacketServer {
    private val logger = LoggerFactory.getLogger(CentralPacketServer::class.java)

    private val channelsByWorldId = ConcurrentHashMap<Int, Channel>()
    val incoming: LinkedBlockingQueue<CentralReceivedPacket> = LinkedBlockingQueue()

    fun start(
        host: String = "0.0.0.0",
        port: Int = 43595,
    ): CentralPacketServerHandle {
        val boss = NioEventLoopGroup(1)
        val worker = NioEventLoopGroup()

        val bootstrap =
            ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel::class.java)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val p = ch.pipeline()
                            p.addLast(
                                "frameDecoder",
                                LengthFieldBasedFrameDecoder(MAX_FRAME_BYTES, 0, 4, 0, 4)
                            )
                            p.addLast("frameEncoder", LengthFieldPrepender(4))
                            p.addLast("handler", ServerHandler())
                        }
                    }
                )

        val bind: ChannelFuture = bootstrap.bind(host, port).syncUninterruptibly()
        val ch = bind.channel()
        logger.info("Central packet server listening on {}:{}", host, port)
        return CentralPacketServerHandle(boss, worker, ch)
    }

    /**
     * Sends an unsigned packet from central -> world (requires the world to be connected).
     */
    fun sendToWorld(worldId: Int, opcode: Int, payload: ByteArray = byteArrayOf()): Boolean {
        val ch = channelsByWorldId[worldId] ?: return false
        if (!ch.isActive) return false

        val seq = ServerSequences.next(worldId)
        val buf = ch.alloc().buffer(1 + 1 + 8 + 4 + 4 + payload.size)
        buf.writeByte(TYPE_PUSH.toInt())
        buf.writeByte(PacketProtocol.VERSION.toInt())
        buf.writeLong(seq)
        buf.writeInt(opcode)
        buf.writeInt(payload.size)
        buf.writeBytes(payload)
        ch.writeAndFlush(buf)
        return true
    }

    private object ServerSequences {
        private val seqByWorld = ConcurrentHashMap<Int, AtomicLong>()
        fun next(worldId: Int): Long = seqByWorld.computeIfAbsent(worldId) { AtomicLong(1L) }.getAndIncrement()
    }

    private class ServerHandler : SimpleChannelInboundHandler<ByteBuf>() {
        private var authedWorldId: Int? = null
        private var expectedSeq: Long = 1L

        override fun channelInactive(ctx: ChannelHandlerContext) {
            val wid = authedWorldId
            if (wid != null) {
                channelsByWorldId.remove(wid, ctx.channel())
                logger.info("World {} disconnected from packet server", wid)
            }
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
            val type = msg.readByte()
            val version = msg.readByte()
            if (version != PacketProtocol.VERSION) {
                ctx.close()
                return
            }

            when (type) {
                TYPE_AUTH -> handleAuth(ctx, msg)
                TYPE_DATA -> handleData(ctx, msg)
                else -> ctx.close()
            }
        }

        private fun handleAuth(ctx: ChannelHandlerContext, msg: ByteBuf) {
            if (authedWorldId != null) {
                ctx.close()
                return
            }

            val worldId = msg.readInt()
            val timestamp = msg.readLong()
            val nonce = ByteArray(16)
            msg.readBytes(nonce)
            val signature = readUtf8WithU16Len(msg)

            val world = AppState.worldsById[worldId]
            if (world == null || world.authPublicKey.isBlank()) {
                logger.warn("AUTH failed: unknown worldId={} or missing public key", worldId)
                writeAuthFail(ctx)
                return
            }

            // Anti-replay window (5 minutes)
            val now = System.currentTimeMillis()
            if (abs(now - timestamp) > 5 * 60 * 1000L) {
                logger.warn("AUTH failed: replay window worldId={} ts={} now={}", worldId, timestamp, now)
                writeAuthFail(ctx)
                return
            }

            val ok =
                Ed25519.verify(
                    world.authPublicKey,
                    buildPacketAuthPayload(PacketProtocol.VERSION, worldId, timestamp, nonce),
                    signature
                )

            if (!ok) {
                logger.warn("AUTH failed: bad signature worldId={}", worldId)
                writeAuthFail(ctx)
                return
            }

            authedWorldId = worldId
            expectedSeq = 1L
            channelsByWorldId[worldId] = ctx.channel()
            logger.info("World {} authenticated on packet server", worldId)
            writeAuthOk(ctx)
        }

        private fun handleData(ctx: ChannelHandlerContext, msg: ByteBuf) {
            val worldId = authedWorldId ?: run {
                ctx.close()
                return
            }

            val seq = msg.readLong()
            val opcode = msg.readInt()
            val len = msg.readInt()
            if (len < 0 || len > MAX_FRAME_BYTES) {
                ctx.close()
                return
            }
            val payload = ByteArray(len)
            msg.readBytes(payload)
            val signature = readUtf8WithU16Len(msg)

            if (seq != expectedSeq) {
                logger.warn("DATA seq mismatch: worldId={} got={} expected={}", worldId, seq, expectedSeq)
                ctx.close()
                return
            }

            val world = AppState.worldsById[worldId] ?: run {
                ctx.close()
                return
            }

            val ok =
                signature.isNotEmpty() && Ed25519.verify(
                    world.authPublicKey,
                    buildPacketDataPayload(PacketProtocol.VERSION, worldId, seq, opcode, payload),
                    signature
                )

            if (!ok) {
                logger.warn("DATA bad signature: worldId={} seq={} opcode={} payloadLen={}", worldId, seq, opcode, payload.size)
                ctx.close()
                return
            }

            expectedSeq++
            incoming.offer(CentralReceivedPacket(worldId, opcode, payload, seq))
            logger.debug("DATA accepted: worldId={} seq={} opcode={} payloadLen={}", worldId, seq, opcode, payload.size)
        }

        private fun writeAuthOk(ctx: ChannelHandlerContext) {
            val buf = ctx.alloc().buffer(2)
            buf.writeByte(TYPE_AUTH_OK.toInt())
            buf.writeByte(PacketProtocol.VERSION.toInt())
            ctx.writeAndFlush(buf)
        }

        private fun writeAuthFail(ctx: ChannelHandlerContext) {
            val buf = ctx.alloc().buffer(2)
            buf.writeByte(TYPE_AUTH_FAIL.toInt())
            buf.writeByte(PacketProtocol.VERSION.toInt())
            ctx.writeAndFlush(buf).addListener { ctx.close() }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            ctx.close()
        }
    }
}

private fun readUtf8WithU16Len(buf: ByteBuf): String {
    val len = buf.readUnsignedShort()
    if (len == 0) return ""
    val bytes = ByteArray(len)
    buf.readBytes(bytes)
    return String(bytes, StandardCharsets.UTF_8)
}

