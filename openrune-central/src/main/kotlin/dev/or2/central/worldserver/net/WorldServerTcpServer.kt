package dev.or2.central.worldserver.net

import dev.or2.central.worldserver.net.push.WorldServerPushChannelRegistry
import dev.or2.central.worldserver.session.WorldServerSessionService
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
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
    private val soBacklog: Int = 512,
    private val readTimeoutSeconds: Int = 120,
    private val maxConnectionsPerIp: Int = 32,
    private val maxConnectionsTotal: Int = 4096,
    private val maxFramesPerSecond: Double = 80.0,
    private val maxFrameBurst: Double = 120.0,
) {
    private val log = LoggerFactory.getLogger(WorldServerTcpServer::class.java)
    private val bossGroup = NioEventLoopGroup(1, threadFactory("openrune-worldserver-boss", true))
    private val workerGroup =
        NioEventLoopGroup(0, threadFactory("openrune-worldserver-io", true))
    private var serverChannel: Channel? = null
    private val connectionLimits =
        WorldServerConnectionLimits(
            maxPerIp = maxConnectionsPerIp.coerceAtLeast(0),
            maxTotal = maxConnectionsTotal.coerceAtLeast(0),
        )

    fun start() {
        val backlog = soBacklog.coerceIn(64, 16_384)
        val rt = readTimeoutSeconds.coerceIn(5, 3600)
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
                        connectionLimits = connectionLimits,
                        readTimeoutSeconds = rt,
                        maxFramesPerSecond = maxFramesPerSecond,
                        maxFrameBurst = maxFrameBurst,
                    ),
                )
        val future = bootstrap.bind(port).sync()
        serverChannel = future.channel()
        log.info(
            "World server listening on {} (SO_BACKLOG={}, readTimeout={}s, maxConn/ip={}, maxConn/total={})",
            port,
            backlog,
            rt,
            maxConnectionsPerIp,
            maxConnectionsTotal,
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
        private fun threadFactory(
            baseName: String,
            daemon: Boolean,
        ): ThreadFactory {
            val seq = AtomicInteger()
            return ThreadFactory { runnable ->
                Thread(runnable, "${baseName}-${seq.incrementAndGet()}").apply {
                    isDaemon = daemon
                }
            }
        }
    }
}
