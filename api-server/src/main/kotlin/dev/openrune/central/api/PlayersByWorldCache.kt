package dev.openrune.central.api

import dev.openrune.central.AppState
import dev.openrune.central.world.WorldManager

/**
 * Simple in-memory cache for "players online per world" so callers can poll frequently
 * without rebuilding a response on every request.
 */
object PlayersByWorldCache {
    private const val TTL_MS: Long = 3 * 60 * 1000L
    private val lock = Any()

    @Volatile private var cached: PlayersByWorldResponseDto? = null
    @Volatile private var cachedAtMs: Long = 0L
    @Volatile private var cachedVersion: Long = 0L

    fun getOrBuild(nowMs: Long = System.currentTimeMillis()): PlayersByWorldResponseDto {
        val version = WorldManager.changeVersion()
        val existing = cached
        if (existing != null && cachedVersion == version && (nowMs - cachedAtMs) <= TTL_MS) return existing

        synchronized(lock) {
            val again = cached
            val currentVersion = WorldManager.changeVersion()
            if (again != null && cachedVersion == currentVersion && (nowMs - cachedAtMs) <= TTL_MS) return again

            val worlds =
                AppState.config.worlds.map { w ->
                    WorldPlayersOnlineDto(
                        worldId = w.id,
                        playersOnline = WorldManager.onlineCount(w.id)
                    )
                }

            return PlayersByWorldResponseDto(
                updatedAtMs = nowMs,
                ttlMs = TTL_MS,
                worlds = worlds
            ).also {
                cached = it
                cachedAtMs = nowMs
                cachedVersion = currentVersion
            }
        }
    }
}

