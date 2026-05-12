package dev.or2.central.server.net

import WorldServerConnectionLimits
import dev.or2.central.WorldOperationRepository
import dev.or2.central.util.config.WorldServerTcpConfig
import dev.or2.central.server.net.push.WorldServerPushChannelRegistry
import dev.or2.central.server.session.WorldServerSessionService
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory

class WorldServerTcpServer(
    private val port: Int,
    private val sessionService: WorldServerSessionService,
    private val executor: ExecutorService,
    private val pushChannelRegistry: WorldServerPushChannelRegistry,
    private val worldOperationRepository: WorldOperationRepository,
    private val config: WorldServerTcpConfig,
) {

    private val log = LoggerFactory.getLogger(WorldServerTcpServer::class.java)

    private val bossGroup =
        NioEventLoopGroup(1, threadFactory("openrune-worldserver-boss", true))

    private val workerGroup =
        NioEventLoopGroup(0, threadFactory("openrune-worldserver-io", true))

    private var serverChannel: Channel? = null

    private val connectionLimits =
        WorldServerConnectionLimits(
            maxPerIp = config.maxConnectionsPerIp.coerceAtLeast(0),
            maxTotal = config.maxConnectionsTotal.coerceAtLeast(0),
        )

    fun start() {
        val backlog = config.soBacklog.coerceIn(64, 16_384)
        val readTimeout = config.readTimeoutSeconds.coerceIn(5, 3600)

        val bootstrap =
            ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_BACKLOG, backlog)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(
                    ChannelOption.WRITE_BUFFER_WATER_MARK,
                    WriteBufferWaterMark(32 * 1024, 64 * 1024),
                )
                .childHandler(
                    WorldServerChannelPipeline(
                        sessionService = sessionService,
                        executor = executor,
                        pushChannelRegistry = pushChannelRegistry,
                        worldOperationRepository = worldOperationRepository,
                        connectionLimits = connectionLimits,
                        readTimeoutSeconds = readTimeout,
                        maxFramesPerSecond = config.maxFramesPerSecond,
                        maxFrameBurst = config.maxFrameBurst,
                    ),
                )

        val future = bootstrap.bind(port).sync()
        serverChannel = future.channel()

        log.info(
            "World server listening on {} (backlog={}, timeout={}s, ip={}, total={})",
            port,
            backlog,
            readTimeout,
            config.maxConnectionsPerIp,
            config.maxConnectionsTotal,
        )
    }

    fun stop() {
        try {
            serverChannel?.close()?.sync()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }

    private companion object {
        private fun threadFactory(baseName: String, daemon: Boolean): ThreadFactory {
            val seq = AtomicInteger()
            return ThreadFactory {
                Thread(it, "$baseName-${seq.incrementAndGet()}").apply {
                    isDaemon = daemon
                }
            }
        }
    }
}