package dev.or2.central.worldserver.session

import dev.or2.central.http.world.WorldListCache
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class StaleSessionSweeper(
    private val sessionRepository: SessionRepository,
    private val worldListCache: WorldListCache?,
    private val ttlMillis: Long,
    private val scheduler: ScheduledExecutorService,
) {
    private val log = LoggerFactory.getLogger(StaleSessionSweeper::class.java)

    fun start() {
        scheduler.scheduleAtFixedRate(
            {
                try {
                    val cutoff = System.currentTimeMillis() - ttlMillis
                    val removed = sessionRepository.deleteStale(cutoff)
                    if (removed > 0) {
                        log.info("Removed {} stale sessions", removed)
                        worldListCache?.rebuild()
                    }
                } catch (e: Exception) {
                    log.warn("Stale sessions sweep failed", e)
                }
            },
            1,
            1,
            TimeUnit.MINUTES,
        )
    }
}