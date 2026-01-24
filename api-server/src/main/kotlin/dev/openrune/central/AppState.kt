package dev.openrune.central

import dev.openrune.central.config.AppConfig
import dev.openrune.central.config.WorldConfig
import dev.openrune.central.storage.JsonStorage

object AppState {
    @Volatile
    lateinit var config: AppConfig

    @Volatile
    lateinit var storage: JsonStorage

    val worldsById: Map<Int, WorldConfig>
        get() = config.worlds.associateBy { it.id }
}

