package dev.or2.central.worldlink.protocol.clan

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.FrameWriter
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame

data class ClanCharacterPayload(val characterId: Int)
data class ClanSyncPayload(val characterId: Int, val updateLastSeen: Boolean)
data class ClanCreatePayload(val characterId: Int, val name: String, val foundingCharacterIds: List<Int>)
data class ClanMemberActionPayload(val characterId: Int, val targetCharacterId: Int)
data class ClanMemberRankPayload(val characterId: Int, val targetCharacterId: Int, val rank: Int)
data class ClanMemberMutedPayload(val characterId: Int, val targetCharacterId: Int, val muted: Boolean)
data class ClanMemberStatsPayload(val characterId: Int, val combatLevel: Int, val skillTotal: Int)
data class ClanGuestSelectionPayload(val characterId: Int, val action: Int, val clanId: Long)
data class ClanGuestKickPayload(val characterId: Int, val clanId: Long, val targetName: String)
data class ClanBanAddPayload(val characterId: Int, val targetCharacterId: Int, val targetDisplayName: String)
data class ClanBanRemovePayload(val characterId: Int, val banId: Long)
data class ClanProfilePayload(val characterId: Int, val field: Int, val value: Int)
data class ClanMotifPayload(val characterId: Int, val topIcon: Int, val bottomIcon: Int, val colour: Int)
data class ClanRecruitRequirementsPayload(
    val characterId: Int,
    val minimumCombatLevel: Int,
    val minimumSkillTotal: Int,
    val minimumQuestPoints: Int,
    val ironStatus: Int,
)
data class ClanHallSettingsPayload(val characterId: Int, val homeWorldOnly: Boolean, val locked: Boolean, val playerCount: Int)
data class ClanSettingPayload(val characterId: Int, val namespace: Int, val settingId: Int, val value: Int)
data class ClanRankTitlesPayload(val characterId: Int, val values: Map<Int, Int>)
data class ClanBoardStatePayload(val characterId: Int, val listed: Boolean, val acceptingApplications: Boolean)
data class ClanApplicationSubmitPayload(val characterId: Int, val clanId: Long, val profile: ClanApplicationProfile)
data class ClanApplicationReviewPayload(val characterId: Int, val applicationId: Long, val accept: Boolean)
data class ClanEventUpsertPayload(
    val characterId: Int,
    val eventId: Long,
    val position: Int,
    val date: Int,
    val time: Int,
    val durationDays: Int,
    val world: Int,
    val type: Int,
    val subtype: Int,
    val activity: Int,
    val rankPlusOne: Int,
)
data class ClanEventDeletePayload(val characterId: Int, val eventId: Long)
data class ClanAbdicationPayload(val characterId: Int, val action: Int)
data class ClanDetailPayload(val characterId: Int, val clanId: Long)
data class ClanRenamePayload(val characterId: Int, val name: String)
data class ClanInviteCreatePayload(val characterId: Int, val targetCharacterId: Int, val expiresAtMillis: Long)
data class ClanInviteAcceptPayload(val characterId: Int, val inviteId: Long)
data class ClanApplicationWithdrawPayload(val characterId: Int, val clanId: Long)
data class ClanApplicationVerdictAckPayload(val characterId: Int, val applicationIds: List<Long>)
/**
 * Envelope used only when a Jagex admin manages another clan. The nested frame is one
 * of the normal clan mutation frames, preserving the existing mutation contract.
 */
data class ClanAdminMutationPayload(val characterId: Int, val clanId: Long, val mutationFrame: ByteArray)
data class ClanMutationReply(val action: Int, val clanId: Long)
data class ClanMutationFail(val action: Int, val reason: Int)
data class ClanMembershipClearedPush(val characterId: Int, val clanId: Long, val reason: Int)
data class ClanInvitePush(val inviteeCharacterId: Int, val invite: ClanInviteSnapshot)
data class ClanApplicationResultPush(val applicantCharacterId: Int, val verdict: ClanApplicationVerdict)

