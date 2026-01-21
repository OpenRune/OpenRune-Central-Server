package dev.openrune.central.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorldPlayersOnlineDto(
    @SerialName("worldId")
    val worldId: Int,
    @SerialName("playersOnline")
    val playersOnline: Int
)

@Serializable
data class PlayersByWorldResponseDto(
    @SerialName("updatedAtMs")
    val updatedAtMs: Long,
    @SerialName("ttlMs")
    val ttlMs: Long,
    @SerialName("worlds")
    val worlds: List<WorldPlayersOnlineDto>
)

