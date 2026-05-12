package dev.or2.central.http

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class InfoResponse(val name: String, val version: String)

@Serializable
data class WorldOnlineDto(val worldId: Int, val players: Int)

@Serializable
data class PublicStatsResponse(
    val onlineTotal: Int,
    val worlds: List<WorldOnlineDto>,
    val distinctLoginsTodayUtc: Int,
)
