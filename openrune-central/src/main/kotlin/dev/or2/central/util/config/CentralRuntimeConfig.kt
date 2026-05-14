package dev.or2.central.util.config

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import org.slf4j.LoggerFactory

data class CentralRuntimeConfig(
    val jdbcUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbMaximumPoolSize: Int,
    val sessionsTtlMillis: Long,
    val worldsLinkPort: Int?,
    val worldsLinkSoBacklog: Int,
    val worldsLinkReadTimeoutSeconds: Int,
    val worldsLinkMaxConnectionsPerIp: Int,
    val worldsLinkMaxConnectionsTotal: Int,
    val worldsLinkHandlerThreads: Int,
    val worldsLinkHandlerQueueSize: Int,
    val worldsLinkMaxFramesPerSecond: Int,
    val worldsLinkMaxFrameBurst: Int,
    val onlineSampleIntervalSeconds: Int,
    /**
     * Remote newline-separated bad-word list (e.g. gist raw URL). Fetched on schedule and merged
     * with classpath `profanity/bad_words_local.txt`.
     */
    val badWordsRemoteUrl: String,
    val badWordsRefreshMinutes: Int,
    val badWordsHttpTimeoutSeconds: Int,
)

private val log = LoggerFactory.getLogger("dev.or2.central.config")

fun loadCentralRuntimeConfig(): CentralRuntimeConfig {
    val props = loadCentralConfigPropertiesInternal()

    val jdbcUrl = resolveString(
        "OPENRUNE_JDBC_URL",
        "openrune.jdbc.url",
        props,
        "jdbc:postgresql://127.0.0.1:5432/openrune_central"
    ).trim()

    val dbUser = resolveString("OPENRUNE_DB_USER", "openrune.db.user", props, "openrune").trim()
    val dbPassword = resolveString("OPENRUNE_DB_PASSWORD", "openrune.db.password", props, "openrune")

    val dbPoolSize = resolveInt("OPENRUNE_DB_POOL_SIZE", "openrune.db.poolSize", props, 10)
        .coerceIn(1, 100)

    val sessionsTtl = resolveLong("OPENRUNE_SESSION_TTL_MS", "openrune.sessionsTtlMs", props, 300_000L)
        .coerceAtLeast(1L)

    val handlerThreadsDefault =
        maxOf(4, minOf(32, Runtime.getRuntime().availableProcessors() * 2))

    return CentralRuntimeConfig(
        jdbcUrl = jdbcUrl,
        dbUser = dbUser,
        dbPassword = dbPassword,
        dbMaximumPoolSize = dbPoolSize,
        sessionsTtlMillis = sessionsTtl,

        worldsLinkPort = resolveWorldLinkPort(
            resolveOptionalString("OPENRUNE_WORLD_LINK_PORT", "openrune.worldsLinkPort", props)
        ),

        worldsLinkSoBacklog = resolveInt(
            "OPENRUNE_WORLD_LINK_SO_BACKLOG",
            "openrune.worldsLinkSoBacklog",
            props,
            512
        ).coerceIn(64, 16_384),

        worldsLinkReadTimeoutSeconds = resolveInt(
            "OPENRUNE_WORLD_LINK_READ_TIMEOUT_SEC",
            "openrune.worldsLinkReadTimeoutSeconds",
            props,
            120
        ).coerceIn(5, 3600),

        worldsLinkMaxConnectionsPerIp = resolveInt(
            "OPENRUNE_WORLD_LINK_MAX_CONN_PER_IP",
            "openrune.worldsLinkMaxConnectionsPerIp",
            props,
            32
        ).coerceIn(0, 4096),

        worldsLinkMaxConnectionsTotal = resolveInt(
            "OPENRUNE_WORLD_LINK_MAX_CONN_TOTAL",
            "openrune.worldsLinkMaxConnectionsTotal",
            props,
            4096
        ).coerceIn(0, 262_144),

        worldsLinkHandlerThreads = resolveInt(
            "OPENRUNE_WORLD_LINK_HANDLER_THREADS",
            "openrune.worldsLinkHandlerThreads",
            props,
            handlerThreadsDefault
        ).coerceIn(1, 256),

        worldsLinkHandlerQueueSize = resolveInt(
            "OPENRUNE_WORLD_LINK_HANDLER_QUEUE",
            "openrune.worldsLinkHandlerQueueSize",
            props,
            2048
        ).coerceIn(32, 65_536),

        worldsLinkMaxFramesPerSecond = resolveInt(
            "OPENRUNE_WORLD_LINK_MAX_FRAMES_PER_SEC",
            "openrune.worldsLinkMaxFramesPerSecond",
            props,
            80
        ).coerceIn(0, 10_000),

        worldsLinkMaxFrameBurst = resolveInt(
            "OPENRUNE_WORLD_LINK_MAX_FRAME_BURST",
            "openrune.worldsLinkMaxFrameBurst",
            props,
            120
        ).coerceIn(1, 100_000),

        onlineSampleIntervalSeconds = resolveInt(
            "OPENRUNE_ONLINE_SAMPLE_INTERVAL_SEC",
            "openrune.onlineSampleIntervalSeconds",
            props,
            3600
        ).coerceIn(30, 86_400),

        badWordsRemoteUrl =
            resolveString(
                "OPENRUNE_BAD_WORDS_URL",
                "openrune.badWordsRemoteUrl",
                props,
                "https://gist.githubusercontent.com/briankung/e085841a7a13fa4945a0cf482950436a/raw/326b4078db98541204e3d192d7cf84f63cd4c87a/bad_words.txt",
            ).trim(),

        badWordsRefreshMinutes =
            resolveInt(
                "OPENRUNE_BAD_WORDS_REFRESH_MINUTES",
                "openrune.badWordsRefreshMinutes",
                props,
                360,
            ).coerceIn(5, 10_080),

        badWordsHttpTimeoutSeconds =
            resolveInt(
                "OPENRUNE_BAD_WORDS_HTTP_TIMEOUT_SEC",
                "openrune.badWordsHttpTimeoutSeconds",
                props,
                20,
            ).coerceIn(3, 120),
    )
}

