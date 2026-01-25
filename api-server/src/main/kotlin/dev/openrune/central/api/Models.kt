package dev.openrune.central.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OkResponseDto(
    @SerialName("ok")
    val ok: Boolean
)

@Serializable
data class ErrorResponseDto(
    @SerialName("error")
    val error: String,
    @SerialName("requiredHeaders")
    val requiredHeaders: List<String>? = null,
    @SerialName("knownWorldIds")
    val knownWorldIds: List<Int>? = null
)

@Serializable
data class WorldInfoDto(
    @SerialName("id")
    val id: Int,
    @SerialName("activity")
    val activity: String,
    @SerialName("playersOnline")
    val playersOnline: Int,
    @SerialName("online")
    val online: Boolean
)

