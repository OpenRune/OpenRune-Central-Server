package dev.or2.central

import com.zaxxer.hikari.HikariDataSource
import dev.or2.central.account.AccountRepository
import dev.or2.central.account.PasswordHasher
import dev.or2.central.util.config.CentralRuntimeConfig
import dev.or2.central.util.config.createCentralDataSource
import dev.or2.central.http.CentralHttpContext
import dev.or2.central.http.centralHttpRoutes
import dev.or2.central.worldserver.logging.LoginEventRepository
import dev.or2.central.worldserver.session.StaleSessionSweeper
import dev.or2.central.account.punishment.PunishmentPgListenerService
import dev.or2.central.account.punishment.PunishmentRepository
import dev.or2.central.worldserver.session.SessionRepository
import dev.or2.central.http.world.WorldKeyVerifier
import dev.or2.central.http.world.WorldListCache
import dev.or2.central.http.world.WorldLoginGateRepository
import dev.or2.central.http.world.WorldRepository
import dev.or2.central.http.world.ops.WorldOpsRepository
import dev.or2.central.worldserver.net.WorldServerTcpServer
import dev.or2.central.worldserver.net.push.WorldServerPushChannelRegistry
import dev.or2.central.worldserver.session.WorldServerSessionService
import dev.or2.central.worldserver.telemetry.MicrometerWorldServerTelemetry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlinx.serialization.json.Json

fun Application.installOpenRuneCentral(centralConfig: CentralRuntimeConfig) {
    environment.log.info("OpenRune Central PostgreSQL: {}", centralConfig.jdbcUrl)

    val dataSource: DataSource = createCentralDataSource(centralConfig)

    CentralSchemaBootstrap.apply(dataSource)

    val worldRepository = WorldRepository(dataSource)
    val worldListCache = WorldListCache(worldRepository)
    val accountRepository = AccountRepository(dataSource)
    val passwordHasher = PasswordHasher()
    val sessionRepository = SessionRepository(dataSource)
    val loginEventRepository = LoginEventRepository(dataSource)
    val punishmentRepository = PunishmentRepository(dataSource)
    val worldOpsRepository = WorldOpsRepository(dataSource)

    val worldKeyVerifier = WorldKeyVerifier()
    val worldLoginGateRepository = WorldLoginGateRepository(dataSource)

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    ClassLoaderMetrics().bindTo(prometheus)
    JvmMemoryMetrics().bindTo(prometheus)
    JvmGcMetrics().bindTo(prometheus)
    ProcessorMetrics().bindTo(prometheus)

    val worldServerTelemetry = MicrometerWorldServerTelemetry(prometheus)
    val worldServerPushChannelRegistry = WorldServerPushChannelRegistry()

    val worldServerSessionService =
        WorldServerSessionService(
            dataSource = dataSource,
            worldRepository = worldRepository,
            worldKeyVerifier = worldKeyVerifier,
            accountRepository = accountRepository,
            passwordHasher = passwordHasher,
            sessionRepository = sessionRepository,
            worldListCache = worldListCache,
            punishmentRepository = punishmentRepository,
            worldOpsRepository = worldOpsRepository,
            worldLoginGateRepository = worldLoginGateRepository,
            telemetry = worldServerTelemetry,
        )

    val worldsLinkPort = centralConfig.worldsLinkPort
    val worldServerTcpExecutor: ExecutorService? =
        if (worldsLinkPort != null) {
            val threads = centralConfig.worldsLinkHandlerThreads
            val queueSize = centralConfig.worldsLinkHandlerQueueSize
            ThreadPoolExecutor(
                threads,
                threads,
                60L,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(queueSize),
                { runnable -> Thread(runnable, "openrune-worldserver").apply { isDaemon = true } },
                ThreadPoolExecutor.CallerRunsPolicy(),
            )
        } else {
            null
        }
    val worldServerTcpServer: WorldServerTcpServer? =
        if (worldsLinkPort != null && worldServerTcpExecutor != null) {
            WorldServerTcpServer(
                port = worldsLinkPort,
                sessionService = worldServerSessionService,
                executor = worldServerTcpExecutor,
                pushChannelRegistry = worldServerPushChannelRegistry,
                soBacklog = centralConfig.worldsLinkSoBacklog,
                readTimeoutSeconds = centralConfig.worldsLinkReadTimeoutSeconds,
                maxConnectionsPerIp = centralConfig.worldsLinkMaxConnectionsPerIp,
                maxConnectionsTotal = centralConfig.worldsLinkMaxConnectionsTotal,
                maxFramesPerSecond = centralConfig.worldsLinkMaxFramesPerSecond.toDouble(),
                maxFrameBurst = centralConfig.worldsLinkMaxFrameBurst.toDouble(),
            )
        } else {
            null
        }

    val sessionsTtlMillis = centralConfig.sessionsTtlMillis
    val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "openrune-central-scheduler").apply { isDaemon = true }
        }
    val staleSessionSweeper =
        StaleSessionSweeper(
            sessionRepository = sessionRepository,
            worldListCache = worldListCache,
            ttlMillis = sessionsTtlMillis,
            scheduler = scheduler,
        )

    val punishmentPgListener =
        PunishmentPgListenerService(
            dataSource = dataSource,
            worldServerPushChannelRegistry = worldServerPushChannelRegistry,
            sessionRepository = sessionRepository,
            worldListCache = worldListCache,
        )

    monitor.subscribe(ApplicationStarted) {
        staleSessionSweeper.start()
        worldServerTcpServer?.start()
        punishmentPgListener.start()
        scheduler.scheduleAtFixedRate(
            {
                try {
                    worldListCache.rebuild()
                } catch (e: Exception) {
                    environment.log.debug("worlds list cache rebuild failed: {}", e.message)
                }
            },
            45L,
            45L,
            TimeUnit.SECONDS,
        )
    }
    monitor.subscribe(ApplicationStopped) {
        punishmentPgListener.stop()
        worldServerTcpServer?.stop()
        worldServerTcpExecutor?.shutdown()
        scheduler.shutdown()
        (dataSource as HikariDataSource).close()
    }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respondText("Internal Server Error", ContentType.Text.Plain, status = HttpStatusCode.InternalServerError)
        }
    }

    install(MicrometerMetrics) {
        registry = prometheus
    }

    routing {
        centralHttpRoutes(
            CentralHttpContext(
                sessionRepository = sessionRepository,
                loginEventRepository = loginEventRepository,
                worldListCache = worldListCache,
                prometheus = prometheus,
            ),
        )
    }
}
