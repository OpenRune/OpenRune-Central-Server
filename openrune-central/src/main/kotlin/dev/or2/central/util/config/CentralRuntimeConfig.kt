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
)

fun loadCentralRuntimeConfig(): CentralRuntimeConfig {
    val props = loadCentralConfigPropertiesInternal()
    val jdbcUrl =
        resolveString("OPENRUNE_JDBC_URL", "openrune.jdbc.url", props, "jdbc:postgresql://127.0.0.1:5432/openrune_central")
            .trim()
    val dbUser =
        resolveString("OPENRUNE_DB_USER", "openrune.db.user", props, "openrune").trim()
    val dbPassword = resolveString("OPENRUNE_DB_PASSWORD", "openrune.db.password", props, "openrune")
    val dbMaximumPoolSize =
        resolveInt("OPENRUNE_DB_POOL_SIZE", "openrune.db.poolSize", props, 10).coerceIn(1, 100)
    val sessionsTtlMillis =
        resolveLong("OPENRUNE_SESSION_TTL_MS", "openrune.sessionsTtlMs", props, 300_000L).coerceAtLeast(1L)
    val worldsLinkPortRaw =
        resolveOptionalString("OPENRUNE_WORLD_LINK_PORT", "openrune.worldsLinkPort", props)
    val worldsLinkSoBacklog =
        resolveInt("OPENRUNE_WORLD_LINK_SO_BACKLOG", "openrune.worldsLinkSoBacklog", props, 512)
            .coerceIn(64, 16_384)
    val defaultHandlerThreads =
        maxOf(4, minOf(32, Runtime.getRuntime().availableProcessors() * 2))
    val worldsLinkReadTimeoutSeconds =
        resolveInt("OPENRUNE_WORLD_LINK_READ_TIMEOUT_SEC", "openrune.worldsLinkReadTimeoutSeconds", props, 120)
            .coerceIn(5, 3600)
    val worldsLinkMaxConnectionsPerIp =
        resolveInt("OPENRUNE_WORLD_LINK_MAX_CONN_PER_IP", "openrune.worldsLinkMaxConnectionsPerIp", props, 32)
            .coerceIn(0, 4096)
    val worldsLinkMaxConnectionsTotal =
        resolveInt("OPENRUNE_WORLD_LINK_MAX_CONN_TOTAL", "openrune.worldsLinkMaxConnectionsTotal", props, 4096)
            .coerceIn(0, 262_144)
    val worldsLinkHandlerThreads =
        resolveInt("OPENRUNE_WORLD_LINK_HANDLER_THREADS", "openrune.worldsLinkHandlerThreads", props, defaultHandlerThreads)
            .coerceIn(1, 256)
    val worldsLinkHandlerQueueSize =
        resolveInt("OPENRUNE_WORLD_LINK_HANDLER_QUEUE", "openrune.worldsLinkHandlerQueueSize", props, 2048)
            .coerceIn(32, 65_536)
    val worldsLinkMaxFramesPerSecond =
        resolveInt("OPENRUNE_WORLD_LINK_MAX_FRAMES_PER_SEC", "openrune.worldsLinkMaxFramesPerSecond", props, 80)
            .coerceIn(0, 10_000)
    val worldsLinkMaxFrameBurst =
        resolveInt("OPENRUNE_WORLD_LINK_MAX_FRAME_BURST", "openrune.worldsLinkMaxFrameBurst", props, 120)
            .coerceIn(1, 100_000)
    return CentralRuntimeConfig(
        jdbcUrl = jdbcUrl,
        dbUser = dbUser,
        dbPassword = dbPassword,
        dbMaximumPoolSize = dbMaximumPoolSize,
        sessionsTtlMillis = sessionsTtlMillis,
        worldsLinkPort = resolveWorldLinkPort(worldsLinkPortRaw),
        worldsLinkSoBacklog = worldsLinkSoBacklog,
        worldsLinkReadTimeoutSeconds = worldsLinkReadTimeoutSeconds,
        worldsLinkMaxConnectionsPerIp = worldsLinkMaxConnectionsPerIp,
        worldsLinkMaxConnectionsTotal = worldsLinkMaxConnectionsTotal,
        worldsLinkHandlerThreads = worldsLinkHandlerThreads,
        worldsLinkHandlerQueueSize = worldsLinkHandlerQueueSize,
        worldsLinkMaxFramesPerSecond = worldsLinkMaxFramesPerSecond,
        worldsLinkMaxFrameBurst = worldsLinkMaxFrameBurst,
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
): CentralRuntimeConfig {
    val pool = dbMaximumPoolSize.coerceIn(1, 100)
    val ttl = sessionsTtlMillis.coerceAtLeast(1L)
    val backlog = worldLinkSoBacklog.coerceIn(64, 16_384)
    val linkPort = worldLinkPort.takeUnless { it <= 0 }
    return CentralRuntimeConfig(
        jdbcUrl = jdbcUrl.trim(),
        dbUser = dbUser.trim(),
        dbPassword = dbPassword,
        dbMaximumPoolSize = pool,
        sessionsTtlMillis = ttl,
        worldsLinkPort = linkPort,
        worldsLinkSoBacklog = backlog,
        worldsLinkReadTimeoutSeconds = worldsLinkReadTimeoutSeconds.coerceIn(5, 3600),
        worldsLinkMaxConnectionsPerIp = worldsLinkMaxConnectionsPerIp.coerceIn(0, 4096),
        worldsLinkMaxConnectionsTotal = worldsLinkMaxConnectionsTotal.coerceIn(0, 262_144),
        worldsLinkHandlerThreads = worldsLinkHandlerThreads.coerceIn(1, 256),
        worldsLinkHandlerQueueSize = worldsLinkHandlerQueueSize.coerceIn(32, 65_536),
        worldsLinkMaxFramesPerSecond = worldsLinkMaxFramesPerSecond.coerceIn(0, 10_000),
        worldsLinkMaxFrameBurst = worldsLinkMaxFrameBurst.coerceIn(1, 100_000),
    )
}

fun applyKtorHttpPortFromCentralConfigBeforeEngineStart() {
    val props = loadCentralConfigPropertiesInternal()
    val fromFile = resolvePropertyValue("openrune.http.port", props)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    val fromEnv = System.getenv("OPENRUNE_HTTP_PORT")?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    val port = fromEnv ?: fromFile
    if (port != null && port in 1..65535) {
        System.setProperty("ktor.deployment.port", port.toString())
        centralConfigLog.info("Ktor HTTP port set to {}", port)
    }
}

private val centralConfigLog = LoggerFactory.getLogger("dev.or2.central.config")

private fun loadCentralConfigPropertiesInternal(): Properties {
    val p = Properties()
    val userDir = System.getProperty("user.dir") ?: "."

    val explicit = System.getenv("OPENRUNE_CONFIG")?.trim()?.takeIf { it.isNotEmpty() }
    if (explicit != null) {
        val path = Paths.get(explicit)
        if (Files.isRegularFile(path)) {
            loadPropsFromPath(p, path)
            centralConfigLog.info("Loaded central config from {}", path.toAbsolutePath().normalize())
            return p
        }
        centralConfigLog.warn(
            "OPENRUNE_CONFIG is set to \"{}\" but that path is not a readable file; trying other locations.",
            explicit,
        )
    }

    val candidates = ArrayList<Path>()
    candidates.add(Paths.get(userDir, "central-config.properties"))
    candidates.add(Paths.get(userDir, "config", "central-config.properties"))
    jarSiblingDirectory()?.let { dir -> candidates.add(dir.resolve("central-config.properties")) }

    for (path in candidates) {
        if (Files.isRegularFile(path)) {
            loadPropsFromPath(p, path)
            centralConfigLog.info("Loaded central config from {}", path.toAbsolutePath().normalize())
            return p
        }
    }

    val cl = Thread.currentThread().contextClassLoader
    cl.getResourceAsStream("central-config.properties")?.use { ins ->
        InputStreamReader(ins, StandardCharsets.UTF_8).use { reader -> p.load(reader) }
        centralConfigLog.info("Loaded central config from classpath resource central-config.properties")
        return p
    }

    centralConfigLog.warn(
        "No central-config.properties found (user.dir={}). Tried: {}. Using defaults for JDBC and other settings.",
        Paths.get(userDir).toAbsolutePath().normalize(),
        candidates.joinToString { it.toAbsolutePath().normalize().toString() },
    )
    return p
}

private fun loadPropsFromPath(
    p: Properties,
    path: Path,
) {
    Files.newInputStream(path).use { ins ->
        InputStreamReader(ins, StandardCharsets.UTF_8).use { reader -> p.load(reader) }
    }
}

private fun jarSiblingDirectory(): Path? {
    val url = CentralRuntimeConfig::class.java.protectionDomain?.codeSource?.location ?: return null
    return try {
        val p = Paths.get(url.toURI())
        if (Files.isRegularFile(p) && p.toString().lowercase().endsWith(".jar")) {
            p.parent
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun resolveString(
    envName: String,
    propKey: String,
    props: Properties,
    default: String,
): String {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    resolvePropertyValue(propKey, props)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return default
}

private fun resolveOptionalString(
    envName: String,
    propKey: String,
    props: Properties,
): String? {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    resolvePropertyValue(propKey, props)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return null
}

private fun resolveInt(
    envName: String,
    propKey: String,
    props: Properties,
    default: Int,
): Int {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { return it }
    resolvePropertyValue(propKey, props)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { return it }
    return default
}

private fun resolveLong(
    envName: String,
    propKey: String,
    props: Properties,
    default: Long,
): Long {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()?.let { return it }
    resolvePropertyValue(propKey, props)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()?.let { return it }
    return default
}

private val envPlaceholderRegex = Regex("""\{env\.([A-Za-z0-9_.-]+)}""")

private fun resolvePropertyValue(propKey: String, props: Properties): String? {
    val raw = props.getProperty(propKey) ?: return null
    return envPlaceholderRegex.replace(raw) { m ->
        val key = m.groupValues[1]
        resolveEnvPlaceholder(key) ?: ""
    }
}

private fun resolveEnvPlaceholder(key: String): String? {
    System.getenv(key)?.let { return it }
    System.getenv(key.uppercase())?.let { return it }
    val normalized = key.replace('.', '_').replace('-', '_')
    System.getenv(normalized)?.let { return it }
    System.getenv(normalized.uppercase())?.let { return it }
    return null
}

private fun resolveWorldLinkPort(raw: String?): Int? {
    val r = raw?.trim().orEmpty()
    if (r.equals("false", ignoreCase = true) || r == "0") {
        return null
    }
    if (r.isBlank()) {
        return 9091
    }
    return r.toIntOrNull() ?: 9091
}
