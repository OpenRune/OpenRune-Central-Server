package dev.or2.central.embed

import dev.or2.central.CentralApplication
import dev.or2.central.config.CentralConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine

class CentralEmbeddedServer(
    private val config: CentralConfig,
) {
    @Volatile
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private var stopEmbeddedPostgresOnStop = false
    private lateinit var effectiveConfig: CentralConfig

    fun start() {
        check(server == null) { "Server already started" }
        val (resolved, startedEmbedded) = EmbeddedPostgres.resolveConfig(config)
        effectiveConfig = resolved
        stopEmbeddedPostgresOnStop = startedEmbedded
        server =
            embeddedServer(Netty, port = effectiveConfig.http.port, host = "0.0.0.0") {
                CentralApplication.configure(this, effectiveConfig)
            }.also { it.start(wait = false) }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
        server = null
        if (stopEmbeddedPostgresOnStop) {
            EmbeddedPostgres.stop()
            stopEmbeddedPostgresOnStop = false
        }
    }
}