fun centralRuntimeConfigFromJdbc(
    jdbcUrl: String,
    dbUser: String,
    dbPassword: String,
    dbMaximumPoolSize: Int,
    worldLinkPort: Int,
    worldLinkSoBacklog: Int,
    sessionsTtlMillis: Long = 300_000L,
    worldsLinkReadTimeoutSeconds: Int = 120,
    worldsLinkMaxConnectionsPerIp: Int = 32,
    worldsLinkMaxConnectionsTotal: Int = 4096,
    worldsLinkHandlerThreads: Int =
        maxOf(4, minOf(32, Runtime.getRuntime().availableProcessors() * 2)),
    worldsLinkHandlerQueueSize: Int = 2048,
    worldsLinkMaxFramesPerSecond: Int = 80,
    worldsLinkMaxFrameBurst: Int = 120,
    onlineSampleIntervalSeconds: Int = 3600,
    badWordsRemoteUrl: String =
        "https://gist.githubusercontent.com/briankung/e085841a7a13fa4945a0cf482950436a/raw/326b4078db98541204e3d192d7cf84f63cd4c87a/bad_words.txt",
    badWordsRefreshMinutes: Int = 360,
    badWordsHttpTimeoutSeconds: Int = 20,
): CentralRuntimeConfig {

    return CentralRuntimeConfig(
        jdbcUrl = jdbcUrl.trim(),
        dbUser = dbUser.trim(),
        dbPassword = dbPassword,
        dbMaximumPoolSize = dbMaximumPoolSize.coerceIn(1, 100),
        sessionsTtlMillis = sessionsTtlMillis.coerceAtLeast(1L),

        worldsLinkPort = worldLinkPort.takeUnless { it <= 0 },
        worldsLinkSoBacklog = worldLinkSoBacklog.coerceIn(64, 16_384),

        worldsLinkReadTimeoutSeconds = worldsLinkReadTimeoutSeconds.coerceIn(5, 3600),
        worldsLinkMaxConnectionsPerIp = worldsLinkMaxConnectionsPerIp.coerceIn(0, 4096),
        worldsLinkMaxConnectionsTotal = worldsLinkMaxConnectionsTotal.coerceIn(0, 262_144),
        worldsLinkHandlerThreads = worldsLinkHandlerThreads.coerceIn(1, 256),
        worldsLinkHandlerQueueSize = worldsLinkHandlerQueueSize.coerceIn(32, 65_536),
        worldsLinkMaxFramesPerSecond = worldsLinkMaxFramesPerSecond.coerceIn(0, 10_000),
        worldsLinkMaxFrameBurst = worldsLinkMaxFrameBurst.coerceIn(1, 100_000),

        onlineSampleIntervalSeconds = onlineSampleIntervalSeconds.coerceIn(30, 86_400),

        badWordsRemoteUrl = badWordsRemoteUrl.trim(),
        badWordsRefreshMinutes = badWordsRefreshMinutes.coerceIn(5, 10_080),
        badWordsHttpTimeoutSeconds = badWordsHttpTimeoutSeconds.coerceIn(3, 120),
    )
}

