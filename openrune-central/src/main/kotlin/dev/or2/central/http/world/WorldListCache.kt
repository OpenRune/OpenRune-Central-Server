package dev.or2.central.http.world

import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json

class WorldListCache(
    private val repository: WorldRepository,
) {
    private val payload = AtomicReference(ByteArray(0))
    private val worldListJsJson = AtomicReference("")

    private val json =
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    init {
        rebuild()
    }

    fun snapshot(): ByteArray = payload.get()

    fun worldListJsSnapshot(): String = worldListJsJson.get()

    fun rebuild() {
        val worlds = repository.findAllForList()
        payload.set(WorldListBinaryEncoder.encode(worlds))
        val dto =
            WorldsJsResponse(
                worlds.map { row ->
                    WorldsJsWorld(
                        id = row.worldId,
                        types = WorldFlag.runeliteTypeNamesFromMask(row.properties),
                        address = row.host,
                        activity = row.activity,
                        location = row.location,
                        players = row.population,
                    )
                },
            )
        worldListJsJson.set(json.encodeToString(dto))
    }
}
