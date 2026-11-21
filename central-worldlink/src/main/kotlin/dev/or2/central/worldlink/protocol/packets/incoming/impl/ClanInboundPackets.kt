package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming
import dev.or2.central.worldlink.protocol.clan.*

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SYNC, name = "WORLD_CLAN_SYNC", fields = [FieldKind.INT, FieldKind.BYTE])
object WorldClanSyncPacket : InboundPacket<ClanSyncPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeSync(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_CREATE, name = "WORLD_CLAN_CREATE", fields = [FieldKind.INT, FieldKind.STRING_96, FieldKind.CLAN_VARIABLE])
object WorldClanCreatePacket : InboundPacket<ClanCreatePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeCreate(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER, name = "WORLD_CLAN_ADD_MEMBER", fields = [FieldKind.INT, FieldKind.INT])
object WorldClanAddMemberPacket : InboundPacket<ClanMemberActionPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeMemberAction(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_LEAVE, name = "WORLD_CLAN_LEAVE", fields = [FieldKind.INT])
object WorldClanLeavePacket : InboundPacket<ClanCharacterPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeCharacter(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER, name = "WORLD_CLAN_REMOVE_MEMBER", fields = [FieldKind.INT, FieldKind.INT])
object WorldClanRemoveMemberPacket : InboundPacket<ClanMemberActionPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeMemberAction(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_RANK, name = "WORLD_CLAN_SET_RANK", fields = [FieldKind.INT, FieldKind.INT, FieldKind.BYTE])
object WorldClanSetRankPacket : InboundPacket<ClanMemberRankPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeMemberRank(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_MUTED, name = "WORLD_CLAN_SET_MUTED", fields = [FieldKind.INT, FieldKind.INT, FieldKind.BYTE])
object WorldClanSetMutedPacket : InboundPacket<ClanMemberMutedPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeMemberMuted(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_BAN_ADD, name = "WORLD_CLAN_BAN_ADD", fields = [FieldKind.INT, FieldKind.INT, FieldKind.STRING_96])
object WorldClanBanAddPacket : InboundPacket<ClanBanAddPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeBanAdd(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE, name = "WORLD_CLAN_BAN_REMOVE", fields = [FieldKind.INT, FieldKind.LONG])
object WorldClanBanRemovePacket : InboundPacket<ClanBanRemovePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeBanRemove(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_PROFILE, name = "WORLD_CLAN_SET_PROFILE", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.INT])
object WorldClanSetProfilePacket : InboundPacket<ClanProfilePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeProfile(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_MOTIF, name = "WORLD_CLAN_SET_MOTIF", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.BYTE, FieldKind.BYTE])
object WorldClanSetMotifPacket : InboundPacket<ClanMotifPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeMotif(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS, name = "WORLD_CLAN_SET_RECRUIT_REQUIREMENTS", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.USHORT, FieldKind.USHORT, FieldKind.BYTE])
object WorldClanSetRecruitRequirementsPacket : InboundPacket<ClanRecruitRequirementsPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeRecruitRequirements(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_HALL_SETTINGS, name = "WORLD_CLAN_SET_HALL_SETTINGS", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.BYTE, FieldKind.USHORT])
object WorldClanSetHallSettingsPacket : InboundPacket<ClanHallSettingsPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeHallSettings(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_SETTING, name = "WORLD_CLAN_SET_SETTING", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.USHORT, FieldKind.INT])
object WorldClanSetSettingPacket : InboundPacket<ClanSettingPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeSetting(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_RANK_TITLES, name = "WORLD_CLAN_SET_RANK_TITLES", fields = [FieldKind.INT, FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanSetRankTitlesPacket : InboundPacket<ClanRankTitlesPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeRankTitles(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SET_BOARD_STATE, name = "WORLD_CLAN_SET_BOARD_STATE", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.BYTE])
object WorldClanSetBoardStatePacket : InboundPacket<ClanBoardStatePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeBoardState(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_APPLICATION_SUBMIT, name = "WORLD_CLAN_APPLICATION_SUBMIT", fields = [FieldKind.INT, FieldKind.LONG, FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanApplicationSubmitPacket : InboundPacket<ClanApplicationSubmitPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeApplicationSubmit(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW, name = "WORLD_CLAN_APPLICATION_REVIEW", fields = [FieldKind.INT, FieldKind.LONG, FieldKind.BYTE])
object WorldClanApplicationReviewPacket : InboundPacket<ClanApplicationReviewPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeApplicationReview(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_APPLICATION_WITHDRAW, name = "WORLD_CLAN_APPLICATION_WITHDRAW", fields = [FieldKind.INT, FieldKind.LONG])
object WorldClanApplicationWithdrawPacket : InboundPacket<ClanApplicationWithdrawPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeApplicationWithdraw(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_EVENT_UPSERT, name = "WORLD_CLAN_EVENT_UPSERT", fields = [FieldKind.INT, FieldKind.LONG, FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT])
object WorldClanEventUpsertPacket : InboundPacket<ClanEventUpsertPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeEventUpsert(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_EVENT_DELETE, name = "WORLD_CLAN_EVENT_DELETE", fields = [FieldKind.INT, FieldKind.LONG])
object WorldClanEventDeletePacket : InboundPacket<ClanEventDeletePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeEventDelete(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_ABDICATION, name = "WORLD_CLAN_ABDICATION", fields = [FieldKind.INT, FieldKind.BYTE])
object WorldClanAbdicationPacket : InboundPacket<ClanAbdicationPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeAbdication(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_BOARD_SYNC, name = "WORLD_CLAN_BOARD_SYNC", fields = [FieldKind.INT])
object WorldClanBoardSyncPacket : InboundPacket<ClanCharacterPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeCharacter(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_DETAIL, name = "WORLD_CLAN_DETAIL", fields = [FieldKind.INT, FieldKind.LONG])
object WorldClanDetailPacket : InboundPacket<ClanDetailPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeDetail(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_RENAME, name = "WORLD_CLAN_RENAME", fields = [FieldKind.INT, FieldKind.STRING_96])
object WorldClanRenamePacket : InboundPacket<ClanRenamePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeRename(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_INVITE_CREATE, name = "WORLD_CLAN_INVITE_CREATE", fields = [FieldKind.INT, FieldKind.INT, FieldKind.LONG])
object WorldClanInviteCreatePacket : InboundPacket<ClanInviteCreatePayload> { override fun decode(input: FrameReader) = ClanPackets.decodeInviteCreate(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_INVITE_ACCEPT, name = "WORLD_CLAN_INVITE_ACCEPT", fields = [FieldKind.INT, FieldKind.LONG])
object WorldClanInviteAcceptPacket : InboundPacket<ClanInviteAcceptPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeInviteAccept(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_APPLICATION_VERDICT_ACK, name = "WORLD_CLAN_APPLICATION_VERDICT_ACK", fields = [FieldKind.INT, FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanApplicationVerdictAckPacket : InboundPacket<ClanApplicationVerdictAckPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeApplicationVerdictAck(input) }

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION,
    name = "WORLD_CLAN_ADMIN_MUTATION",
    fields = [FieldKind.INT, FieldKind.LONG, FieldKind.USHORT, FieldKind.CLAN_VARIABLE_REQUIRED],
)
object WorldClanAdminMutationPacket : InboundPacket<ClanAdminMutationPayload> {
    override fun decode(input: FrameReader) = ClanPackets.decodeAdminMutation(input)
}
@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_MEMBER_STATS, name = "WORLD_CLAN_MEMBER_STATS", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.USHORT])
object WorldClanMemberStatsPacket : InboundPacket<ClanMemberStatsPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeMemberStats(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_GUEST_SELECTION, name = "WORLD_CLAN_GUEST_SELECTION", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.LONG])
object WorldClanGuestSelectionPacket : InboundPacket<ClanGuestSelectionPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeGuestSelection(input) }

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_GUEST_KICK, name = "WORLD_CLAN_GUEST_KICK", fields = [FieldKind.INT, FieldKind.LONG, FieldKind.STRING_96])
object WorldClanGuestKickPacket : InboundPacket<ClanGuestKickPayload> { override fun decode(input: FrameReader) = ClanPackets.decodeGuestKick(input) }
