package dev.or2.central.embed

import dev.or2.central.util.config.CentralRuntimeConfig
import dev.or2.central.installOpenRuneCentral
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine

class OpenRuneCentralEmbeddedServer(
    private val httpPort: Int,
    private val runtime: CentralRuntimeConfig,
) {
    @Volatile
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private var stopEmbeddedPostgresOnStop: Boolean = false

    fun start() {
        check(server == null) { "OpenRuneCentralEmbeddedServer already started" }
        val (effectiveRuntime, startedEmbedded) =
            EmbeddedCentralDevPostgres.resolveRuntimeForEmbeddedServer(runtime)
        stopEmbeddedPostgresOnStop = startedEmbedded
        val s =
            embeddedServer(
                Netty,
                port = httpPort,
                host = "0.0.0.0",
                module = { installOpenRuneCentral(effectiveRuntime) },
            )
        s.start(wait = false)
        server = s
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
        server = null
        if (stopEmbeddedPostgresOnStop) {
            EmbeddedCentralDevPostgres.stop()
            stopEmbeddedPostgresOnStop = false
        }
    }
}
