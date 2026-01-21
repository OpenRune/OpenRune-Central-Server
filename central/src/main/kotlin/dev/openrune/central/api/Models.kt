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
data class PrivateAuthTestResponseDto(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("worldId")
    val worldId: Int,
    @SerialName("bodyLength")
    val bodyLength: Int
)

@Serializable
data class StorageWriteResponseDto(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("bucket")
    val bucket: String,
    @SerialName("id")
    val id: String? = null
)

@Serializable
data class SendStatusDto(
    @SerialName("result")
    val result: String,
    @SerialName("error")
    val error: String? = null
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

