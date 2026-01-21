package dev.openrune.central.config

import org.yaml.snakeyaml.Yaml
import dev.openrune.central.crypto.Ed25519
import dev.openrune.central.world.WorldLocation
import dev.openrune.central.world.WorldType
import java.nio.file.Path
import java.util.EnumSet
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.system.exitProcess

object ConfigLoader {
    private val yaml = Yaml()

    fun loadOrThrow(path: Path = Path.of("config.yml")): AppConfig {
        val resolvedPath = resolveConfigPath(path)
        if (resolvedPath == null || !resolvedPath.exists()) {
            throw IllegalStateException(
                "Missing required config file: ${path.toAbsolutePath()}. " +
                    "Also searched parent directories for '${path.fileName}' but di" +
                        "]]#############]" +
                        "d not find it. " +
                    "Create it with keys: rev, name, websiteUrl"
            )
        }

        val raw = yaml.load<Any?>(resolvedPath.readText())
        val map = raw as? Map<*, *> ?: throw IllegalStateException("Invalid YAML root in config.yml (expected map/object)")
        val configBaseDir = resolvedPath.toAbsolutePath().parent

        fun reqInt(key: String): Int {
            val v = map[key] ?: throw IllegalStateException("config.yml missing required key: $key")
            return when (v) {
                is Number -> v.toInt()
                else -> v.toString().trim().toIntOrNull()
                    ?: throw IllegalStateException("config.yml key '$key' must be an int")
            }
        }

        fun reqString(key: String): String {
            val v = map[key] ?: throw IllegalStateException("config.yml missing required key: $key")
            val s = v.toString().trim()
            if (s.isEmpty()) throw IllegalStateException("config.yml key '$key' cannot be empty")
            return s
        }

        fun reqList(key: String): List<*> {
            val v = map[key] ?: throw IllegalStateException("config.yml missing required key: $key")
            return v as? List<*> ?: throw IllegalStateException("config.yml key '$key' must be a list")
        }

        fun reqMap(key: String): Map<*, *> {
            val v = map[key] ?: throw IllegalStateException("config.yml missing required key: $key")
            return v as? Map<*, *> ?: throw IllegalStateException("config.yml key '$key' must be an object/map")
        }

        fun optMap(from: Map<*, *>, key: String): Map<*, *>? =
            (from[key] as? Map<*, *>)

        fun asStringList(v: Any?): List<String> {
            val list = v as? List<*> ?: throw IllegalStateException("Expected a list")
            return list.map { it?.toString() ?: "" }
        }

        fun reqString(from: Map<*, *>, key: String, pathLabel: String): String {
            val v = from[key] ?: throw IllegalStateException("$pathLabel missing required key: $key")
            val s = v.toString().trim()
            if (s.isEmpty()) throw IllegalStateException("$pathLabel key '$key' cannot be empty")
            return s
        }

        fun reqInt(from: Map<*, *>, key: String, pathLabel: String): Int {
            val v = from[key] ?: throw IllegalStateException("$pathLabel missing required key: $key")
            return when (v) {
                is Number -> v.toInt()
                else -> v.toString().trim().toIntOrNull()
                    ?: throw IllegalStateException("$pathLabel key '$key' must be an int")
            }
        }

        val storageMap = reqMap("storage")
        val storageTypeStr = reqString(storageMap, "type", "storage").uppercase().replace('-', '_')
        val storage: StorageConfig =
            when (storageTypeStr) {
                "MONGO" -> {
                    val mongo = optMap(storageMap, "mongo") ?: emptyMap<Any?, Any?>()
                    MongoStorageConfig(
                        connectionString = reqString(mongo, "connectionString", "storage.mongo"),
                        database = reqString(mongo, "database", "storage.mongo")
                    )
                }
                "POSTGRES" -> {
                    val pg = optMap(storageMap, "postgres") ?: emptyMap<Any?, Any?>()
                    PostgresStorageConfig(
                        jdbcUrl = reqString(pg, "jdbcUrl", "storage.postgres"),
                        username = reqString(pg, "username", "storage.postgres"),
                        password = reqString(pg, "password", "storage.postgres"),
                        schema = reqString(pg, "schema", "storage.postgres")
                    )
                }
                "FLAT_GSON" -> {
                    val fg = optMap(storageMap, "flatGson") ?: emptyMap<Any?, Any?>()
                    FlatGsonStorageConfig(
                        baseDir = resolveMaybeRelativePath(reqString(fg, "baseDir", "storage.flatGson"), configBaseDir)
                    )
                }
                else -> throw IllegalStateException("storage.type must be one of: mongo, postgres, flat_gson")
            }

        val worlds = reqList("worlds").mapIndexed { idx, rawWorld ->
            val w = rawWorld as? Map<*, *> ?: throw IllegalStateException("worlds[$idx] must be an object/map")

            fun wReqInt(key: String): Int {
                val v = w[key] ?: throw IllegalStateException("worlds[$idx] missing key: $key")
                return when (v) {
                    is Number -> v.toInt()
                    else -> v.toString().trim().toIntOrNull()
                        ?: throw IllegalStateException("worlds[$idx].$key must be an int")
                }
            }

            fun wReqString(key: String): String {
                val v = w[key] ?: throw IllegalStateException("worlds[$idx] missing key: $key")
                val s = v.toString().trim()
                if (s.isEmpty()) throw IllegalStateException("worlds[$idx].$key cannot be empty")
                return s
            }

            val typesRaw = w["types"] ?: throw IllegalStateException("worlds[$idx] missing key: types")
            val types = EnumSet.noneOf(WorldType::class.java)
            val typeNames = asStringList(typesRaw)
            if (typeNames.isEmpty()) {
                throw IllegalStateException("worlds[$idx].types cannot be empty")
            }
            for (t in typeNames) {
                try {
                    types.add(WorldType.valueOf(t))
                } catch (_: IllegalArgumentException) {
                    throw IllegalStateException("worlds[$idx].types contains unknown type '$t'")
                }
            }

            val locationStr = wReqString("location")
            val location = try {
                WorldLocation.valueOf(locationStr)
            } catch (_: IllegalArgumentException) {
                throw IllegalStateException("worlds[$idx].location is unknown: '$locationStr'")
            }

            val authPublicKeyFromConfig = (w["authPublicKey"]?.toString() ?: "").trim()
            val authPublicKey = authPublicKeyFromConfig.ifBlank {
                val kp = Ed25519.generateKeyPair()
                val red = "\u001B[31m"
                val reset = "\u001B[0m"

                // Print so the user can copy into config/world server, then exit immediately.
                System.err.println("${red}CONFIG ERROR:${reset} worlds[$idx] (id=${wReqInt("id")}) is missing authPublicKey.")
                System.err.println("${red}Generated a new Ed25519 keypair. You MUST paste these before starting again:${reset}")
                System.err.println("${red}  Central config.yml -> authPublicKey: \"${kp.publicKey}\"${reset}")
                System.err.println("${red}  World server (secret) -> authPrivateKey: \"${kp.privateKey}\"${reset}")
                System.err.flush()
                exitProcess(1)
            }

            WorldConfig(
                id = wReqInt("id"),
                types = types,
                address = wReqString("address"),
                activity = wReqString("activity"),
                location = location,
                authPublicKey = authPublicKey
            )
        }

        if (worlds.isEmpty()) {
            throw IllegalStateException("config.yml worlds list cannot be empty")
        }
        val dupes = worlds.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (dupes.isNotEmpty()) {
            throw IllegalStateException("config.yml has duplicate world ids: ${dupes.sorted()}")
        }

        return AppConfig(
            rev = reqInt("rev"),
            name = reqString("name"),
            websiteUrl = reqString("websiteUrl"),
            storage = storage,
            worlds = worlds
        )
    }

    /**
     * Resolves config path relative to the current working directory, searching upwards if needed.
     */
    fun resolveConfigPath(path: Path = Path.of("config.yml")): Path? {
        if (path.exists()) return path
        return findConfigUpwards(path.fileName.toString())
    }

    private fun findConfigUpwards(fileName: String): Path? {
        var dir = Path.of("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve(fileName)
            if (candidate.exists()) return candidate
            val parent = dir.parent ?: return null
            dir = parent
        }
    }

    private fun resolveMaybeRelativePath(value: String, baseDir: Path): String {
        val p = Path.of(value)
        return if (p.isAbsolute) value else baseDir.resolve(p).normalize().toString()
    }
}

