package dev.openrune.central

import dev.openrune.central.config.ConfigLoader
import dev.openrune.central.logging.RuntimeLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Programmatic entrypoint for starting the central server with an explicit config path.
 *
 * Example:
 * `CentralServer.start("D:/OpenRune/OpenRune-Central-Server/config.yml")`
 */
object CentralServer {
    private val logger = LoggerFactory.getLogger(CentralServer::class.java)

    fun start(
        configPath: Path,
        host: String = "0.0.0.0",
        port: Int = 8080,
        generateJavaLocal: Boolean = true,
        /**
         * If true, block only until Ktor reports the application has started, then return.
         * (Unlike `engine.start(wait=true)`, this does NOT block until shutdown.)
         */
        awaitStarted: Boolean = true,
        awaitStartedTimeoutMs: Long = 30_000L,
        rev: Int = -1
    ): NettyApplicationEngine {

        val loaded = ConfigLoader.loadOrThrow(configPath)
        val appConfig =
            if (rev != -1 && rev != loaded.rev) {
                logger.info(
                    "Overriding config revision: configPath='{}' rev(config)={} -> rev(override)={}",
                    configPath.toAbsolutePath().normalize().toString(),
                    loaded.rev,
                    rev
                )
                loaded.copy(rev = rev)
            } else {
                loaded
            }

        RuntimeLogging.configureForMode()

        val startedLatch = if (awaitStarted) CountDownLatch(1) else null

        val engine =
            embeddedServer(Netty, host = host, port = port) {
                module(appConfig, generateJavaLocal = generateJavaLocal)
            }.also { e ->
                if (startedLatch != null) {
                    e.environment.monitor.subscribe(ApplicationStarted) {
                        startedLatch.countDown()
                    }
                }
            }

        // Never block until shutdown; we control blocking via awaitStarted.
        engine.start(wait = false)

        if (startedLatch != null) {
            val ok = startedLatch.await(awaitStartedTimeoutMs, TimeUnit.MILLISECONDS)
            if (!ok) {
                logger.warn(
                    "Central server start timed out after {}ms (host={}, port={}, configPath='{}')",
                    awaitStartedTimeoutMs,
                    host,
                    port,
                    configPath.toAbsolutePath().normalize().toString()
                )
            }
        }
        return engine
    }

    /**
     * Convenience helper for the "block the current thread" behavior.
     */
    fun startBlocking(
        configPath: Path,
        host: String = "0.0.0.0",
        port: Int = 8080,
        generateJavaLocal: Boolean = true,
        rev: Int = -1
    ): NettyApplicationEngine =
        embeddedServer(Netty, host = host, port = port) {
            val loaded = ConfigLoader.loadOrThrow(configPath)
            val appConfig = if (rev != -1 && rev != loaded.rev) loaded.copy(rev = rev) else loaded
            module(appConfig, generateJavaLocal = generateJavaLocal)
        }.also { it.start(wait = true) }
}

