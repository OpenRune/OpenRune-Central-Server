package dev.openrune.central.storage

import dev.openrune.central.config.FlatGsonStorageConfig
import dev.openrune.central.config.MongoStorageConfig
import dev.openrune.central.config.PostgresStorageConfig
import dev.openrune.central.config.StorageConfig
import dev.openrune.central.config.StorageType
import dev.openrune.central.storage.flat.FlatGsonStorage
import dev.openrune.central.storage.mongo.MongoJsonStorage
import dev.openrune.central.storage.postgres.PostgresJsonStorage

object StorageFactory {
    fun create(config: StorageConfig): JsonStorage {
        return when (config.type) {
            StorageType.FLAT_GSON -> FlatGsonStorage(config as FlatGsonStorageConfig)
            StorageType.POSTGRES -> PostgresJsonStorage(config as PostgresStorageConfig)
            StorageType.MONGO -> MongoJsonStorage(config as MongoStorageConfig)
        }
    }
}

