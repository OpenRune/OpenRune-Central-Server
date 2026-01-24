package dev.openrune.central.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import dev.openrune.central.AppState
import dev.openrune.central.JavaWsManager
import dev.openrune.central.world.WorldManager
import dev.openrune.central.worldlist.WorldListCache
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun Application.registerPublicApi() {
    val publicJson =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }

    suspend fun <T> respondPublicJson(call: ApplicationCall, value: T, serializer: KSerializer<T>) {
        call.respondText(
            text = publicJson.encodeToString(serializer, value),
            contentType = ContentType.Application.Json
        )
    }

    routing {
        get("/worldlist.ws") {
            call.respondBytes(
                bytes = WorldListCache.getOrBuild(),
                contentType = ContentType.Application.OctetStream
            )
        }

        get("/java_local.ws") {
            call.respondText(
                text = JavaWsManager.readText(),
                contentType = ContentType.Text.Plain
            )
        }

        route("/api/public") {
            get("/health") {
                respondPublicJson(call, OkResponseDto(ok = true), OkResponseDto.serializer())
            }

            /**
             * Lightweight endpoint for polling player counts per world.
             * Cached in-memory and refreshed at most every ~3 minutes.
             */
            get("/players/world") {
                respondPublicJson(call, PlayersByWorldCache.getOrBuild(), PlayersByWorldResponseDto.serializer())
            }

            get("/players/world/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val world = AppState.worldsById[id]
                if (world == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponseDto(error = "unknown world"))
                    return@get
                }
                respondPublicJson(
                    call,
                    WorldPlayersOnlineDto(
                        worldId = world.id,
                        playersOnline = WorldManager.onlineCount(world.id)
                    ),
                    WorldPlayersOnlineDto.serializer()
                )
            }

            get("/worlds") {
                val worlds = AppState.config.worlds.map { world ->
                    WorldInfoDto(
                        id = world.id,
                        activity = world.activity,
                        playersOnline = WorldManager.onlineCount(world.id),
                        online = WorldManager.isWorldOnline(world.id)
                    )
                }
                respondPublicJson(call, worlds, ListSerializer(WorldInfoDto.serializer()))
            }

            get("/worlds/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val world = AppState.worldsById[id]
                if (world == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponseDto(error = "unknown world"))
                    return@get
                }
                respondPublicJson(
                    call,
                    WorldInfoDto(
                        id = world.id,
                        activity = world.activity,
                        playersOnline = WorldManager.onlineCount(world.id),
                        online = WorldManager.isWorldOnline(world.id)
                    ),
                    WorldInfoDto.serializer()
                )
            }
        }
    }
}

