package dev.or2.central.social

import java.util.concurrent.ConcurrentHashMap

data class OnlineCharacter(
    val characterId: Int,
    val worldId: Int,
    val accountId: Long,
)

class OnlinePresenceIndex {
    private val byCharacterId = ConcurrentHashMap<Int, OnlineCharacter>()

    fun put(
        characterId: Int,
        worldId: Int,
        accountId: Long,
    ) {
        if (characterId <= 0 || worldId <= 0) {
            return
        }
        byCharacterId[characterId] = OnlineCharacter(characterId, worldId, accountId)
    }

    fun remove(characterId: Int): OnlineCharacter? =
        if (characterId <= 0) {
            null
        } else {
            byCharacterId.remove(characterId)
        }

    /** Remove only if this character still belongs to the departing world. */
    fun removeIfOnWorld(characterId: Int, worldId: Int): OnlineCharacter? {
        if (characterId <= 0 || worldId <= 0) return null
        while (true) {
            val current = byCharacterId[characterId] ?: return null
            if (current.worldId != worldId) return null
            if (byCharacterId.remove(characterId, current)) return current
        }
    }

    fun removeAllOnWorld(worldId: Int): List<OnlineCharacter> {
        if (worldId <= 0) {
            return emptyList()
        }
        val removed = mutableListOf<OnlineCharacter>()
        for ((characterId, entry) in byCharacterId) {
            if (entry.worldId == worldId) {
                if (byCharacterId.remove(characterId, entry)) removed.add(entry)
            }
        }
        return removed
    }

    fun removeByAccount(accountId: Long): List<OnlineCharacter> {
        if (accountId <= 0L) {
            return emptyList()
        }
        val removed = mutableListOf<OnlineCharacter>()
        for ((characterId, entry) in byCharacterId) {
            if (entry.accountId == accountId) {
                if (byCharacterId.remove(characterId, entry)) removed.add(entry)
            }
        }
        return removed
    }

    fun worldFor(characterId: Int): Int? = byCharacterId[characterId]?.worldId

    fun isOnline(
        characterId: Int,
        worldId: Int,
    ): Boolean {
        val entry = byCharacterId[characterId] ?: return false
        return entry.worldId == worldId
    }

    fun get(characterId: Int): OnlineCharacter? = byCharacterId[characterId]

    fun hydrate(entries: Collection<OnlineCharacter>) {
        byCharacterId.clear()
        for (entry in entries) {
            if (entry.characterId > 0 && entry.worldId > 0) {
                byCharacterId[entry.characterId] = entry
            }
        }
    }

    fun allOnline(): Collection<OnlineCharacter> = byCharacterId.values
}
