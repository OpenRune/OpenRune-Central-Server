package dev.or2.central.embed

import dev.or2.central.util.config.CentralRuntimeConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory

internal object EmbeddedCentralDevPostgres {
    private val log = LoggerFactory.getLogger(EmbeddedCentralDevPostgres::class.java)

    @Volatile
    private var embedded: EmbeddedPostgres? = null

    fun resolveRuntimeForEmbeddedServer(runtime: CentralRuntimeConfig): Pair<CentralRuntimeConfig, Boolean> {
        if (runtime.jdbcUrl.isNotBlank()) {
            return runtime to false
        }
        synchronized(this) {
            if (embedded == null) {
                val dataDir = embeddedPgdataPath()
                Files.createDirectories(dataDir)
                embedded =
                    EmbeddedPostgres.builder()
                        .setDataDirectory(dataDir.toFile())
                        .setCleanDataDirectory(false)
                        .start()
                log.info("Embedded PostgreSQL started (Central dev server, PGDATA: {})", dataDir)
            }
            val inst = embedded!!
            val url = inst.getJdbcUrl("postgres", "postgres")
            val effective =
                runtime.copy(
                    jdbcUrl = url,
                    dbUser = "postgres",
                    dbPassword = "",
                )
            return effective to true
        }
    }

    fun stop() {
        synchronized(this) {
            embedded?.close()
            embedded = null
        }
    }

    private fun embeddedPgdataPath(): Path {
        val fromEnv = System.getenv("OPENRUNE_EMBEDDED_PGDATA")?.trim()?.takeIf { it.isNotEmpty() }
        val pathStr = fromEnv ?: ".data/pgdata"
        return Path.of(pathStr).toAbsolutePath().normalize()
    }
}