object ClanPackets {
    fun encodeSync(characterId: Int, updateLastSeen: Boolean = false): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SYNC) {
            writeInt(characterId)
            writeByte(if (updateLastSeen) 1 else 0)
        }
    fun encodeBoardSync(characterId: Int): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_BOARD_SYNC) { writeInt(characterId) }
    fun encodeDetail(characterId: Int, clanId: Long): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_DETAIL) { writeInt(characterId); writeLong(clanId) }
    fun encodeLeave(characterId: Int): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_LEAVE) { writeInt(characterId) }
    fun encodeMemberStats(payload: ClanMemberStatsPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_MEMBER_STATS) {
        writeInt(payload.characterId); writeByte(payload.combatLevel); writeShort(payload.skillTotal)
    }
    fun encodeGuestSelection(payload: ClanGuestSelectionPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_GUEST_SELECTION) {
        writeInt(payload.characterId); writeByte(payload.action); writeLong(payload.clanId)
    }
    fun encodeGuestKick(payload: ClanGuestKickPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_GUEST_KICK) {
        writeInt(payload.characterId); writeLong(payload.clanId); writeUtf8Clan(payload.targetName)
    }

    fun encodeCreate(payload: ClanCreatePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_CREATE) {
        writeInt(payload.characterId)
        writeUtf8Clan(payload.name)
        val founders = payload.foundingCharacterIds.distinct().take(ClanLimits.MAX_FOUNDERS)
        writeByte(founders.size)
        founders.forEach { writeInt(it) }
    }

    fun encodeMemberAction(opcode: Int, payload: ClanMemberActionPayload): ByteArray = outboundFrame(opcode) {
        writeInt(payload.characterId); writeInt(payload.targetCharacterId)
    }

    fun encodeMemberRank(payload: ClanMemberRankPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_RANK) {
        writeInt(payload.characterId); writeInt(payload.targetCharacterId); writeByte(payload.rank)
    }

    fun encodeMemberMuted(payload: ClanMemberMutedPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_MUTED) {
        writeInt(payload.characterId); writeInt(payload.targetCharacterId); writeByte(if (payload.muted) 1 else 0)
    }

    fun encodeBanAdd(payload: ClanBanAddPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_BAN_ADD) {
        writeInt(payload.characterId); writeInt(payload.targetCharacterId); writeUtf8Clan(payload.targetDisplayName)
    }

    fun encodeBanRemove(payload: ClanBanRemovePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE) {
        writeInt(payload.characterId); writeLong(payload.banId)
    }

    fun encodeProfile(payload: ClanProfilePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_PROFILE) {
        writeInt(payload.characterId); writeByte(payload.field); writeInt(payload.value)
    }

    fun encodeMotif(payload: ClanMotifPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_MOTIF) {
        writeInt(payload.characterId); writeByte(payload.topIcon); writeByte(payload.bottomIcon); writeByte(payload.colour)
    }

    fun encodeRecruitRequirements(payload: ClanRecruitRequirementsPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS) {
        writeInt(payload.characterId); writeByte(payload.minimumCombatLevel); writeShort(payload.minimumSkillTotal)
        writeShort(payload.minimumQuestPoints); writeByte(payload.ironStatus)
    }

    fun encodeHallSettings(payload: ClanHallSettingsPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_HALL_SETTINGS) {
        writeInt(payload.characterId); writeByte(if (payload.homeWorldOnly) 1 else 0); writeByte(if (payload.locked) 1 else 0); writeShort(payload.playerCount)
    }

    fun encodeSetting(payload: ClanSettingPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_SETTING) {
        writeInt(payload.characterId); writeByte(payload.namespace); writeShort(payload.settingId); writeInt(payload.value)
    }

    fun encodeRankTitles(payload: ClanRankTitlesPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_RANK_TITLES) {
        writeInt(payload.characterId)
        val values = payload.values.entries.take(ClanLimits.MAX_RANK_TITLES)
        writeByte(values.size)
        values.forEach { (rank, titleId) -> writeByte(rank); writeShort(titleId) }
    }

    fun encodeBoardState(payload: ClanBoardStatePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SET_BOARD_STATE) {
        writeInt(payload.characterId); writeByte(if (payload.listed) 1 else 0); writeByte(if (payload.acceptingApplications) 1 else 0)
    }

    fun encodeApplicationSubmit(payload: ClanApplicationSubmitPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_SUBMIT) {
        writeInt(payload.characterId); writeLong(payload.clanId); writeApplicationProfile(payload.profile)
    }

    fun encodeApplicationReview(payload: ClanApplicationReviewPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW) {
        writeInt(payload.characterId); writeLong(payload.applicationId); writeByte(if (payload.accept) 1 else 0)
    }

    fun encodeEventUpsert(payload: ClanEventUpsertPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_EVENT_UPSERT) {
        writeInt(payload.characterId); writeLong(payload.eventId); writeInt(payload.position); writeInt(payload.date); writeInt(payload.time)
        writeInt(payload.durationDays); writeInt(payload.world); writeInt(payload.type); writeInt(payload.subtype); writeInt(payload.activity); writeInt(payload.rankPlusOne)
    }

    fun encodeEventDelete(payload: ClanEventDeletePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_EVENT_DELETE) {
        writeInt(payload.characterId); writeLong(payload.eventId)
    }

    fun encodeAbdication(payload: ClanAbdicationPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_ABDICATION) {
        writeInt(payload.characterId); writeByte(payload.action)
    }

    fun encodeRename(payload: ClanRenamePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_RENAME) {
        writeInt(payload.characterId); writeUtf8Clan(payload.name)
    }

    fun encodeInviteCreate(payload: ClanInviteCreatePayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_INVITE_CREATE) {
        writeInt(payload.characterId); writeInt(payload.targetCharacterId); writeLong(payload.expiresAtMillis)
    }

    fun encodeApplicationWithdraw(payload: ClanApplicationWithdrawPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_WITHDRAW) {
        writeInt(payload.characterId); writeLong(payload.clanId)
    }

    fun encodeInviteAccept(payload: ClanInviteAcceptPayload): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_INVITE_ACCEPT) {
        writeInt(payload.characterId); writeLong(payload.inviteId)
    }

    fun encodeApplicationVerdictAck(payload: ClanApplicationVerdictAckPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_VERDICT_ACK) {
            writeInt(payload.characterId)
            val ids = payload.applicationIds.distinct().take(ClanLimits.MAX_PENDING_VERDICTS)
            writeShort(ids.size)
            ids.forEach { writeLong(it) }
        }

    fun encodeAdminMutation(payload: ClanAdminMutationPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION) {
            require(payload.mutationFrame.isNotEmpty())
            require(payload.mutationFrame.size <= 0xffff)
            writeInt(payload.characterId)
            writeLong(payload.clanId)
            writeShort(payload.mutationFrame.size)
            writeBytes(payload.mutationFrame)
        }

    fun encodeOk(payload: ClanMutationReply): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_OK) { writeByte(payload.action); writeLong(payload.clanId) }
    fun encodeFail(payload: ClanMutationFail): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_FAIL) { writeByte(payload.action); writeByte(payload.reason) }
    fun encodeSyncFail(reason: Int): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SYNC_FAIL) { writeByte(reason) }

    fun encodeSyncOk(sync: ClanCharacterSync): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SYNC_OK) {
        writeShort(sync.applicationDailyCount.coerceIn(0, 65535))
        writeShort(sync.creationDailyCount.coerceIn(0, 65535))
        writeInvites(sync.pendingInvites)
        writeVerdicts(sync.applicationVerdicts)
        writeByte(if (sync.snapshot == null) 0 else 1)
        sync.snapshot?.let { writeSnapshot(it) }
        writeByte(if (sync.guestSnapshot == null) 0 else 1)
        sync.guestSnapshot?.let { writeSnapshot(it) }
    }

    fun encodeDetailOk(snapshot: ClanSnapshot?): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_DETAIL_OK) {
        writeByte(if (snapshot == null) 0 else 1)
        snapshot?.let { writeSnapshot(it) }
    }

    fun encodeServerSnapshot(snapshot: ClanSnapshot): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_SNAPSHOT) { writeSnapshot(snapshot) }
    fun encodeServerDisbanded(clanId: Long): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_DISBANDED) { writeLong(clanId) }
    fun encodeServerApplicationWithdrawn(clanId: Long): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_APPLICATION_WITHDRAWN) { writeLong(clanId) }
    fun encodeMembershipCleared(push: ClanMembershipClearedPush): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_MEMBERSHIP_CLEARED) {
        writeInt(push.characterId); writeLong(push.clanId); writeByte(push.reason)
    }
    fun encodeInvitePush(push: ClanInvitePush): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_INVITE) {
        writeInt(push.inviteeCharacterId); writeInvite(push.invite)
    }
    fun encodeApplicationResultPush(push: ClanApplicationResultPush): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_APPLICATION_RESULT) {
        writeInt(push.applicantCharacterId); writeVerdict(push.verdict)
    }
    fun encodeBoardOk(snapshot: ClanBoardSnapshot): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_BOARD_SYNC_OK) { writeBoard(snapshot) }

    fun decodeCharacter(input: FrameReader): ClanCharacterPayload = ClanCharacterPayload(input.readInt()).also { input.requireFullyConsumed() }
    fun decodeSync(input: FrameReader): ClanSyncPayload {
        val characterId = input.readInt()
        val value = input.readUnsignedByte()
        require(value in 0..1) { "Invalid last-seen update flag" }
        input.requireFullyConsumed()
        return ClanSyncPayload(characterId, value == 1)
    }
    fun decodeDetail(input: FrameReader): ClanDetailPayload = ClanDetailPayload(input.readInt(), input.readLong()).also { input.requireFullyConsumed() }
    fun decodeMemberStats(input: FrameReader): ClanMemberStatsPayload =
        ClanMemberStatsPayload(input.readInt(), input.readUnsignedByte(), input.readUnsignedShort()).also { input.requireFullyConsumed() }
    fun decodeGuestSelection(input: FrameReader): ClanGuestSelectionPayload =
        ClanGuestSelectionPayload(input.readInt(), input.readUnsignedByte(), input.readLong()).also { input.requireFullyConsumed() }
    fun decodeGuestKick(input: FrameReader): ClanGuestKickPayload =
        ClanGuestKickPayload(input.readInt(), input.readLong(), input.readUtf8LenPrefixed()).also { input.requireFullyConsumed() }
    fun decodeAdminMutation(input: FrameReader): ClanAdminMutationPayload {
        val characterId = input.readInt()
        val clanId = input.readLong()
        val length = input.readUnsignedShort()
        require(length in 1..ClanLimits.MAX_CLAN_FRAME_BODY)
        val frame = input.readFully(length)
        input.requireFullyConsumed()
        return ClanAdminMutationPayload(characterId, clanId, frame)
    }

    fun decodeMemberAction(input: FrameReader): ClanMemberActionPayload = ClanMemberActionPayload(input.readInt(), input.readInt()).also { input.requireFullyConsumed() }
    fun decodeMemberRank(input: FrameReader): ClanMemberRankPayload = ClanMemberRankPayload(input.readInt(), input.readInt(), input.readUnsignedByte()).also { input.requireFullyConsumed() }
    fun decodeMemberMuted(input: FrameReader): ClanMemberMutedPayload = ClanMemberMutedPayload(input.readInt(), input.readInt(), input.readUnsignedByte() != 0).also { input.requireFullyConsumed() }
    fun decodeBanRemove(input: FrameReader): ClanBanRemovePayload = ClanBanRemovePayload(input.readInt(), input.readLong()).also { input.requireFullyConsumed() }
    fun decodeProfile(input: FrameReader): ClanProfilePayload = ClanProfilePayload(input.readInt(), input.readUnsignedByte(), input.readInt()).also { input.requireFullyConsumed() }
    fun decodeMotif(input: FrameReader): ClanMotifPayload = ClanMotifPayload(input.readInt(), input.readUnsignedByte(), input.readUnsignedByte(), input.readUnsignedByte()).also { input.requireFullyConsumed() }
    fun decodeRecruitRequirements(input: FrameReader): ClanRecruitRequirementsPayload = ClanRecruitRequirementsPayload(
        input.readInt(), input.readUnsignedByte(), input.readUnsignedShort(), input.readUnsignedShort(), input.readUnsignedByte(),
    ).also { input.requireFullyConsumed() }
    fun decodeHallSettings(input: FrameReader): ClanHallSettingsPayload = ClanHallSettingsPayload(
        input.readInt(), input.readUnsignedByte() != 0, input.readUnsignedByte() != 0, input.readUnsignedShort(),
    ).also { input.requireFullyConsumed() }
    fun decodeSetting(input: FrameReader): ClanSettingPayload = ClanSettingPayload(input.readInt(), input.readUnsignedByte(), input.readUnsignedShort(), input.readInt()).also { input.requireFullyConsumed() }
    fun decodeBoardState(input: FrameReader): ClanBoardStatePayload = ClanBoardStatePayload(input.readInt(), input.readUnsignedByte() != 0, input.readUnsignedByte() != 0).also { input.requireFullyConsumed() }
    fun decodeApplicationReview(input: FrameReader): ClanApplicationReviewPayload = ClanApplicationReviewPayload(input.readInt(), input.readLong(), input.readUnsignedByte() != 0).also { input.requireFullyConsumed() }
    fun decodeEventDelete(input: FrameReader): ClanEventDeletePayload = ClanEventDeletePayload(input.readInt(), input.readLong()).also { input.requireFullyConsumed() }
    fun decodeAbdication(input: FrameReader): ClanAbdicationPayload = ClanAbdicationPayload(input.readInt(), input.readUnsignedByte()).also { input.requireFullyConsumed() }
    fun decodeInviteAccept(input: FrameReader): ClanInviteAcceptPayload = ClanInviteAcceptPayload(input.readInt(), input.readLong()).also { input.requireFullyConsumed() }
    fun decodeApplicationWithdraw(input: FrameReader): ClanApplicationWithdrawPayload = ClanApplicationWithdrawPayload(input.readInt(), input.readLong()).also { input.requireFullyConsumed() }

    fun decodeApplicationVerdictAck(input: FrameReader): ClanApplicationVerdictAckPayload {
        val characterId = input.readInt()
        val count = input.readUnsignedShort()
        require(count in 1..ClanLimits.MAX_PENDING_VERDICTS)
        val ids = List(count) { input.readLong() }
        input.requireFullyConsumed()
        return ClanApplicationVerdictAckPayload(characterId, ids)
    }

    fun decodeCreate(input: FrameReader): ClanCreatePayload {
        val characterId = input.readInt()
        val name = input.readUtf8LenPrefixed().trim()
        val count = input.readUnsignedByte()
        require(count <= ClanLimits.MAX_FOUNDERS) { "too many clan founders" }
        val ids = List(count) { input.readInt() }
        input.requireFullyConsumed()
        return ClanCreatePayload(characterId, name, ids)
    }

    fun decodeBanAdd(input: FrameReader): ClanBanAddPayload =
        ClanBanAddPayload(input.readInt(), input.readInt(), input.readUtf8LenPrefixed().trim()).also { input.requireFullyConsumed() }

    fun decodeRankTitles(input: FrameReader): ClanRankTitlesPayload {
        val characterId = input.readInt()
        val count = input.readUnsignedByte()
        require(count <= ClanLimits.MAX_RANK_TITLES) { "too many rank titles" }
        val values = linkedMapOf<Int, Int>()
        repeat(count) { values[input.readUnsignedByte()] = input.readUnsignedShort() }
        input.requireFullyConsumed()
        return ClanRankTitlesPayload(characterId, values)
    }

    fun decodeApplicationSubmit(input: FrameReader): ClanApplicationSubmitPayload {
        val payload = ClanApplicationSubmitPayload(input.readInt(), input.readLong(), readApplicationProfile(input))
        input.requireFullyConsumed()
        return payload
    }

    fun decodeEventUpsert(input: FrameReader): ClanEventUpsertPayload {
        val value = ClanEventUpsertPayload(
            input.readInt(), input.readLong(), input.readInt(), input.readInt(), input.readInt(), input.readInt(),
            input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(),
        )
        input.requireFullyConsumed()
        return value
    }

    fun decodeRename(input: FrameReader): ClanRenamePayload =
        ClanRenamePayload(input.readInt(), input.readUtf8LenPrefixed().trim()).also { input.requireFullyConsumed() }

    fun decodeInviteCreate(input: FrameReader): ClanInviteCreatePayload =
        ClanInviteCreatePayload(input.readInt(), input.readInt(), input.readLong()).also { input.requireFullyConsumed() }

    fun decodeSyncOk(input: FrameReader): ClanCharacterSync {
        val applicationDailyCount = input.readUnsignedShort()
        val creationDailyCount = input.readUnsignedShort()
        val invites = readInvites(input)
        val verdicts = readVerdicts(input)
        val snapshot = if (input.readUnsignedByte() != 0) readSnapshot(input) else null
        val guestSnapshot = if (input.readUnsignedByte() != 0) readSnapshot(input) else null
        input.requireFullyConsumed()
        return ClanCharacterSync(snapshot, applicationDailyCount, creationDailyCount, invites, verdicts, guestSnapshot)
    }

    fun decodeDetailOk(input: FrameReader): ClanSnapshot? {
        val snapshot = if (input.readUnsignedByte() != 0) readSnapshot(input) else null
        input.requireFullyConsumed()
        return snapshot
    }

    fun decodeServerSnapshot(input: FrameReader): ClanSnapshot = readSnapshot(input).also { input.requireFullyConsumed() }
    fun decodeBoardOk(input: FrameReader): ClanBoardSnapshot = readBoard(input).also { input.requireFullyConsumed() }
    fun decodeMutationReply(input: FrameReader): ClanMutationReply = ClanMutationReply(input.readUnsignedByte(), input.readLong()).also { input.requireFullyConsumed() }
    fun decodeMutationFail(input: FrameReader): ClanMutationFail = ClanMutationFail(input.readUnsignedByte(), input.readUnsignedByte()).also { input.requireFullyConsumed() }
    fun decodeSyncFail(input: FrameReader): Int = input.readUnsignedByte().also { input.requireFullyConsumed() }
    fun decodeMembershipCleared(input: FrameReader): ClanMembershipClearedPush = ClanMembershipClearedPush(input.readInt(), input.readLong(), input.readUnsignedByte()).also { input.requireFullyConsumed() }
    fun decodeServerDisbanded(input: FrameReader): Long = input.readLong().also { input.requireFullyConsumed() }
    fun decodeServerApplicationWithdrawn(input: FrameReader): Long = input.readLong().also { input.requireFullyConsumed() }
    fun decodeInvitePush(input: FrameReader): ClanInvitePush = ClanInvitePush(input.readInt(), readInvite(input)).also { input.requireFullyConsumed() }
    fun decodeApplicationResultPush(input: FrameReader): ClanApplicationResultPush = ClanApplicationResultPush(input.readInt(), readVerdict(input)).also { input.requireFullyConsumed() }

    private fun FrameWriter.writeSnapshot(s: ClanSnapshot) {
        writeLong(s.clanId); writeUtf8Clan(s.name); writeInt(s.ownerCharacterId); writeUtf8Clan(s.ownerDisplayName)
        writeInt(s.creationTimeMinutes); writeInt(s.renamedTimeMinutes); writeByte(if (s.allowUnaffined) 1 else 0)
        writeByte(s.talkRank); writeByte(s.kickRank); writeShort(s.homeWorld); writeInt(s.interestsMask)
        writeByte(s.motif.topIcon); writeByte(s.motif.bottomIcon); writeByte(s.motif.colour)
        writeByte(s.recruitRequirements.minimumCombatLevel); writeShort(s.recruitRequirements.minimumSkillTotal)
        writeShort(s.recruitRequirements.minimumQuestPoints); writeByte(s.recruitRequirements.ironStatus)
        writeByte(if (s.hallSettings.homeWorldOnly) 1 else 0); writeByte(if (s.hallSettings.locked) 1 else 0); writeShort(s.hallSettings.playerCount)
        writeByte(if (s.boardListed) 1 else 0); writeByte(if (s.acceptingApplications) 1 else 0)
        writeInt(s.pendingAbdicationRequesterCharacterId); writeLong(s.pendingAbdicationRequestedAt); writeLong(s.pendingAbdicationDueAt)

        require(s.members.size <= ClanLimits.MAX_MEMBERS)
        writeShort(s.members.size)
        s.members.forEach { m ->
            writeInt(m.characterId); writeUtf8Clan(m.displayName); writeByte(m.rank); writeInt(m.joinRuneDay); writeByte(if (m.muted) 1 else 0)
            writeInt(m.extraInfo); writeInt(m.lastSeenRuneDay); writeByte(m.combatLevel); writeShort(m.skillTotal); writeInt(m.worldId)
        }
        require(s.bans.size <= ClanLimits.MAX_BANS)
        writeShort(s.bans.size)
        s.bans.forEach { b -> writeLong(b.id); writeInt(b.characterId); writeUtf8Clan(b.displayName); writeLong(b.createdAtMillis) }
        writeIntMapByteKey(s.rankTitles)
        writeIntMapShortKey(s.permissions)
        writeIntMapShortKey(s.broadcastSettings)
        writeIntMapShortKey(s.settings)
        require(s.applications.size <= ClanLimits.MAX_APPLICATIONS)
        writeShort(s.applications.size)
        s.applications.forEach { a ->
            writeLong(a.id); writeInt(a.characterId); writeUtf8Clan(a.displayName); writeLong(a.appliedAtMillis); writeApplicationProfile(a.profile)
        }
        require(s.events.size <= ClanLimits.MAX_EVENTS)
        writeShort(s.events.size)
        s.events.forEach { e ->
            writeLong(e.id); writeInt(e.position); writeInt(e.date); writeInt(e.time); writeInt(e.durationDays); writeInt(e.world)
            writeInt(e.type); writeInt(e.subtype); writeInt(e.activity); writeInt(e.rankPlusOne); writeInt(e.creatorCharacterId); writeUtf8Clan(e.creatorDisplayName)
        }
    }

    private fun readSnapshot(input: FrameReader): ClanSnapshot {
        val clanId = input.readLong(); val name = input.readUtf8LenPrefixed(); val ownerCharacterId = input.readInt(); val ownerDisplayName = input.readUtf8LenPrefixed()
        val creationTimeMinutes = input.readInt(); val renamedTimeMinutes = input.readInt(); val allowUnaffined = input.readUnsignedByte() != 0
        val talkRank = input.readSignedByte(); val kickRank = input.readUnsignedByte(); val homeWorld = input.readUnsignedShort(); val interestsMask = input.readInt()
        val motif = ClanMotifSnapshot(input.readUnsignedByte(), input.readUnsignedByte(), input.readUnsignedByte())
        val recruit = ClanRecruitRequirementsSnapshot(input.readUnsignedByte(), input.readUnsignedShort(), input.readUnsignedShort(), input.readUnsignedByte())
        val hall = ClanHallSettingsSnapshot(input.readUnsignedByte() != 0, input.readUnsignedByte() != 0, input.readUnsignedShort())
        val boardListed = input.readUnsignedByte() != 0; val acceptingApplications = input.readUnsignedByte() != 0
        val pendingRequester = input.readInt(); val requestedAt = input.readLong(); val dueAt = input.readLong()
        val memberCount = input.readUnsignedShort(); require(memberCount <= ClanLimits.MAX_MEMBERS)
        val members = List(memberCount) {
            ClanMemberSnapshot(
                input.readInt(), input.readUtf8LenPrefixed(), input.readUnsignedByte(), input.readInt(),
                input.readUnsignedByte() != 0, input.readInt(), input.readInt(), input.readUnsignedByte(),
                input.readUnsignedShort(), input.readInt(),
            )
        }
        val banCount = input.readUnsignedShort(); require(banCount <= ClanLimits.MAX_BANS)
        val bans = List(banCount) { ClanBanSnapshot(input.readLong(), input.readInt(), input.readUtf8LenPrefixed(), input.readLong()) }
        val titles = readIntMapByteKey(input, ClanLimits.MAX_RANK_TITLES)
        val permissions = readIntMapShortKey(input, ClanLimits.MAX_SETTINGS)
        val broadcasts = readIntMapShortKey(input, ClanLimits.MAX_SETTINGS)
        val settings = readIntMapShortKey(input, ClanLimits.MAX_SETTINGS)
        val applicationCount = input.readUnsignedShort(); require(applicationCount <= ClanLimits.MAX_APPLICATIONS)
        val applications = List(applicationCount) {
            ClanApplicationSnapshot(input.readLong(), input.readInt(), input.readUtf8LenPrefixed(), input.readLong(), readApplicationProfile(input))
        }
        val eventCount = input.readUnsignedShort(); require(eventCount <= ClanLimits.MAX_EVENTS)
        val events = List(eventCount) {
            ClanEventSnapshot(input.readLong(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readUtf8LenPrefixed())
        }
        return ClanSnapshot(
            clanId, name, ownerCharacterId, ownerDisplayName, creationTimeMinutes, renamedTimeMinutes, allowUnaffined,
            talkRank, kickRank, homeWorld, interestsMask, motif, recruit, hall, boardListed, acceptingApplications,
            pendingRequester, requestedAt, dueAt, members, bans, titles, permissions, broadcasts, settings, applications, events,
        )
    }

    private fun FrameWriter.writeApplicationProfile(profile: ClanApplicationProfile) {
        writeShort(profile.combatLevel.coerceIn(0, 65535)); writeShort(profile.skillTotal.coerceIn(0, 65535))
        writeShort(profile.questPoints.coerceIn(0, 65535)); writeByte(profile.ironStatus.coerceIn(0, 255))
        val skills = profile.skillLevels.take(ClanLimits.APPLICATION_SKILL_COUNT)
        writeByte(skills.size)
        skills.forEach { writeByte(it.coerceIn(0, 255)) }
    }

    private fun readApplicationProfile(input: FrameReader): ClanApplicationProfile {
        val combat = input.readUnsignedShort(); val total = input.readUnsignedShort(); val qp = input.readUnsignedShort(); val iron = input.readUnsignedByte()
        val count = input.readUnsignedByte(); require(count <= ClanLimits.APPLICATION_SKILL_COUNT)
        return ClanApplicationProfile(combat, total, qp, iron, List(count) { input.readUnsignedByte() })
    }

    private fun FrameWriter.writeInvite(invite: ClanInviteSnapshot) {
        writeLong(invite.id); writeLong(invite.clanId); writeUtf8Clan(invite.clanName); writeInt(invite.inviterCharacterId)
        writeUtf8Clan(invite.inviterDisplayName); writeLong(invite.expiresAtMillis)
    }

    private fun FrameWriter.writeInvites(invites: List<ClanInviteSnapshot>) {
        val values = invites.take(ClanLimits.MAX_PENDING_INVITES)
        writeShort(values.size); values.forEach { writeInvite(it) }
    }

    private fun readInvite(input: FrameReader): ClanInviteSnapshot = ClanInviteSnapshot(
        id = input.readLong(), clanId = input.readLong(), clanName = input.readUtf8LenPrefixed(), inviterCharacterId = input.readInt(),
        inviterDisplayName = input.readUtf8LenPrefixed(), expiresAtMillis = input.readLong(),
    )

    private fun readInvites(input: FrameReader): List<ClanInviteSnapshot> {
        val count = input.readUnsignedShort(); require(count <= ClanLimits.MAX_PENDING_INVITES)
        return List(count) { readInvite(input) }
    }

    private fun FrameWriter.writeVerdict(verdict: ClanApplicationVerdict) {
        writeLong(verdict.applicationId); writeLong(verdict.clanId); writeUtf8Clan(verdict.clanName)
        writeByte(if (verdict.accepted) 1 else 0); writeLong(verdict.reviewedAtMillis)
    }

    private fun FrameWriter.writeVerdicts(verdicts: List<ClanApplicationVerdict>) {
        val values = verdicts.take(ClanLimits.MAX_PENDING_VERDICTS)
        writeShort(values.size); values.forEach { writeVerdict(it) }
    }

    private fun readVerdict(input: FrameReader): ClanApplicationVerdict = ClanApplicationVerdict(
        applicationId = input.readLong(), clanId = input.readLong(), clanName = input.readUtf8LenPrefixed(),
        accepted = input.readUnsignedByte() != 0, reviewedAtMillis = input.readLong(),
    )

    private fun readVerdicts(input: FrameReader): List<ClanApplicationVerdict> {
        val count = input.readUnsignedShort(); require(count <= ClanLimits.MAX_PENDING_VERDICTS)
        return List(count) { readVerdict(input) }
    }

    private fun FrameWriter.writeBoard(snapshot: ClanBoardSnapshot) {
        require(snapshot.entries.size <= ClanLimits.MAX_BOARD_RESULTS)
        writeShort(snapshot.entries.size)
        snapshot.entries.forEach { e ->
            writeLong(e.clanId); writeUtf8Clan(e.name); writeUtf8Clan(e.ownerDisplayName); writeShort(e.memberCount)
            writeInt(e.creationTimeMinutes); writeInt(e.interestsMask); writeByte(e.recruitRequirements.minimumCombatLevel)
            writeShort(e.recruitRequirements.minimumSkillTotal); writeShort(e.recruitRequirements.minimumQuestPoints)
            writeByte(e.recruitRequirements.ironStatus); writeShort(e.homeWorld); writeByte(if (e.acceptingApplications) 1 else 0)
        }
    }

    private fun readBoard(input: FrameReader): ClanBoardSnapshot {
        val count = input.readUnsignedShort(); require(count <= ClanLimits.MAX_BOARD_RESULTS)
        return ClanBoardSnapshot(List(count) {
            ClanBoardEntrySnapshot(
                input.readLong(), input.readUtf8LenPrefixed(), input.readUtf8LenPrefixed(), input.readUnsignedShort(), input.readInt(), input.readInt(),
                ClanRecruitRequirementsSnapshot(input.readUnsignedByte(), input.readUnsignedShort(), input.readUnsignedShort(), input.readUnsignedByte()),
                input.readUnsignedShort(), input.readUnsignedByte() != 0,
            )
        })
    }

    private fun FrameWriter.writeIntMapByteKey(values: Map<Int, Int>) {
        require(values.size <= ClanLimits.MAX_RANK_TITLES)
        writeByte(values.size); values.forEach { (k, v) -> writeByte(k); writeShort(v) }
    }

    private fun FrameWriter.writeIntMapShortKey(values: Map<Int, Int>) {
        require(values.size <= ClanLimits.MAX_SETTINGS)
        writeShort(values.size); values.forEach { (k, v) -> writeShort(k); writeInt(v) }
    }

    private fun readIntMapByteKey(input: FrameReader, max: Int): Map<Int, Int> {
        val count = input.readUnsignedByte(); require(count <= max)
        return linkedMapOf<Int, Int>().apply { repeat(count) { this[input.readUnsignedByte()] = input.readUnsignedShort() } }
    }

    private fun readIntMapShortKey(input: FrameReader, max: Int): Map<Int, Int> {
        val count = input.readUnsignedShort(); require(count <= max)
        return linkedMapOf<Int, Int>().apply { repeat(count) { this[input.readUnsignedShort()] = input.readInt() } }
    }

    private fun FrameWriter.writeUtf8Clan(value: String) = writeUtf8Truncated(value, ClanLimits.NAME_MAX_UTF8)
}

