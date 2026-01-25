package dev.openrune.central

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.application.ApplicationStopping
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import dev.openrune.central.api.registerPublicApi
import dev.openrune.central.config.AppConfig
import dev.openrune.central.config.ConfigLoader
import dev.openrune.central.JavaWsManager
import dev.openrune.central.logging.RuntimeLogging
import dev.openrune.central.metrics.PlayersOnlineHistoryRecorder
import dev.openrune.central.packet.CentralPacketServer

import dev.openrune.central.packet.PacketDispatcher
import dev.openrune.central.packet.routing.PacketRouter
import dev.openrune.central.storage.StorageFactory

import org.slf4j.event.Level


fun Application.module() {
    val appConfig = ConfigLoader.loadOrThrow()
    module(appConfig, generateJavaLocal = true)
}

internal fun Application.module(appConfig: AppConfig, generateJavaLocal: Boolean) {

    AppState.config = appConfig
    environment.log.info("Central config loaded: name='${appConfig.name}', rev='${appConfig.rev}', websiteUrl='${appConfig.websiteUrl}'")

    AppState.storage = StorageFactory.create(appConfig.storage)
    environment.log.info("Storage backend: ${appConfig.storage.type}")

    // Persist hourly/daily players-online history (per world + global).
    PlayersOnlineHistoryRecorder.start()

    if (generateJavaLocal) {
        JavaWsManager.ensure(appConfig)
    }

    if (!RuntimeLogging.isProd()) {
        install(CallLogging) {
            level = Level.INFO
        }
    }

    install(ContentNegotiation) {
        json(
            Json {
                // Pretty-printing is expensive and increases payload sizes; keep responses compact.
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                status = io.ktor.http.HttpStatusCode.InternalServerError,
                message = dev.openrune.central.api.ErrorResponseDto(
                    error = cause.message ?: "internal server error"
                )
            )
        }
    }

    registerPublicApi()

    val packetPort = System.getenv("CENTRAL_PACKET_PORT")?.toIntOrNull() ?: 43595
    val packetHandle = CentralPacketServer.start(host = "0.0.0.0", port = packetPort)

    // Default packet handlers live on the codecs (e.g., LoginRequestCodec.handle(...)).
    val router = PacketRouter()

    val dispatcher = PacketDispatcher(router).also { it.start() }

    environment.monitor.subscribe(ApplicationStopping) {
        dispatcher.stop()
        packetHandle.close()
    }
}