fun applyKtorHttpPortFromCentralConfigBeforeEngineStart() {
    val props = loadCentralConfigPropertiesInternal()

    val port = resolveInt("OPENRUNE_HTTP_PORT", "openrune.http.port", props, -1)
        .takeIf { it in 1..65535 }

    if (port != null) {
        System.setProperty("ktor.deployment.port", port.toString())
        log.info("Ktor HTTP port set to {}", port)
    }
}

private fun env(name: String): String? =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

private fun resolveString(env: String, prop: String, props: Properties, default: String): String =
    env(env)
        ?: resolvePropertyValue(prop, props)?.trim()?.takeIf { it.isNotEmpty() }
        ?: default

private fun resolveOptionalString(env: String, prop: String, props: Properties): String? =
    env(env)
        ?: resolvePropertyValue(prop, props)?.trim()?.takeIf { it.isNotEmpty() }

private fun resolveInt(env: String, prop: String, props: Properties, default: Int): Int =
    env(env)?.toIntOrNull()
        ?: resolvePropertyValue(prop, props)?.toIntOrNull()
        ?: default

private fun resolveLong(env: String, prop: String, props: Properties, default: Long): Long =
    env(env)?.toLongOrNull()
        ?: resolvePropertyValue(prop, props)?.toLongOrNull()
        ?: default

private val envPlaceholderRegex = Regex("""\{env\.([A-Za-z0-9_.-]+)}""")

private fun resolvePropertyValue(propKey: String, props: Properties): String? {
    val raw = props.getProperty(propKey) ?: return null
    return envPlaceholderRegex.replace(raw) { match ->
        resolveEnv(match.groupValues[1]) ?: ""
    }
}

private fun resolveEnv(key: String): String? =
    listOf(
        key,
        key.uppercase(),
        key.replace('.', '_').replace('-', '_'),
        key.replace('.', '_').replace('-', '_').uppercase()
    ).firstNotNullOfOrNull(System::getenv)

private fun loadCentralConfigPropertiesInternal(): Properties {
    val props = Properties()
    val userDir = System.getProperty("user.dir") ?: "."

    val explicit = System.getenv("OPENRUNE_CONFIG")?.trim()?.takeIf { it.isNotEmpty() }

    if (explicit != null) {
        val path = Paths.get(explicit)
        if (Files.isRegularFile(path)) {
            loadPropsFromPath(props, path)
            log.info("Loaded central config from {}", path.toAbsolutePath().normalize())
            return props
        }
        log.warn("OPENRUNE_CONFIG invalid: {}", explicit)
    }

    val cwd = Paths.get(userDir, "central-config.properties")
    if (Files.isRegularFile(cwd)) {
        loadPropsFromPath(props, cwd)
        log.info("Loaded central config from {}", cwd.toAbsolutePath().normalize())
        return props
    }

    jarSiblingDirectory()?.resolve("central-config.properties")?.let {
        if (Files.isRegularFile(it)) {
            loadPropsFromPath(props, it)
            log.info("Loaded central config from {}", it.toAbsolutePath().normalize())
            return props
        }
    }

    log.warn("No central-config.properties found, using defaults")
    return props
}

private fun loadPropsFromPath(props: Properties, path: Path) {
    Files.newInputStream(path).use { ins ->
        InputStreamReader(ins, StandardCharsets.UTF_8).use { reader ->
            props.load(reader)
        }
    }
}

private fun jarSiblingDirectory(): Path? {
    val url = CentralRuntimeConfig::class.java.protectionDomain?.codeSource?.location ?: return null
    return runCatching {
        val p = Paths.get(url.toURI())
        if (Files.isRegularFile(p) && p.toString().endsWith(".jar")) p.parent else null
    }.getOrNull()
}

private fun resolveWorldLinkPort(raw: String?): Int? {
    val r = raw?.trim().orEmpty()

    return when {
        r.equals("false", true) || r == "0" -> null
        r.isBlank() -> 9091
        else -> r.toIntOrNull() ?: 9091
    }
}