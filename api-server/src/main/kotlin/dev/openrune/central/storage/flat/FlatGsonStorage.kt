package dev.openrune.central.storage.flat

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.openrune.central.config.FlatGsonStorageConfig
import dev.openrune.central.storage.JsonBucket
import dev.openrune.central.storage.JsonStorage
import dev.openrune.central.storage.StorageNaming
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FlatGsonStorage(private val config: FlatGsonStorageConfig) : JsonStorage {
    private val gson = Gson()

    private val baseDir: Path = Path.of(config.baseDir)
    private val recordsDir: Path = baseDir.resolve("records")
    private val logsDir: Path = baseDir.resolve("logs")

    // Per-file locks to keep writes atomic-ish under concurrency
    private val locks = ConcurrentHashMap<String, Any>()

    init {
        Files.createDirectories(recordsDir)
        Files.createDirectories(logsDir)
    }

    override suspend fun upsert(bucket: JsonBucket, id: String, json: String) {
        val normalized = normalizeJson(json)
        val dir = recordsDir.resolve(StorageNaming.bucketSnake(bucket))
        Files.createDirectories(dir)
        val file = dir.resolve(safeFileName(id) + ".json")
        val tmp = dir.resolve(safeFileName(id) + ".json.tmp")

        val lock = locks.computeIfAbsent(file.toString()) { Any() }
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                Files.writeString(tmp, normalized, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
        }
    }

    override suspend fun get(bucket: JsonBucket, id: String): String? {
        val dir = recordsDir.resolve(StorageNaming.bucketSnake(bucket))
        val file = dir.resolve(safeFileName(id) + ".json")
        return withContext(Dispatchers.IO) {
            if (!Files.exists(file)) null else Files.readString(file, StandardCharsets.UTF_8)
        }
    }

    override suspend fun append(bucket: JsonBucket, json: String) {
        val normalized = normalizeJson(json)
        val dir = logsDir.resolve(StorageNaming.bucketSnake(bucket))
        Files.createDirectories(dir)
        val file = dir.resolve("append.ndjson")
        val lock = locks.computeIfAbsent(file.toString()) { Any() }
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                Files.writeString(
                    file,
                    normalized + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                )
            }
        }
    }

    override suspend fun append(bucket: JsonBucket, partition: String, json: String) {
        val normalized = normalizeJson(json)
        Files.createDirectories(logsDir)

        val fileName =
            when (bucket) {
                JsonBucket.LOGS -> StorageNaming.logPartitionName(partition) + ".json"
                else -> StorageNaming.bucketSnake(bucket) + "_" + StorageNaming.toSnakeCase(partition) + ".json"
            }

        val file = logsDir.resolve(fileName)
        val lock = locks.computeIfAbsent(file.toString()) { Any() }
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                Files.writeString(
                    file,
                    normalized + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                )
            }
        }
    }

    override fun close() {
        // nothing
    }

    private fun normalizeJson(json: String): String {
        // Validates JSON and normalizes it for consistent storage
        val el = JsonParser.parseString(json)
        return gson.toJson(el)
    }

    private fun safeFileName(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9._-]"), "_").take(180)
}

