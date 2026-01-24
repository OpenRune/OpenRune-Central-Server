package dev.openrune.central.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import dev.openrune.central.AppState
import dev.openrune.central.config.WorldConfig
import dev.openrune.central.crypto.Ed25519
import kotlin.math.abs

suspend fun ApplicationCall.requireAuthedWorld(): WorldConfig? {
    val worldId = request.headers[PrivateAuthHeaders.WORLD_ID]
    val timestamp = request.headers[PrivateAuthHeaders.TIMESTAMP_MS]
    val signature = request.headers[PrivateAuthHeaders.SIGNATURE]

    if (worldId.isNullOrBlank() || timestamp.isNullOrBlank() || signature.isNullOrBlank()) {
        respond(
            HttpStatusCode.Unauthorized,
            ErrorResponseDto(
                error = "missing private api auth headers",
                requiredHeaders = listOf(
                    PrivateAuthHeaders.WORLD_ID,
                    PrivateAuthHeaders.TIMESTAMP_MS,
                    PrivateAuthHeaders.SIGNATURE
                )
            )
        )
        return null
    }

    val worldIdInt = worldId.toIntOrNull()
    if (worldIdInt == null) {
        respond(
            HttpStatusCode.Unauthorized,
            ErrorResponseDto(
                error = "invalid world id (must be an int)",
                knownWorldIds = AppState.worldsById.keys.sorted()
            )
        )
        return null
    }

    val world = AppState.worldsById[worldIdInt]
    if (world == null) {
        respond(
            HttpStatusCode.Unauthorized,
            ErrorResponseDto(
                error = "unknown world id: $worldIdInt",
                knownWorldIds = AppState.worldsById.keys.sorted()
            )
        )
        return null
    }

    if (world.authPublicKey.isBlank()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponseDto(error = "world authPublicKey not configured"))
        return null
    }

    val ts = timestamp.toLongOrNull()
    if (ts == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponseDto(error = "invalid timestamp"))
        return null
    }

    // Anti-replay window (5 minutes)
    val now = System.currentTimeMillis()
    if (abs(now - ts) > 5 * 60 * 1000L) {
        respond(HttpStatusCode.Unauthorized, ErrorResponseDto(error = "timestamp too old/new"))
        return null
    }

    // NOTE: body is verified by the route using verifySignatureForBody(...)
    return world
}

suspend fun ApplicationCall.verifySignatureForBody(world: WorldConfig, body: String): Boolean {
    val worldId = request.headers[PrivateAuthHeaders.WORLD_ID] ?: return false
    val timestamp = request.headers[PrivateAuthHeaders.TIMESTAMP_MS] ?: return false
    val signature = request.headers[PrivateAuthHeaders.SIGNATURE] ?: return false

    val data = buildPrivateAuthPayload(
        timestampMs = timestamp,
        worldId = worldId,
        method = request.httpMethod.value,
        path = request.path(),
        body = body
    )

    return Ed25519.verify(world.authPublicKey, data, signature)
}

