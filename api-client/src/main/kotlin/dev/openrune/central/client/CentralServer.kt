package dev.openrune.central.client

import dev.openrune.central.client.packet.ClientPacketCall
import dev.openrune.central.client.packet.ClientPacketDispatcher
import dev.openrune.central.client.packet.ClientPacketHandler
import dev.openrune.central.client.packet.ClientPacketRouter
import dev.openrune.central.crypto.Ed25519
import dev.openrune.central.packet.IncomingPacket
import dev.openrune.central.packet.OutgoingPacket
import dev.openrune.central.packet.PacketProtocol
import dev.openrune.central.packet.buildPacketAuthPayload
import dev.openrune.central.packet.buildPacketDataPayload
import dev.openrune.central.packet.model.OutgoingPacketBody
import dev.openrune.central.packet.registry.OutgoingPackets
import dev.openrune.central.packet.io.PacketWriter
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

sealed interface ConnectionResult {
    enum class Status { CONNECTED, NOT_CONNECTED, FAILED_AUTH }

    val status: Status
    val error: String?

    data class Connected(val connection: CentralConnection) : ConnectionResult {
        override val status: Status = Status.CONNECTED
        override val error: String? = null
    }

    data class NotConnected(override val error: String? = null) : ConnectionResult {
        override val status: Status = Status.NOT_CONNECTED
    }

    data class FailedAuth(override val error: String? = null) : ConnectionResult {
        override val status: Status = Status.FAILED_AUTH
    }
}

object CentralServer {

    fun openConnection(
        host: String,
        port: Int,
        worldId: Int,
        worldPrivateKey: String,
        connectTimeoutMs: Int = 10_000,
        authTimeoutMs: Long = 10_000,
        startListening: Boolean = true
    ): ConnectionResult {
        val res = CentralConnection.connect(host, port, worldId, worldPrivateKey, connectTimeoutMs, authTimeoutMs)
        if (startListening && res is ConnectionResult.Connected) {
            res.connection.startListening()
        }
        return res
    }

    fun openConnection(
        worldId: Int,
        worldPrivateKey: String,
        connectTimeoutMs: Int = 10_000,
        authTimeoutMs: Long = 10_000,
        startListening: Boolean = true
    ): ConnectionResult {
        val host = System.getenv("CENTRAL_PACKET_HOST") ?: "127.0.0.1"
        val port = System.getenv("CENTRAL_PACKET_PORT")?.toIntOrNull() ?: 43595
        return openConnection(host, port, worldId, worldPrivateKey, connectTimeoutMs, authTimeoutMs, startListening)
    }
}

