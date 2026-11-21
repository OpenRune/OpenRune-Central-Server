package dev.or2.central.worldlink.protocol.pvpa

enum class PvPAGroupMode { OFFICIAL_TOURNAMENT, OFFICIAL_DUEL }
enum class PvPAQueueState { GUEST, SIGNED_UP }

data class PvPAGroupKey(val mode: PvPAGroupMode, val preferredRegion: Int) {
    init { require(preferredRegion in 0..2) }
}

data class PvPAGroupMember(
    val characterId: Int,
    val displayName: String,
    val worldId: Int,
    val state: PvPAQueueState,
)

data class PvPAGroupSnapshot(
    val key: PvPAGroupKey,
    val revision: Long,
    val members: List<PvPAGroupMember>,
)

