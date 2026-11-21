package dev.or2.central.pvpa

import dev.or2.central.worldlink.protocol.pvpa.*

// Worlds keep the last accepted revision across a Central restart, so a new Central process
// must not restart group revisions at zero (same scheme as LiveChatPresenceIndex).
private val REVISION_EPOCH: Long = System.currentTimeMillis() * 1_000_000L

/**
 * Central-side authoritative grouping state. No RS client ranks or transport assumptions.
 *
 * This class has no automatic matchmaking, invitations, staging, client-rank
 * conversion, database writes, or Worldlink registrations. Those need their own verified contracts.
 *
 * Caller must authenticate that characterId currently belongs to worldId. A move between
 * worlds must be driven by a verified Central presence transition, never a client claim.
 */
class PvPAGroupingState {
    private data class Group(
        val members: LinkedHashMap<Int, PvPAGroupMember> = linkedMapOf(),
        var revision: Long = REVISION_EPOCH,
    )
    private val groups = mutableMapOf<PvPAGroupKey, Group>()
    private val groupByCharacter = mutableMapOf<Int, PvPAGroupKey>()

    @Synchronized
    fun load(characterId: Int, displayName: String, worldId: Int, key: PvPAGroupKey): PvPAGroupSnapshot {
        require(characterId > 0 && worldId > 0 && displayName.isNotBlank())
        val oldKey = groupByCharacter[characterId]
        if (oldKey != null && oldKey != key) removeInternal(characterId, oldKey)
        val group = groups.getOrPut(key) { Group() }
        val existing = group.members[characterId]
        val replacement = PvPAGroupMember(characterId, displayName, worldId,
            if (oldKey == key) existing?.state ?: PvPAQueueState.GUEST else PvPAQueueState.GUEST)
        if (existing != replacement) { group.members[characterId] = replacement; group.revision++ }
        groupByCharacter[characterId] = key
        return snapshotInternal(key, group)
    }

    @Synchronized
    fun signUp(characterId: Int, worldId: Int): PvPAGroupSnapshot? = changeState(characterId, worldId, PvPAQueueState.SIGNED_UP)

    @Synchronized
    fun withdraw(characterId: Int, worldId: Int): PvPAGroupSnapshot? = changeState(characterId, worldId, PvPAQueueState.GUEST)

    @Synchronized
    fun leave(characterId: Int, worldId: Int): PvPAGroupSnapshot? {
        val key = groupByCharacter[characterId] ?: return null
        val member = groups[key]?.members?.get(characterId) ?: return null
        if (member.worldId != worldId) return null
        return removeInternal(characterId, key)
    }

    /** Invoked only after Central verifies a successful world transfer. */
    @Synchronized
    fun transferVerified(characterId: Int, oldWorldId: Int, newWorldId: Int): PvPAGroupSnapshot? {
        require(newWorldId > 0)
        val key = groupByCharacter[characterId] ?: return null
        val group = groups[key] ?: return null
        val member = group.members[characterId] ?: return null
        if (member.worldId != oldWorldId) return null
        if (member.worldId != newWorldId) {
            group.members[characterId] = member.copy(worldId = newWorldId)
            group.revision++
        }
        return snapshotInternal(key, group)
    }

    @Synchronized
    fun forCharacter(characterId: Int): PvPAGroupSnapshot? {
        val key = groupByCharacter[characterId] ?: return null
        val group = groups[key] ?: return null
        return snapshotInternal(key, group)
    }

    @Synchronized
    fun forGroup(key: PvPAGroupKey): PvPAGroupSnapshot = snapshotInternal(key, groups.getOrPut(key) { Group() })

    private fun changeState(characterId: Int, worldId: Int, target: PvPAQueueState): PvPAGroupSnapshot? {
        val key = groupByCharacter[characterId] ?: return null
        val group = groups[key] ?: return null
        val existing = group.members[characterId] ?: return null
        if (existing.worldId != worldId) return null
        if (existing.state != target) { group.members[characterId] = existing.copy(state = target); group.revision++ }
        return snapshotInternal(key, group)
    }

    private fun removeInternal(characterId: Int, key: PvPAGroupKey): PvPAGroupSnapshot? {
        val group = groups[key] ?: return null
        if (group.members.remove(characterId) == null) return null
        group.revision++
        groupByCharacter.remove(characterId)
        return snapshotInternal(key, group)
    }

    private fun snapshotInternal(key: PvPAGroupKey, group: Group): PvPAGroupSnapshot =
        PvPAGroupSnapshot(key, group.revision, group.members.values.toList())
}