class CentralConnection private constructor(
    private val group: EventLoopGroup,
    private val channel: Channel,
    private val worldId: Int,
    private val worldPrivateKey: String,
) {
    private val seq = AtomicLong(1L)
    private val incoming = LinkedBlockingQueue<IncomingPacket>()

    private val router = ClientPacketRouter()
    private val dispatcher = ClientPacketDispatcher(this, router)

    val packetSender: PacketSender = PacketSender(this)

    // One-shot awaiters for "request -> next response" flows.
    private val awaitersByOpcodeAndRequestId = ConcurrentHashMap<Int, ConcurrentHashMap<Long, CompletableFuture<Any>>>()

    internal fun <T : Any> awaitResponse(type: dev.openrune.central.packet.registry.OutgoingPackets, requestId: Long): CompletableFuture<T> {
        val fut = CompletableFuture<Any>()
        awaitersByOpcodeAndRequestId
            .computeIfAbsent(type.packetId) { ConcurrentHashMap() }[requestId] = fut
        @Suppress("UNCHECKED_CAST")
        return fut as CompletableFuture<T>
    }

    internal fun tryCompleteCorrelated(opcode: Int, decoded: Any): Boolean {
        val requestId =
            when (decoded) {
                is dev.openrune.central.packet.model.OutgoingPacketBody -> decoded.requestId
                else -> return false
            }
        if (requestId == 0L) return false

        val byReqId = awaitersByOpcodeAndRequestId[opcode] ?: return false
        val fut = byReqId.remove(requestId) ?: return false
        fut.complete(decoded)
        return true
    }

    internal fun nextRequestId(): Long = RequestIds.next()

    private object RequestIds {
        private val next = AtomicLong(1L)
        fun next(): Long = next.getAndIncrement()
    }

    fun isActive(): Boolean = channel.isActive

    fun close() {
        try {
            dispatcher.stop()
            channel.close().syncUninterruptibly()
        } finally {
            group.shutdownGracefully().syncUninterruptibly()
        }
    }

    internal fun worldIdUnsafe(): Int = worldId

    /**
     * Start background listening/dispatching of incoming packets to registered handlers.
     * Safe to call multiple times.
     */
    fun startListening() {
        dispatcher.start()
    }

    fun stopListening() {
        dispatcher.stop()
    }

    /**
     * Register a handler by packet body class (auto resolves the opcode via [OutgoingPackets]).
     */
    fun <T : OutgoingPacketBody> on(clazz: kotlin.reflect.KClass<T>, handler: (ClientPacketCall, T) -> Unit) {
        val type =
            OutgoingPackets.byClass(clazz)
                ?: throw IllegalArgumentException("No OutgoingPackets entry for $clazz")

        router.register(
            object : ClientPacketHandler<T>(clazz) {
                override val type: OutgoingPackets = type
                override fun handle(call: ClientPacketCall, packet: T) = handler(call, packet)
            }
        )
    }

    /**
     * Register a handler by packet type (opcode).
     */
    fun <T : OutgoingPacketBody> on(type: OutgoingPackets, clazz: kotlin.reflect.KClass<T>, handler: (ClientPacketCall, T) -> Unit) {
        router.register(
            object : ClientPacketHandler<T>(clazz) {
                override val type: OutgoingPackets = type
                override fun handle(call: ClientPacketCall, packet: T) = handler(call, packet)
            }
        )
    }

    fun sendPacket(opcode: Int, payload: ByteArray = byteArrayOf()) {
        sendPacket(OutgoingPacket(opcode, payload))
    }

    /**
     * Convenience helper to build a payload with [PacketWriter] and send it.
     *
     * This avoids coupling the client module to server-only registries/codecs.
     */
    fun sendPacket(opcode: Int, writePayload: PacketWriter.() -> Unit) {
        val w = PacketWriter().apply(writePayload)
        sendPacket(opcode, w.toByteArray())
    }

    fun sendPacket(packet: OutgoingPacket) {
        val s = seq.getAndIncrement()
        val signature =
            Ed25519.sign(
                worldPrivateKey,
                buildPacketDataPayload(PacketProtocol.VERSION, worldId, s, packet.opcode, packet.payload)
            )

        val buf = channel.alloc().buffer(1 + 1 + 8 + 4 + 4 + packet.payload.size + 2 + signature.length)
        buf.writeByte(PacketProtocol.TYPE_DATA.toInt())
        buf.writeByte(PacketProtocol.VERSION.toInt())
        buf.writeLong(s)
        buf.writeInt(packet.opcode)
        buf.writeInt(packet.payload.size)
        buf.writeBytes(packet.payload)
        writeUtf8WithU16Len(buf, signature)

        channel.writeAndFlush(buf)
    }

    /**
     * Blocks until a packet arrives.
     */
    fun nextPacketBlocking(): IncomingPacket = incoming.take()

    /**
     * Blocks up to [timeoutMs] for a packet; returns null on timeout.
     */
    fun nextPacketBlocking(timeoutMs: Long): IncomingPacket? = incoming.poll(timeoutMs, TimeUnit.MILLISECONDS)

    /**
     * Typed helper: blocks until a known central->world packet arrives, decodes it, and returns it.
     * Unknown opcodes are skipped (so you can roll out new packets safely).
     */
    fun nextPacketBlockingDecoded(): OutgoingPacketBody {
        while (true) {
            val pkt = nextPacketBlocking()
            val type = OutgoingPackets.byId(pkt.opcode) ?: continue
            return type.decode(pkt.payload)
        }
    }

    /**
     * Convenience "keep looking until packets arrive" loop.
     * This blocks the current thread.
     */
    fun receiveLoop(onPacket: (IncomingPacket) -> Unit) {
        while (isActive()) {
            val pkt = nextPacketBlocking()
            onPacket(pkt)
        }
    }

    /**
     * Typed helper: blocking receive loop that decodes known packets.
     */
    fun receiveLoopDecoded(onPacket: (OutgoingPacketBody) -> Unit) {
        while (isActive()) {
            onPacket(nextPacketBlockingDecoded())
        }
    }

    internal fun offerIncoming(pkt: IncomingPacket) {
        incoming.offer(pkt)
    }

    companion object {
        internal fun connect(
            host: String,
            port: Int,
            worldId: Int,
            worldPrivateKey: String,
            connectTimeoutMs: Int,
            authTimeoutMs: Long
        ): ConnectionResult {
            val group = NioEventLoopGroup(1)
            val authResult = CompletableFuture<AuthOutcome>()

            val bootstrap =
                Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel::class.java)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                    .handler(
                        object : ChannelInitializer<SocketChannel>() {
                            override fun initChannel(ch: SocketChannel) {
                                val p = ch.pipeline()
                                p.addLast(
                                    "frameDecoder",
                                    LengthFieldBasedFrameDecoder(
                                        PacketProtocol.MAX_FRAME_BYTES,
                                        0,
                                        4,
                                        0,
                                        4
                                    )
                                )
                                p.addLast("frameEncoder", LengthFieldPrepender(4))
                                // After auth completes successfully, this handler removes itself and lets packets flow.
                                p.addLast("auth", ClientHandler(worldId, worldPrivateKey, authResult))
                            }
                        }
                    )

            val connectFuture = bootstrap.connect(host, port).syncUninterruptibly()
            if (!connectFuture.isSuccess) {
                val msg = connectFuture.cause()?.message ?: "connect failed"
                group.shutdownGracefully().syncUninterruptibly()
                return ConnectionResult.NotConnected(msg)
            }

            val ch = connectFuture.channel()

            val outcome =
                try {
                    authResult.get(authTimeoutMs, TimeUnit.MILLISECONDS)
                } catch (t: Throwable) {
                    ch.close().syncUninterruptibly()
                    group.shutdownGracefully().syncUninterruptibly()
                    return ConnectionResult.NotConnected(t.message ?: "auth wait failed")
                }

            if (outcome != AuthOutcome.OK) {
                ch.close().syncUninterruptibly()
                group.shutdownGracefully().syncUninterruptibly()
                return ConnectionResult.FailedAuth("FAILED_AUTH")
            }

            val client = CentralConnection(group, ch, worldId, worldPrivateKey)
            ch.pipeline().addLast("enqueue", EnqueueHandler(client))
            return ConnectionResult.Connected(client)
        }
    }
}

