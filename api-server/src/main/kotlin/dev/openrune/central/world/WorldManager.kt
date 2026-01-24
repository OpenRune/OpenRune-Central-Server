package dev.openrune.central.world

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object WorldManager {
    private val states: ConcurrentHashMap<Int, WorldState> = ConcurrentHashMap()
    private val onlineIndex: ConcurrentHashMap<Long, Int> = ConcurrentHashMap()

    private val changeCounter = AtomicLong(0)

    fun state(worldId: Int): WorldState = states.computeIfAbsent(worldId) { WorldState() }

    fun onlineCount(worldId: Int): Int = state(worldId).onlinePlayers.size

    fun isOnline(worldId: Int, uid: Long): Boolean = state(worldId).onlinePlayers.contains(uid)

    /**
     * Checks if a uid is currently marked online in any world.
     * Used to prevent concurrent logins across worlds.
     */
    fun isOnlineAnywhere(uid: Long): Boolean {
        if (uid == 0L) return false
        return onlineIndex.containsKey(uid)
    }

    fun isWorldOnline(worldId: Int): Boolean = state(worldId).worldOnline

    fun setWorldOnline(worldId: Int, online: Boolean) {
        val s = state(worldId)
        if (s.worldOnline != online) {
            s.worldOnline = online
            changeCounter.incrementAndGet()
        }
    }

    fun setOnline(worldId: Int, uid: Long) {
        if (uid == 0L) return
        val inserted = onlineIndex.putIfAbsent(uid, worldId) == null
        if (!inserted) return

        // Keep per-world set for accurate counts and potential per-world membership checks.
        val changed = state(worldId).onlinePlayers.add(uid)
        if (changed) changeCounter.incrementAndGet()
    }

    fun setOffline(worldId: Int, uid: Long) {
        if (uid == 0L) return
        val removedFromWorld = state(worldId).onlinePlayers.remove(uid)
        val removedFromIndex = onlineIndex.remove(uid, worldId)
        if (removedFromWorld || removedFromIndex) changeCounter.incrementAndGet()
    }

    fun changeVersion(): Long = changeCounter.get()
}

class WorldState {
    val onlinePlayers: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    @Volatile var worldOnline: Boolean = true
}

