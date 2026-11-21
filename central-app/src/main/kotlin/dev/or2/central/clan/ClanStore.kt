package dev.or2.central.clan

import java.util.concurrent.ConcurrentHashMap

/** Read-mostly Central runtime projection of committed clan rows. */
class ClanStore {
    private val byId = ConcurrentHashMap<Long, ClanStateRow>()
    private val clanIdByCharacter = ConcurrentHashMap<Int, Long>()
    private val clanIdByNormalizedName = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun replaceAll(states: Collection<ClanStateRow>) {
        byId.clear()
        clanIdByCharacter.clear()
        clanIdByNormalizedName.clear()
        states.forEach(::putInternal)
    }

    @Synchronized
    fun put(state: ClanStateRow) {
        val old = byId[state.clan.clanId]
        if (old != null) removeIndexes(old)
        putInternal(state)
    }

    @Synchronized
    fun remove(clanId: Long): ClanStateRow? {
        val removed = byId.remove(clanId) ?: return null
        removeIndexes(removed)
        return removed
    }

    fun get(clanId: Long): ClanStateRow? = byId[clanId]
    fun clanForCharacter(characterId: Int): ClanStateRow? = clanIdByCharacter[characterId]?.let(byId::get)
    fun clanIdForCharacter(characterId: Int): Long? = clanIdByCharacter[characterId]
    fun byName(name: String): ClanStateRow? = clanIdByNormalizedName[key(name)]?.let(byId::get)
    fun all(): List<ClanStateRow> = byId.values.sortedBy { it.clan.clanId }

    private fun putInternal(state: ClanStateRow) {
        byId[state.clan.clanId] = state
        clanIdByNormalizedName[key(state.clan.name)] = state.clan.clanId
        state.members.forEach { clanIdByCharacter[it.characterId] = state.clan.clanId }
    }

    private fun removeIndexes(state: ClanStateRow) {
        clanIdByNormalizedName.remove(key(state.clan.name), state.clan.clanId)
        state.members.forEach { clanIdByCharacter.remove(it.characterId, state.clan.clanId) }
    }

    private fun key(value: String): String = value.trim().lowercase()
}

class ClanLoader(private val repository: CentralClanRepository) {
    fun loadInto(store: ClanStore) = store.replaceAll(repository.loadAll())
}
