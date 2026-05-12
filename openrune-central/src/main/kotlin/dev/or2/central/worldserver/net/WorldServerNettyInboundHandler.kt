package dev.or2.central.worldserver.net

import dev.or2.central.worldserver.net.protocol.WorldServerOpcodes
import dev.or2.central.worldserver.net.push.WorldServerPushChannelRegistry
import dev.or2.central.worldserver.session.WorldServerConnectionState
import dev.or2.central.worldserver.session.WorldServerHandleResult
import dev.or2.central.worldserver.session.WorldServerSessionService
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.TooLongFrameException
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.ReadTimeoutException
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import kotlin.math.min
import org.slf4j.LoggerFactory

class WorldServerNettyInboundHandler(
    private val sessionService: WorldServerSessionService,
    private val executor: ExecutorService,
    private val pushChannelRegistry: WorldServerPushChannelRegistry,
    maxFramesPerSecond: Double,
    maxFrameBurst: Double,
) : SimpleChannelInboundHandler<ByteBuf>() {
    private val state = WorldServerConnectionState()
    private val log = LoggerFactory.getLogger(WorldServerNettyInboundHandler::class.java)
    private var registeredWorldId: Int? = null
    private val frameBucket: FrameTokenBucket? =
        if (maxFramesPerSecond > 0.0) {
            FrameTokenBucket(refillPerSecond = maxFramesPerSecond, maxBurst = maxFrameBurst.coerceAtLeast(1.0))
        } else {
            null
        }

    override fun channelRead0(
        ctx: ChannelHandlerContext,
        msg: ByteBuf,
    ) {
        if (frameBucket != null && !frameBucket.tryConsume()) {
            ctx.close()
            return
        }
        val bytes = ByteArray(msg.readableBytes())
        msg.readBytes(bytes)
        state.remoteHost =
            (ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress
        executor.execute {
            try {
                when (val result = sessionService.handle(state, bytes)) {
                    is WorldServerHandleResult.Reply -> {
                        val b0 = result.payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
                        if (registeredWorldId == null &&
                            b0 == WorldServerOpcodes.OP_PUSH_SUBSCRIBE_ACK &&
                            state.handshakeDone &&
                            state.worldId >= 0 &&
                            state.subscribedForServerPush
                        ) {
                            registeredWorldId = state.worldId
                            pushChannelRegistry.register(state.worldId, ctx.channel())
                            state.subscribedForServerPush = false
                        }
                        val buf = Unpooled.wrappedBuffer(result.payload)
                        val close = result.closeAfterWrite
                        ctx.channel().eventLoop().execute {
                            val future = ctx.writeAndFlush(buf)
                            if (close) {
                                future.addListener { ctx.close() }
                            }
                        }
                    }
                    WorldServerHandleResult.CloseSilent -> {
                        ctx.channel().eventLoop().execute { ctx.close() }
                    }
                }
            } catch (e: Exception) {
                log.error("World server handler error (peer={})", ctx.channel().remoteAddress(), e)
                ctx.channel().eventLoop().execute { ctx.close() }
            }
        }
    }

    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable,
    ) {
        when (cause) {
            is ReadTimeoutException ->
                log.debug("World server read timeout, closing {}", ctx.channel().remoteAddress())
            is TooLongFrameException, is CorruptedFrameException ->
                log.debug("World server framing error from {}: {}", ctx.channel().remoteAddress(), cause.toString())
            else ->
                log.warn("World server channel exception", cause)
        }
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val wid = registeredWorldId
        if (wid != null) {
            pushChannelRegistry.unregister(wid, ctx.channel())
        }
        registeredWorldId = null
        super.channelInactive(ctx)
    }

    private class FrameTokenBucket(
        private val refillPerSecond: Double,
        private val maxBurst: Double,
    ) {
        private var tokens: Double = maxBurst
        private var lastNanos: Long = System.nanoTime()

        fun tryConsume(): Boolean {
            val now = System.nanoTime()
            val elapsed = (now - lastNanos) / 1_000_000_000.0
            lastNanos = now
            tokens = min(maxBurst, tokens + elapsed * refillPerSecond)
            if (tokens < 1.0) {
                return false
            }
            tokens -= 1.0
            return true
        }
    }
}
