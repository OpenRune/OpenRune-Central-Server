package dev.or2.central.http

import dev.or2.central.worldserver.logging.LoginEventRepository
import dev.or2.central.worldserver.session.SessionRepository
import dev.or2.central.http.world.WorldListCache
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Instant
import java.time.ZoneOffset

data class CentralHttpContext(
    val sessionRepository: SessionRepository,
    val loginEventRepository: LoginEventRepository,
    val worldListCache: WorldListCache,
    val prometheus: PrometheusMeterRegistry,
)

fun Route.centralHttpRoutes(ctx: CentralHttpContext) {
    singlePageApplication {
        applicationRoute = "/admin"
        useResources = true
        filesPath = "static/admin"
    }

    get("/api/v1/health") { call.respond(HealthResponse("ok")) }
    get("/api/v1/info") { call.respond(InfoResponse("OpenRune Central", "0-dev")) }

    get("/api/v1/public/stats") {
        val counts = ctx.sessionRepository.countsByWorld()
        val worlds =
            counts.entries
                .sortedBy { it.key }
                .map { WorldOnlineDto(it.key, it.value) }
        val dayUtc =
            Instant.now()
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .toString()
        val loginsToday = ctx.loginEventRepository.countDistinctAccountsForDay(dayUtc)
        call.respond(
            PublicStatsResponse(
                onlineTotal = ctx.sessionRepository.totalOnline(),
                worlds = worlds,
                distinctLoginsTodayUtc = loginsToday,
            ),
        )
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

    get("/actuator/health") {
        call.respondText("""{"status":"UP"}""", ContentType.Application.Json)
    }

    get("/actuator/prometheus") {
        call.respondText(
            ctx.prometheus.scrape(),
            ContentType.parse("text/plain; version=0.0.4; charset=utf-8"),
        )
    }
}
