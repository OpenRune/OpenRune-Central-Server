package dev.or2.central.embed

import dev.or2.central.util.config.CentralRuntimeConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

internal object EmbeddedCentralDevPostgres {

    private val log = LoggerFactory.getLogger(EmbeddedCentralDevPostgres::class.java)

    @Volatile
    private var embedded: EmbeddedPostgres? = null

    fun resolveRuntimeForEmbeddedServer(
        runtime: CentralRuntimeConfig,
    ): Pair<CentralRuntimeConfig, Boolean> {

        if (runtime.jdbcUrl.isNotBlank()) {
            return runtime to false
        }

        synchronized(this) {
            embedded?.let {
                val url = it.getJdbcUrl("postgres", "postgres")
                return runtime.withEmbedded(url) to true
            }

            val dataDir = embeddedPgdataPath().also {
                Files.createDirectories(it)
            }

            val instance = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir.toFile())
                .setCleanDataDirectory(false)
                .start()

            embedded = instance

            log.info("Embedded PostgreSQL started (PGDATA={})", dataDir)

            val url = instance.getJdbcUrl("postgres", "postgres")
            return runtime.withEmbedded(url) to true
        }
    }

    fun stop() {
        synchronized(this) {
            embedded?.close()
            embedded = null
        }
    }

    private fun CentralRuntimeConfig.withEmbedded(url: String) =
        copy(
            jdbcUrl = url,
            dbUser = "postgres",
            dbPassword = "",
        )

    private fun embeddedPgdataPath(): Path {
        val env = System.getenv("OPENRUNE_EMBEDDED_PGDATA")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return Path.of(env ?: ".data/pgdata")
            .toAbsolutePath()
            .normalize()
    }
}