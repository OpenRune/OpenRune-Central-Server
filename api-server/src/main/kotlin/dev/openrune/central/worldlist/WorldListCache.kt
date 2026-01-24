package dev.openrune.central.worldlist

import io.netty.buffer.ByteBufAllocator
import dev.openrune.central.AppState
import dev.openrune.central.world.WorldManager
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

object WorldListCache {
    private val ttl: Duration = Duration.ofMinutes(5)
    private const val MAJOR_PLAYER_DELTA: Int = 25

    private val stateRef: AtomicReference<CachedWorldList?> = AtomicReference(null)
    private val lock = Any()

    fun getOrBuild(): ByteArray {
        val nowMs = System.currentTimeMillis()
        val existing = stateRef.get()
        if (existing != null && existing.isStillValid(nowMs)) {
            return existing.bytes
        }

        synchronized(lock) {
            val again = stateRef.get()
            if (again != null && again.isStillValid(nowMs)) {
                return again.bytes
            }

            val built = build(nowMs)
            stateRef.set(built)
            return built.bytes
        }
    }

    fun invalidate() {
        stateRef.set(null)
    }

    private fun build(nowMs: Long): CachedWorldList {
        val worlds = AppState.config.worlds
        val version = WorldManager.changeVersion()
        val snapshot = worlds.associateBy({ it.id }) { world ->
            WorldSnapshot(
                online = WorldManager.isWorldOnline(world.id),
                players = WorldManager.onlineCount(world.id)
            )
        }

        val worldListBuffer = encodeWorldList(worlds, ByteBufAllocator.DEFAULT)
        val bytes = ByteArray(worldListBuffer.readableBytes())
        worldListBuffer.gdata(bytes)
        return CachedWorldList(
            bytes = bytes,
            expiresAtMs = nowMs + ttl.toMillis(),
            version = version,
            snapshot = snapshot
        )
    }

    private data class CachedWorldList(
        val bytes: ByteArray,
        val expiresAtMs: Long,
        val version: Long,
        val snapshot: Map<Int, WorldSnapshot>
    ) {
        fun isStillValid(nowMs: Long): Boolean {
            if (nowMs >= expiresAtMs) return false

            // Fast path: if nothing in WorldManager changed, we don't need to re-check each world.
            if (WorldManager.changeVersion() == version) return true

            // Invalidate on online/offline change or "major" player swings.
            for ((worldId, prev) in snapshot) {
                val nowOnline = WorldManager.isWorldOnline(worldId)
                if (nowOnline != prev.online) return false

                val nowPlayers = WorldManager.onlineCount(worldId)
                if (kotlin.math.abs(nowPlayers - prev.players) >= MAJOR_PLAYER_DELTA) return false
            }

            return true
        }
    }

    private data class WorldSnapshot(
        val online: Boolean,
        val players: Int
    )
}

