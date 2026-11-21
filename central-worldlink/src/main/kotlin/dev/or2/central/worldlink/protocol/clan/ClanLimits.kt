package dev.or2.central.worldlink.protocol.clan

object ClanLimits {
    const val NAME_MAX_UTF8: Int = 96
    const val MAX_MEMBERS: Int = 500
    const val MAX_BANS: Int = 100
    const val MAX_FOUNDERS: Int = 5
    const val MAX_RANK_TITLES: Int = 24
    const val MAX_SETTINGS: Int = 256
    const val MAX_APPLICATIONS: Int = 20
    const val MAX_EVENTS: Int = 100
    const val MAX_BOARD_RESULTS: Int = 200
    const val MAX_PENDING_INVITES: Int = 32
    const val MAX_PENDING_VERDICTS: Int = 32
    const val APPLICATION_SKILL_COUNT: Int = 24
    const val MAX_CLAN_FRAME_BODY: Int = 256 * 1024
}
