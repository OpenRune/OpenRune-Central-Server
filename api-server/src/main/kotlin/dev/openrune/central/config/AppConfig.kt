package dev.openrune.central.config

import dev.openrune.central.world.WorldLocation
import dev.openrune.central.world.WorldType
import java.util.EnumSet

data class AppConfig(
    val rev: Int,
    val name: String,
    val websiteUrl: String,
    val storage: StorageConfig,
    val worlds: List<WorldConfig>
)

sealed interface StorageConfig {
    val type: StorageType
}

enum class StorageType {
    MONGO,
    POSTGRES,
    FLAT_GSON
}

data class MongoStorageConfig(
    val connectionString: String,
    val database: String,
) : StorageConfig {
    override val type: StorageType = StorageType.MONGO
}

data class PostgresStorageConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val schema: String
) : StorageConfig {
    override val type: StorageType = StorageType.POSTGRES
}

data class FlatGsonStorageConfig(
    val baseDir: String
) : StorageConfig {
    override val type: StorageType = StorageType.FLAT_GSON
}

data class WorldConfig(
    val id: Int,
    val types: EnumSet<WorldType>,
    val address: String,
    val activity: String,
    val location: WorldLocation,
    val authPublicKey: String
)

