package dev.or2.central.clan

internal object ClanRules {
    const val MAX_MEMBERS = 500
    const val MAX_BANS = 100
    const val MAX_FOUNDERS = 5

    const val GUEST = -1
    const val MEMBER = 0
    const val ADMINISTRATOR = 100
    const val DEPUTY_OWNER = 125
    const val OWNER = 126
    // Runtime-only effective rank for a verified Jagex Moderator; never assignable/persisted.
    const val JAGEX_MODERATOR = 127
    const val NOBODY = 128

    const val PERMISSION_ACCESS = 12
    const val EDIT_PUBLIC_PROFILE = 13
    const val ASSIGN_MEMBER_RANKS = 14
    const val EXPEL_MEMBERS = 15
    const val EDIT_RANK_TITLES = 16
    const val RECRUITMENT = 17
    const val BAN_FROM_CHAT = 18
    const val DEPOSIT_INTO_COFFER = 19
    const val WITHDRAW_FROM_COFFER = 20
    const val ORGANISE_EVENTS = 21
    const val INITIATE_CLAN_WARS = 22
    const val ENTER_CLAN_HALL = 23
    const val EDIT_CLAN_HALL = 24
    const val ICONS = 25
    const val DEPOSIT_IN_PARTY_CHEST = 94
    const val PULL_PARTY_CHEST_LEVER = 95
    const val PARTY_BALLOON_DROPS = 96

    val ADMIN_PERMISSION_IDS = intArrayOf(
        PERMISSION_ACCESS, EDIT_PUBLIC_PROFILE, ASSIGN_MEMBER_RANKS, EXPEL_MEMBERS,
        EDIT_RANK_TITLES, RECRUITMENT, BAN_FROM_CHAT, DEPOSIT_INTO_COFFER,
        WITHDRAW_FROM_COFFER, ORGANISE_EVENTS, INITIATE_CLAN_WARS, ENTER_CLAN_HALL,
        EDIT_CLAN_HALL, ICONS, DEPOSIT_IN_PARTY_CHEST, PULL_PARTY_CHEST_LEVER,
        PARTY_BALLOON_DROPS,
    )

    val BROADCAST_RANK_IDS = intArrayOf(
        27, 28, 29, 30, 32, 58, 59, 60, 61, 62, 63, 64, 97, 100, 105, 106, 107, 108, 109,
    )

    val DEFAULT_RANK_TITLES = linkedMapOf(
        0 to 62, 10 to 63, 20 to 64, 30 to 65, 40 to 66,
        50 to 67, 60 to 68, 70 to 69, 80 to 70, 90 to 71,
    )

    val ASSIGNABLE_RANKS = setOf(
        0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99,
        100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125,
    )

    val EDITABLE_TITLE_RANKS = setOf(
        0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99,
        101, 102, 103, 104, 105, 110, 115, 120, 124,
    )
}