package dev.or2.central.http

import dev.or2.central.server.logging.CentralActivityLogRepository
import dev.or2.central.server.session.WorldSessionRepository
import dev.or2.central.http.world.WorldListCache
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

data class CentralHttpContext(
    val sessionRepository: WorldSessionRepository,
    val activityLogRepository: CentralActivityLogRepository,
    val worldListCache: WorldListCache,
)

fun Route.centralHttpRoutes(ctx: CentralHttpContext) {
    singlePageApplication {
        applicationRoute = "/admin"
        useResources = true
        filesPath = "static/admin"
    }

    get("/worldslist.ws") {
        val bytes = ctx.worldListCache.snapshot()
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }

    get("/worlds.js") {
        val body = ctx.worldListCache.worldListJsSnapshot()
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.respondText(body, ContentType.Application.Json)
    }

}