private class ClientHandler(
    private val worldId: Int,
    private val worldPrivateKey: String,
    private val authResult: CompletableFuture<AuthOutcome>,
) : SimpleChannelInboundHandler<ByteBuf>() {
    private var authed = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        val timestamp = System.currentTimeMillis()
        val nonce = PacketProtocol.randomNonce16()
        val signature =
            Ed25519.sign(
                worldPrivateKey,
                buildPacketAuthPayload(PacketProtocol.VERSION, worldId, timestamp, nonce)
            )

        val buf = ctx.alloc().buffer(1 + 1 + 4 + 8 + 16 + 2 + signature.length)
        buf.writeByte(PacketProtocol.TYPE_AUTH.toInt())
        buf.writeByte(PacketProtocol.VERSION.toInt())
        buf.writeInt(worldId)
        buf.writeLong(timestamp)
        buf.writeBytes(nonce)
        writeUtf8WithU16Len(buf, signature)
        ctx.writeAndFlush(buf)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (authed) {
            // Pass through frames after auth is complete.
            ctx.fireChannelRead(msg.retain())
            return
        }

        val type = msg.readByte()
        val version = msg.readByte()
        if (version != PacketProtocol.VERSION) {
            authResult.completeExceptionally(IllegalStateException("protocol version mismatch"))
            ctx.close()
            return
        }

        when (type) {
            PacketProtocol.TYPE_AUTH_OK -> {
                authed = true
                authResult.complete(AuthOutcome.OK)
                // Remove ourselves so we don't sit in the hot path.
                ctx.pipeline().remove(this)
            }

            PacketProtocol.TYPE_AUTH_FAIL -> {
                authResult.complete(AuthOutcome.FAILED_AUTH)
                ctx.close()
            }

            else -> {
                // ignore until auth completes
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        authResult.completeExceptionally(cause)
        ctx.close()
    }
}

private enum class AuthOutcome { OK, FAILED_AUTH }

private class EnqueueHandler(
    private val client: CentralConnection
) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is ByteBuf) {
            return
        }

        val type = msg.readByte()
        val version = msg.readByte()
        if (version != PacketProtocol.VERSION) {
            ctx.close()
            return
        }

        if (type != PacketProtocol.TYPE_PUSH) {
            return
        }

        val seq = msg.readLong()
        val opcode = msg.readInt()
        val len = msg.readInt()
        if (len < 0 || len > PacketProtocol.MAX_FRAME_BYTES) {
            ctx.close()
            return
        }
        val payload = ByteArray(len)
        msg.readBytes(payload)
        client.offerIncoming(IncomingPacket(opcode = opcode, payload = payload, seq = seq))
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }
}

private fun writeUtf8WithU16Len(buf: ByteBuf, s: String) {
    val bytes = s.toByteArray(StandardCharsets.UTF_8)
    require(bytes.size <= 0xFFFF) { "string too long" }
    buf.writeShort(bytes.size)
    buf.writeBytes(bytes)
}

