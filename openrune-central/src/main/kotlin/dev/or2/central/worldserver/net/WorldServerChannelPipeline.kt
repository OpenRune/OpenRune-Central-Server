package dev.or2.central.worldserver.net

import dev.or2.central.worldserver.net.codec.WorldServerInboundFrameSpecs
import dev.or2.central.worldserver.net.push.WorldServerPushChannelRegistry
import dev.or2.central.worldserver.session.WorldServerSessionService
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class WorldServerChannelPipeline(
    private val sessionService: WorldServerSessionService,
    private val executor: ExecutorService,
    private val pushChannelRegistry: WorldServerPushChannelRegistry,
    private val connectionLimits: WorldServerConnectionLimits,
    private val readTimeoutSeconds: Int,
    private val maxFramesPerSecond: Double,
    private val maxFrameBurst: Double,
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val p = ch.pipeline()
        p.addLast("connGate", WorldServerConnectionGateHandler(connectionLimits))
        p.addLast("readTimeout", ReadTimeoutHandler(readTimeoutSeconds.toLong().coerceAtLeast(5), TimeUnit.SECONDS))
        p.addLast(
            "frameDecoder",
            LengthFieldBasedFrameDecoder(
                WorldServerInboundFrameSpecs.MAX_INBOUND_FRAMED_BODY,
                0,
                4,
                0,
                4,
            ),
        )
        p.addLast("frameEncoder", LengthFieldPrepender(4))
        p.addLast(
            "handler",
            WorldServerNettyInboundHandler(
                sessionService,
                executor,
                pushChannelRegistry,
                maxFramesPerSecond,
                maxFrameBurst,
            ),
        )
    }
}