object GameToCentralClanPackets {
    fun sync(characterId: Int, updateLastSeen: Boolean = false) = ClanPackets.encodeSync(characterId, updateLastSeen)
    fun boardSync(characterId: Int) = ClanPackets.encodeBoardSync(characterId)
    fun detail(characterId: Int, clanId: Long) = ClanPackets.encodeDetail(characterId, clanId)
    fun create(payload: ClanCreatePayload) = ClanPackets.encodeCreate(payload)
    fun leave(characterId: Int) = ClanPackets.encodeLeave(characterId)
    fun addMember(characterId: Int, targetCharacterId: Int) = ClanPackets.encodeMemberAction(WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER, ClanMemberActionPayload(characterId, targetCharacterId))
    fun removeMember(characterId: Int, targetCharacterId: Int) = ClanPackets.encodeMemberAction(WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER, ClanMemberActionPayload(characterId, targetCharacterId))
    fun setRank(payload: ClanMemberRankPayload) = ClanPackets.encodeMemberRank(payload)
    fun setMuted(payload: ClanMemberMutedPayload) = ClanPackets.encodeMemberMuted(payload)
    fun banAdd(payload: ClanBanAddPayload) = ClanPackets.encodeBanAdd(payload)
    fun banRemove(payload: ClanBanRemovePayload) = ClanPackets.encodeBanRemove(payload)
    fun setProfile(payload: ClanProfilePayload) = ClanPackets.encodeProfile(payload)
    fun setMotif(payload: ClanMotifPayload) = ClanPackets.encodeMotif(payload)
    fun setRecruitRequirements(payload: ClanRecruitRequirementsPayload) = ClanPackets.encodeRecruitRequirements(payload)
    fun setHallSettings(payload: ClanHallSettingsPayload) = ClanPackets.encodeHallSettings(payload)
    fun setSetting(payload: ClanSettingPayload) = ClanPackets.encodeSetting(payload)
    fun setRankTitles(payload: ClanRankTitlesPayload) = ClanPackets.encodeRankTitles(payload)
    fun setBoardState(payload: ClanBoardStatePayload) = ClanPackets.encodeBoardState(payload)
    fun submitApplication(payload: ClanApplicationSubmitPayload) = ClanPackets.encodeApplicationSubmit(payload)
    fun reviewApplication(payload: ClanApplicationReviewPayload) = ClanPackets.encodeApplicationReview(payload)
    fun withdrawApplication(payload: ClanApplicationWithdrawPayload) = ClanPackets.encodeApplicationWithdraw(payload)
    fun upsertEvent(payload: ClanEventUpsertPayload) = ClanPackets.encodeEventUpsert(payload)
    fun deleteEvent(payload: ClanEventDeletePayload) = ClanPackets.encodeEventDelete(payload)
    fun abdication(payload: ClanAbdicationPayload) = ClanPackets.encodeAbdication(payload)
    fun rename(payload: ClanRenamePayload) = ClanPackets.encodeRename(payload)
    fun createInvite(payload: ClanInviteCreatePayload) = ClanPackets.encodeInviteCreate(payload)
    fun acceptInvite(payload: ClanInviteAcceptPayload) = ClanPackets.encodeInviteAccept(payload)
    fun acknowledgeApplicationVerdicts(characterId: Int, applicationIds: Collection<Long>) =
        ClanPackets.encodeApplicationVerdictAck(ClanApplicationVerdictAckPayload(characterId, applicationIds.toList()))
    fun memberStats(characterId: Int, combatLevel: Int, skillTotal: Int) =
        ClanPackets.encodeMemberStats(ClanMemberStatsPayload(characterId, combatLevel, skillTotal))
    fun selectGuestClan(characterId: Int, clanId: Long) =
        ClanPackets.encodeGuestSelection(ClanGuestSelectionPayload(characterId, ClanGuestSelectionAction.SELECT, clanId))
    fun clearGuestClan(characterId: Int) =
        ClanPackets.encodeGuestSelection(ClanGuestSelectionPayload(characterId, ClanGuestSelectionAction.CLEAR, 0L))
    fun clearKickedGuest(characterId: Int, clanId: Long, targetName: String) =
        ClanPackets.encodeGuestKick(ClanGuestKickPayload(characterId, clanId, targetName))
    fun adminMutation(characterId: Int, clanId: Long, mutationFrame: ByteArray) =
        ClanPackets.encodeAdminMutation(ClanAdminMutationPayload(characterId, clanId, mutationFrame))
}