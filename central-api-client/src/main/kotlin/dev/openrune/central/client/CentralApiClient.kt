package dev.openrune.central.client

import dev.openrune.central.api.PrivateAuthHeaders
import dev.openrune.central.api.buildPrivateAuthPayload
import dev.openrune.central.api.LoginRequestDto
import dev.openrune.central.api.LoginResponseDto
import dev.openrune.central.api.LogoutRequestDto
import dev.openrune.central.api.LogoutResponseDto
import dev.openrune.central.api.PlayerUID
import dev.openrune.central.api.PlayerLoadResponse
import dev.openrune.central.api.PlayerSaveLoadRequestDto
import dev.openrune.central.api.PlayerSaveLoadResponseDto
import dev.openrune.central.api.PlayerSaveUpsertRequestDto
import dev.openrune.central.api.PlayerSaveUpsertResponseDto
import dev.openrune.central.crypto.Ed25519
import dev.openrune.central.logging.Loggable
import dev.openrune.central.logging.LoggingJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

enum class SendResult {
    SUCCESS,
    FAILED
}

data class SendResponse(
    val result: SendResult,
    val statusCode: Int,
    val responseBody: String
)

class CentralApiClient(
    private val baseUrl: String,
    private val worldId: Int,
    private val worldPrivateKey: String
) {
    private val client: HttpClient = HttpClient.newHttpClient()

    private fun postSignedJson(path: String, body: String): HttpResponse<String> {
        val timestamp = System.currentTimeMillis().toString()
        val payload = buildPrivateAuthPayload(timestamp, worldId.toString(), "POST", path, body)
        val signature = Ed25519.sign(worldPrivateKey, payload)

        val req =
            HttpRequest.newBuilder()
                .uri(URI(baseUrl.trimEnd('/') + path))
                .header("Content-Type", "application/json")
                .header(PrivateAuthHeaders.WORLD_ID, worldId.toString())
                .header(PrivateAuthHeaders.TIMESTAMP_MS, timestamp)
                .header(PrivateAuthHeaders.SIGNATURE, signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    }

    /**
     * Sends a Loggable event to central: POST /api/private/logs
     */
    fun sendLog(log: Loggable): SendResponse {
        val path = "/api/private/logs"
        log.player = log.player.lowercase()
        val body = LoggingJson.json.encodeToString(Loggable.serializer(), log)

        return try {
            val resp = postSignedJson(path, body)
            val result = if (resp.statusCode() in 200..299) SendResult.SUCCESS else SendResult.FAILED
            SendResponse(result, resp.statusCode(), resp.body())
        } catch (_: Throwable) {
            SendResponse(SendResult.FAILED, 0, "OFFLINE_SERVER")
        }
    }

    /**
     * Requests a login decision from central: POST /api/private/login/request
     *
     * Body: { "username": "...", "password": "...", "xteas": [Int] }
     */
    fun requestLogin(username: String, password: String, xteas: IntArray = intArrayOf()): LoginResponseDto {
        val path = "/api/private/login/request"
        val body = LoggingJson.json.encodeToString(LoginRequestDto.serializer(), LoginRequestDto(username, password, xteas.toList()))

        return try {
            val resp = postSignedJson(path, body)
            LoggingJson.json.decodeFromString(LoginResponseDto.serializer(), resp.body())
        } catch (_: Throwable) {
            LoginResponseDto(result = PlayerLoadResponse.OFFLINE_SERVER, login = null)
        }
    }

    /**
     * Saves logout state to central: POST /api/private/logout
     *
     * Body: { "uid": Long, "account": "...", "previousXteas": [Int] }
     */
    fun logout(uid: PlayerUID, account: String, previousXteas: IntArray = intArrayOf()): LogoutResponseDto {
        val path = "/api/private/logout"
        val normalizedAccount = account.trim().lowercase()
        val body =
            LoggingJson.json.encodeToString(
                LogoutRequestDto.serializer(),
                LogoutRequestDto(uid = uid.value, account = normalizedAccount, previousXteas = previousXteas.toList())
            )

        return try {
            val resp = postSignedJson(path, body)
            LoggingJson.json.decodeFromString(LogoutResponseDto.serializer(), resp.body())
        } catch (_: Throwable) {
            LogoutResponseDto(ok = false, error = "OFFLINE_SERVER")
        }
    }

    /**
     * Saves a player save payload to central: POST /api/private/player/save
     *
     * `data` should be a JSON string (often `Document.toJson()`).
     */
    fun savePlayer(uid: PlayerUID, account: String, data: String): PlayerSaveUpsertResponseDto {
        val path = "/api/private/player/save"
        val normalizedAccount = account.trim().lowercase()
        val body =
            LoggingJson.json.encodeToString(
                PlayerSaveUpsertRequestDto.serializer(),
                PlayerSaveUpsertRequestDto(uid = uid.value, account = normalizedAccount, data = data)
            )

        return try {
            val resp = postSignedJson(path, body)
            LoggingJson.json.decodeFromString(PlayerSaveUpsertResponseDto.serializer(), resp.body())
        } catch (_: Throwable) {
            PlayerSaveUpsertResponseDto(ok = false, error = "OFFLINE_SERVER")
        }
    }

    /**
     * Loads a player save payload from central: POST /api/private/player/load
     */
    fun loadPlayer(uid: PlayerUID, account: String): PlayerSaveLoadResponseDto {
        val path = "/api/private/player/load"
        val normalizedAccount = account.trim().lowercase()
        val body =
            LoggingJson.json.encodeToString(
                PlayerSaveLoadRequestDto.serializer(),
                PlayerSaveLoadRequestDto(uid = uid.value, account = normalizedAccount)
            )

        return try {
            val resp = postSignedJson(path, body)
            LoggingJson.json.decodeFromString(PlayerSaveLoadResponseDto.serializer(), resp.body())
        } catch (_: Throwable) {
            PlayerSaveLoadResponseDto(ok = false, error = "OFFLINE_SERVER")
        }
    }
}

