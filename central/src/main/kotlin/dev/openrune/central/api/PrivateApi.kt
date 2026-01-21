package dev.openrune.central.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import dev.openrune.central.AppState
import dev.openrune.central.config.WorldConfig
import dev.openrune.central.details.DetailsStore
import dev.openrune.central.storage.JsonBucket
import dev.openrune.central.logging.Loggable
import dev.openrune.central.logging.LoggingJson
import dev.openrune.central.world.WorldManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
fun Application.registerPrivateApi() {
    val logger = log
    val json = Json { ignoreUnknownKeys = true }

    suspend fun verifiedBody(
        call: ApplicationCall,
        world: WorldConfig,
        onInvalidSignature: suspend () -> Unit = {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto(error = "invalid signature"))
        }
    ): String? {
        val body = call.receiveText()
        if (!call.verifySignatureForBody(world, body)) {
            onInvalidSignature()
            return null
        }
        return body
    }

    routing {
        route("/api/private") {
            /**
             * Test endpoint: verify private auth signature end-to-end.
             *
             * World server signs:
             * "{timestamp}\n{worldId}\nPOST\n/api/private/test\n{body}"
             */
            post("/test") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post
                call.respond(PrivateAuthTestResponseDto(ok = true, worldId = world.id, bodyLength = body.length))
            }

            post("/login/request") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val req = try {
                    json.decodeFromString(LoginRequestDto.serializer(), body)
                } catch (_: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, LoginResponseDto(result = PlayerLoadResponse.MALFORMED, login = null))
                    return@post
                }

                val username = req.username.trim()
                val password = req.password
                val xteas = req.xteas

                if (username.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, LoginResponseDto(result = PlayerLoadResponse.MALFORMED, login = null))
                    return@post
                }
                val stableUid = DetailsStore.stableUidForLoginUsername(username)
                if (WorldManager.isOnlineAnywhere(stableUid)) {
                    call.respond(HttpStatusCode.Conflict, LoginResponseDto(result = PlayerLoadResponse.ALREADY_ONLINE, login = null))
                    return@post
                }
                if (xteas.isNotEmpty() && xteas.size != 4) {
                    call.respond(HttpStatusCode.BadRequest, LoginResponseDto(result = PlayerLoadResponse.MALFORMED, login = null))
                    return@post
                }
                if (password.isEmpty() && xteas.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, LoginResponseDto(result = PlayerLoadResponse.MALFORMED, login = null))
                    return@post
                }

                val res = DetailsStore.loadOrCreate(username, password, xteas)
                val status =
                    when (res.result) {
                        PlayerLoadResponse.NEW_ACCOUNT,
                        PlayerLoadResponse.LOAD -> HttpStatusCode.OK

                        PlayerLoadResponse.ALREADY_ONLINE -> HttpStatusCode.Conflict
                        PlayerLoadResponse.INVALID_CREDENTIALS -> HttpStatusCode.Unauthorized
                        PlayerLoadResponse.INVALID_RECONNECTION -> HttpStatusCode.Conflict
                        PlayerLoadResponse.MALFORMED -> HttpStatusCode.BadRequest
                        PlayerLoadResponse.OFFLINE_SERVER -> HttpStatusCode.ServiceUnavailable
                    }


                if (res.result == PlayerLoadResponse.NEW_ACCOUNT || res.result == PlayerLoadResponse.LOAD) {
                    val uid = res.login?.linkedAccounts?.firstOrNull()?.uid?.value ?: 0L
                    WorldManager.setOnline(world.id, uid)
                }

                call.respond(status, LoginResponseDto(result = res.result, login = res.login))
            }

            /**
             * Link a new account name to an existing login.
             * Request: { username, password, newAccount }
             * Response: { ok, linkedAccounts, error? }
             */
            post("/accounts/link") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val req = try {
                    json.decodeFromString(LoginRequestDto.serializer(), body)
                } catch (_: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, LinkAccountResponseDto(ok = false, error = "malformed"))
                    return@post
                }

                val newAccount = req.newAccount?.trim().orEmpty()
                if (newAccount.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, LinkAccountResponseDto(ok = false, error = "missing newAccount"))
                    return@post
                }

                val (ok, linked) = DetailsStore.linkAccount(req.username, req.password, newAccount, req.xteas)
                if (!ok) {
                    call.respond(HttpStatusCode.BadRequest, LinkAccountResponseDto(ok = false, linkedAccounts = linked, error = "failed"))
                    return@post
                }
                call.respond(LinkAccountResponseDto(ok = true, linkedAccounts = linked))
            }

            /**
             * Persist logout-related state for an account.
             * Body: { uid, account, previousXteas: [Int] }
             */
            post("/logout") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val req = try {
                    json.decodeFromString(LogoutRequestDto.serializer(), body)
                } catch (t: Throwable) {
                    logger.warn("logout: decode failed (world=${world.id}, bodyLen=${body.length})", t)
                    call.respond(HttpStatusCode.BadRequest, LogoutResponseDto(ok = false, error = "malformed"))
                    return@post
                }

                val account = req.account.trim()
                if (req.uid == 0L) {
                    logger.info("logout: malformed uid=${req.uid} accountLen=${account.length} xteasSize=${req.previousXteas.size} (world=${world.id})")
                    call.respond(HttpStatusCode.BadRequest, LogoutResponseDto(ok = false, error = "missing uid"))
                    return@post
                }
                if (req.previousXteas.isNotEmpty() && req.previousXteas.size != 4) {
                    logger.info("logout: malformed uid=${req.uid} accountLen=${account.length} xteasSize=${req.previousXteas.size} (world=${world.id})")
                    call.respond(HttpStatusCode.BadRequest, LogoutResponseDto(ok = false, error = "invalid xteas"))
                    return@post
                }

                val ok = DetailsStore.saveLogout(req.uid, account, req.previousXteas)
                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, LogoutResponseDto(ok = false, error = "not found"))
                    return@post
                }

                WorldManager.setOffline(world.id, req.uid)
                call.respond(LogoutResponseDto(ok = true))
            }

            /**
             * Persist player save JSON per (uid, account).
             * Body: { uid, account, data }
             */
            post("/player/save") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val req = try {
                    json.decodeFromString(PlayerSaveUpsertRequestDto.serializer(), body)
                } catch (t: Throwable) {
                    logger.warn("player/save: decode failed (world=${world.id}, bodyLen=${body.length})", t)
                    call.respond(HttpStatusCode.BadRequest, PlayerSaveUpsertResponseDto(ok = false, error = "malformed"))
                    return@post
                }

                val account = req.account.trim()
                if (req.uid == 0L || req.data.isBlank()) {
                    logger.info("player/save: malformed uid=${req.uid} accountLen=${account.length} dataLen=${req.data.length} (world=${world.id})")
                    call.respond(HttpStatusCode.BadRequest, PlayerSaveUpsertResponseDto(ok = false, error = "malformed"))
                    return@post
                }

                val key = DetailsStore.playerSaveKey(req.uid, account)
                if (key == null) {
                    call.respond(HttpStatusCode.NotFound, PlayerSaveUpsertResponseDto(ok = false, error = "not found"))
                    return@post
                }

                AppState.storage.upsert(JsonBucket.PLAYER_SAVES, key, req.data)
                call.respond(PlayerSaveUpsertResponseDto(ok = true))
            }

            /**
             * Load player save JSON per (uid, account).
             * Body: { uid, account }
             */
            post("/player/load") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val req = try {
                    json.decodeFromString(PlayerSaveLoadRequestDto.serializer(), body)
                } catch (t: Throwable) {
                    logger.warn("player/load: decode failed (world=${world.id}, bodyLen=${body.length})", t)
                    call.respond(HttpStatusCode.BadRequest, PlayerSaveLoadResponseDto(ok = false, error = "malformed"))
                    return@post
                }

                val account = req.account.trim()
                if (req.uid == 0L) {
                    logger.info("player/load: malformed uid=${req.uid} accountLen=${account.length} (world=${world.id})")
                    call.respond(HttpStatusCode.BadRequest, PlayerSaveLoadResponseDto(ok = false, error = "malformed"))
                    return@post
                }

                val key = DetailsStore.playerSaveKey(req.uid, account)
                if (key == null) {
                    call.respond(HttpStatusCode.NotFound, PlayerSaveLoadResponseDto(ok = false, error = "not found"))
                    return@post
                }

                val data = AppState.storage.get(JsonBucket.PLAYER_SAVES, key)
                if (data == null) {
                    call.respond(HttpStatusCode.NotFound, PlayerSaveLoadResponseDto(ok = false, error = "not found"))
                    return@post
                }

                call.respond(PlayerSaveLoadResponseDto(ok = true, data = data))
            }

            /**
             * Generic JSON ingestion endpoints (storage backend decides where it goes).
             *
             * Examples:
             *  - POST /api/private/store/PUNISHMENTS/{id}
             *  - POST /api/private/store/PLAYER_SAVES/{id}
             *  - POST /api/private/append/LOGS
             */
            post("/store/{bucket}/{id}") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val bucketStr = call.parameters["bucket"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(error = "missing bucket"))
                    return@post
                }
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(error = "missing id"))
                    return@post
                }

                val bucket = try {
                    JsonBucket.valueOf(bucketStr.uppercase())
                } catch (_: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(error = "unknown bucket: $bucketStr"))
                    return@post
                }

                AppState.storage.upsert(bucket, id, body)
                call.respond(StorageWriteResponseDto(ok = true, bucket = bucket.name, id = id))
            }

            post("/append/{bucket}") {
                val world = call.requireAuthedWorld() ?: return@post
                val body = verifiedBody(call, world) ?: return@post

                val bucketStr = call.parameters["bucket"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(error = "missing bucket"))
                    return@post
                }

                val bucket = try {
                    JsonBucket.valueOf(bucketStr.uppercase())
                } catch (_: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(error = "unknown bucket: $bucketStr"))
                    return@post
                }

                AppState.storage.append(bucket, body)
                call.respond(StorageWriteResponseDto(ok = true, bucket = bucket.name))
            }

            /**
             * Standardized "loggable" endpoint for world servers → central → storage.
             * Body: polymorphic Loggable JSON (from LoggingJson).
             */
            post("/logs") {
                val world = call.requireAuthedWorld() ?: return@post
                val body =
                    verifiedBody(call, world) {
                        call.respond(HttpStatusCode.Unauthorized, SendStatusDto(result = "FAILED", error = "invalid signature"))
                    } ?: return@post

                // Only allow known Loggable subtypes (decode will fail for unknown discriminator).
                val decoded = try {
                    LoggingJson.json.decodeFromString(Loggable.serializer(), body)
                } catch (t: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, SendStatusDto(result = "FAILED", error = "unknown/invalid log type"))
                    return@post
                }

                val normalized = LoggingJson.json.encodeToString(Loggable.serializer(), decoded)

                AppState.storage.append(JsonBucket.LOGS, decoded.logType, normalized)
                call.respond(SendStatusDto(result = "SUCCESS"))
            }
        }
    }
}

