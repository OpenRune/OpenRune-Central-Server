package dev.or2.central.server.net

import WorldServerConnectionLimits
import dev.or2.central.WorldOperationRepository
import dev.or2.central.server.net.codec.WorldServerInboundFrameSpecs
import dev.or2.central.server.net.push.WorldServerPushChannelRegistry
import dev.or2.central.server.session.WorldServerSessionService
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
    private val worldOperationRepository: WorldOperationRepository,
    private val connectionLimits: WorldServerConnectionLimits,
    private val readTimeoutSeconds: Int,
    private val maxFramesPerSecond: Double,
    private val maxFrameBurst: Double,
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        require(readTimeoutSeconds >= 5) { "readTimeoutSeconds must be >= 5" }

        val p = ch.pipeline()

        p.addLast("conn-gate", WorldServerConnectionGateHandler(connectionLimits))

        p.addLast(
            "read-timeout",
            ReadTimeoutHandler(readTimeoutSeconds.toLong(), TimeUnit.SECONDS),
        )

        p.addLast(
            "frame-decoder",
            LengthFieldBasedFrameDecoder(
                WorldServerInboundFrameSpecs.MAX_INBOUND_FRAMED_BODY,
                LENGTH_FIELD_OFFSET,
                LENGTH_FIELD_LENGTH,
                LENGTH_ADJUSTMENT,
                INITIAL_BYTES_TO_STRIP,
            ),
        )

        p.addLast("frame-encoder", LengthFieldPrepender(4))

        p.addLast(
            "world-handler",
            WorldServerNettyInboundHandler(
                sessionService,
                executor,
                pushChannelRegistry,
                worldOperationRepository,
                maxFramesPerSecond,
                maxFrameBurst,
            ),
        )
    }

    private companion object {
        private const val LENGTH_FIELD_OFFSET = 0
        private const val LENGTH_FIELD_LENGTH = 4
        private const val LENGTH_ADJUSTMENT = 0
        private const val INITIAL_BYTES_TO_STRIP = 4
    }
}
