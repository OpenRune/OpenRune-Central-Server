package dev.or2.central.worldlink.protocol.pvpa

/** Independent Worldlink contract. Does not reuse the Clan or LiveChat kind/opcodes. */
object PvPAGroupingWire {
    const val LOAD = 1
    const val SIGN_UP = 2
    const val WITHDRAW = 3
    const val LEAVE = 4
    const val RESTORE = 5
    const val GUEST = 0
    const val QUEUED = 1
    const val TOURNAMENT = 0
    const val DUEL = 1
    const val NOT_ALLOWED = 1
    const val NOT_JOINED = 2
    const val CAPACITY = 3
    const val BAD_REQUEST = 4
    const val MAX_MEMBERS = 500
    const val MAX_DISPLAY_NAME_UTF8 = 96
}

data class PvPAGroupingCommand(
    val action: Int,
    val characterId: Int,
    val mode: Int = PvPAGroupingWire.TOURNAMENT,
    val region: Int = 0,
    val displayName: String = "",
)

data class PvPAGroupingResult(
    val action: Int,
    val status: Int,
    val snapshot: PvPAGroupSnapshot?,
    /** Current membership as seen from Central, even on a failed mutation. */
    val currentMember: PvPAGroupMember?,
)

/** Pure translation layer: no RuneScape clan icon/rank assumptions. */
object PvPAGroupingPresentation {
    fun modeCode(mode: PvPAGroupMode): Int = when (mode) {
        PvPAGroupMode.OFFICIAL_TOURNAMENT -> PvPAGroupingWire.TOURNAMENT
        PvPAGroupMode.OFFICIAL_DUEL -> PvPAGroupingWire.DUEL
    }
    fun modeFromCode(code: Int): PvPAGroupMode? = when (code) {
        PvPAGroupingWire.TOURNAMENT -> PvPAGroupMode.OFFICIAL_TOURNAMENT
        PvPAGroupingWire.DUEL -> PvPAGroupMode.OFFICIAL_DUEL
        else -> null
    }
    fun stateCode(state: PvPAQueueState): Int = when (state) {
        PvPAQueueState.GUEST -> PvPAGroupingWire.GUEST
        PvPAQueueState.SIGNED_UP -> PvPAGroupingWire.QUEUED
    }
}
