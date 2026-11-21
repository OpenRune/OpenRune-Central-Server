package dev.or2.central.worldlink.protocol.clan

data class ClanMemberSnapshot(
    val characterId: Int,
    val displayName: String,
    val rank: Int,
    val joinRuneDay: Int,
    val muted: Boolean,
    val extraInfo: Int,
    val lastSeenRuneDay: Int,
    val combatLevel: Int,
    val skillTotal: Int,
    val worldId: Int,
)

data class ClanBanSnapshot(
    val id: Long,
    val characterId: Int,
    val displayName: String,
    val createdAtMillis: Long,
)

data class ClanApplicationProfile(
    val combatLevel: Int,
    val skillTotal: Int,
    val questPoints: Int,
    val ironStatus: Int,
    val skillLevels: List<Int>,
)

data class ClanApplicationSnapshot(
    val id: Long,
    val characterId: Int,
    val displayName: String,
    val appliedAtMillis: Long,
    val profile: ClanApplicationProfile,
)

data class ClanEventSnapshot(
    val id: Long,
    val position: Int,
    val date: Int,
    val time: Int,
    val durationDays: Int,
    val world: Int,
    val type: Int,
    val subtype: Int,
    val activity: Int,
    val rankPlusOne: Int,
    val creatorCharacterId: Int,
    val creatorDisplayName: String,
)

data class ClanMotifSnapshot(
    val topIcon: Int,
    val bottomIcon: Int,
    val colour: Int,
)

data class ClanRecruitRequirementsSnapshot(
    val minimumCombatLevel: Int,
    val minimumSkillTotal: Int,
    val minimumQuestPoints: Int,
    val ironStatus: Int,
)

data class ClanHallSettingsSnapshot(
    val homeWorldOnly: Boolean,
    val locked: Boolean,
    val playerCount: Int,
)

data class ClanSnapshot(
    val clanId: Long,
    val name: String,
    val ownerCharacterId: Int,
    val ownerDisplayName: String,
    val creationTimeMinutes: Int,
    val renamedTimeMinutes: Int,
    val allowUnaffined: Boolean,
    val talkRank: Int,
    val kickRank: Int,
    val homeWorld: Int,
    val interestsMask: Int,
    val motif: ClanMotifSnapshot,
    val recruitRequirements: ClanRecruitRequirementsSnapshot,
    val hallSettings: ClanHallSettingsSnapshot,
    val boardListed: Boolean,
    val acceptingApplications: Boolean,
    val pendingAbdicationRequesterCharacterId: Int,
    val pendingAbdicationRequestedAt: Long,
    val pendingAbdicationDueAt: Long,
    val members: List<ClanMemberSnapshot>,
    val bans: List<ClanBanSnapshot>,
    val rankTitles: Map<Int, Int>,
    val permissions: Map<Int, Int>,
    val broadcastSettings: Map<Int, Int>,
    val settings: Map<Int, Int>,
    val applications: List<ClanApplicationSnapshot>,
    val events: List<ClanEventSnapshot>,
)

data class ClanBoardEntrySnapshot(
    val clanId: Long,
    val name: String,
    val ownerDisplayName: String,
    val memberCount: Int,
    val creationTimeMinutes: Int,
    val interestsMask: Int,
    val recruitRequirements: ClanRecruitRequirementsSnapshot,
    val homeWorld: Int,
    val acceptingApplications: Boolean,
)

data class ClanBoardSnapshot(val entries: List<ClanBoardEntrySnapshot>)

data class ClanInviteSnapshot(
    val id: Long,
    val clanId: Long,
    val clanName: String,
    val inviterCharacterId: Int,
    val inviterDisplayName: String,
    val expiresAtMillis: Long,
)

data class ClanApplicationVerdict(
    val applicationId: Long,
    val clanId: Long,
    val clanName: String,
    val accepted: Boolean,
    val reviewedAtMillis: Long,
)

data class ClanCharacterSync(
    val snapshot: ClanSnapshot?,
    val applicationDailyCount: Int,
    val creationDailyCount: Int,
    val pendingInvites: List<ClanInviteSnapshot> = emptyList(),
    val applicationVerdicts: List<ClanApplicationVerdict> = emptyList(),
    val guestSnapshot: ClanSnapshot? = null,
)

object ClanGuestSelectionAction {
    const val SELECT: Int = 0
    const val CLEAR: Int = 1
}

object ClanSettingNamespace {
    const val PERMISSION: Int = 0
    const val BROADCAST: Int = 1
    const val GENERIC: Int = 2
}

object ClanProfileField {
    const val HOME_WORLD: Int = 1
    const val TALK_RANK: Int = 2
    const val KICK_RANK: Int = 3
    const val ALLOW_UNAFFINED: Int = 4
    const val INTERESTS_MASK: Int = 5
}

object ClanAbdicationAction {
    const val BEGIN: Int = 1
    const val CANCEL: Int = 2
}

object ClanMembershipClearReason {
    const val LEFT: Int = 1
    const val EXPELLED: Int = 2
    const val DISBANDED: Int = 3
}