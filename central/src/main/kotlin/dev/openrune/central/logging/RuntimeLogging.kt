package dev.openrune.central.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

/**
 * Runtime log tuning to keep prod output minimal.
 *
 * Enable by setting:
 * - env: OPENRUNE_ENV=prod
 * - or JVM: -Dopenrune.env=prod
 */
object RuntimeLogging {
    private const val ENV_VAR = "OPENRUNE_ENV"
    private const val SYS_PROP = "openrune.env"

    fun isProd(): Boolean {
        val sys = System.getProperty(SYS_PROP)?.trim()?.lowercase().orEmpty()
        val env = System.getenv(ENV_VAR)?.trim()?.lowercase().orEmpty()
        val v = if (sys.isNotEmpty()) sys else env
        return v == "prod" || v == "production"
    }

    /**
     * In prod, set root and noisy Ktor loggers to ERROR.
     * Safe to call multiple times.
     */
    fun configureForMode() {
        //if (!isProd()) return

        val ctx = LoggerFactory.getILoggerFactory()
        val root = (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as? Logger) ?: return

        // Root to ERROR (kills INFO spam, keeps errors).
        root.level = Level.ERROR

        // Extra safety: Ktor/netty are noisy at INFO during startup.
        setLevel("io.ktor", Level.ERROR)
        setLevel("io.netty", Level.ERROR)
    }

    private fun setLevel(name: String, level: Level) {
        val logger = LoggerFactory.getLogger(name) as? Logger ?: return
        logger.level = level
    }
}

