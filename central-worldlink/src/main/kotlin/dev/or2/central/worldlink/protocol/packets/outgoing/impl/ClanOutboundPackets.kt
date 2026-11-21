package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame
import dev.or2.central.worldlink.protocol.clan.*

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_OK, name = "WORLD_CLAN_OK", fields = [FieldKind.BYTE, FieldKind.LONG])
object WorldClanOkPacket : OutboundPacket<ClanMutationReply> { override fun encode(payload: ClanMutationReply) = ClanPackets.encodeOk(payload) }

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_FAIL, name = "WORLD_CLAN_FAIL", fields = [FieldKind.BYTE, FieldKind.BYTE])
object WorldClanFailPacket : OutboundPacket<ClanMutationFail> { override fun encode(payload: ClanMutationFail) = ClanPackets.encodeFail(payload) }

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_SYNC_OK, name = "WORLD_CLAN_SYNC_OK", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanSyncOkPacket : OutboundPacket<ClanCharacterSync> {
    override fun encode(payload: ClanCharacterSync) = ClanPackets.encodeSyncOk(payload)
    fun decode(input: FrameReader) = ClanPackets.decodeSyncOk(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_SYNC_FAIL, name = "WORLD_CLAN_SYNC_FAIL", fields = [FieldKind.BYTE])
object WorldClanSyncFailPacket : OutboundPacket<Int> { override fun encode(payload: Int) = ClanPackets.encodeSyncFail(payload) }

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_BOARD_SYNC_OK, name = "WORLD_CLAN_BOARD_SYNC_OK", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanBoardSyncOkPacket : OutboundPacket<ClanBoardSnapshot> {
    override fun encode(payload: ClanBoardSnapshot) = ClanPackets.encodeBoardOk(payload)
    fun decode(input: FrameReader) = ClanPackets.decodeBoardOk(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_DETAIL_OK, name = "WORLD_CLAN_DETAIL_OK", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanDetailOkPacket : OutboundPacket<ClanSnapshot?> {
    override fun encode(payload: ClanSnapshot?) = ClanPackets.encodeDetailOk(payload)
    fun decode(input: FrameReader) = ClanPackets.decodeDetailOk(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_SNAPSHOT, name = "SERVER_CLAN_SNAPSHOT", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerClanSnapshotPacket : OutboundPacket<ClanSnapshot> {
    override fun encode(payload: ClanSnapshot) = ClanPackets.encodeServerSnapshot(payload)
    fun decode(input: FrameReader) = ClanPackets.decodeServerSnapshot(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_MEMBERSHIP_CLEARED, name = "SERVER_CLAN_MEMBERSHIP_CLEARED", fields = [FieldKind.INT, FieldKind.LONG, FieldKind.BYTE])
object ServerClanMembershipClearedPacket : OutboundPacket<ClanMembershipClearedPush> { override fun encode(payload: ClanMembershipClearedPush) = ClanPackets.encodeMembershipCleared(payload) }

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_INVITE, name = "SERVER_CLAN_INVITE", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerClanInvitePacket : OutboundPacket<ClanInvitePush> { override fun encode(payload: ClanInvitePush) = ClanPackets.encodeInvitePush(payload) }

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_APPLICATION_RESULT, name = "SERVER_CLAN_APPLICATION_RESULT", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerClanApplicationResultPacket : OutboundPacket<ClanApplicationResultPush> { override fun encode(payload: ClanApplicationResultPush) = ClanPackets.encodeApplicationResultPush(payload) }

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_DISBANDED, name = "SERVER_CLAN_DISBANDED", fields = [FieldKind.LONG])
object ServerClanDisbandedPacket : OutboundPacket<Long> { override fun encode(payload: Long) = ClanPackets.encodeServerDisbanded(payload) }

/** Tells member worlds an applicant withdrew, so recruiters can be notified. */
@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_APPLICATION_WITHDRAWN, name = "SERVER_CLAN_APPLICATION_WITHDRAWN", fields = [FieldKind.LONG])
object ServerClanApplicationWithdrawnPacket : OutboundPacket<Long> { override fun encode(payload: Long) = ClanPackets.encodeServerApplicationWithdrawn(payload) }

/** Public invalidation hint only; worlds fetch details with a logged-in, authorized viewer. */
@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_INVALIDATED, name = "SERVER_CLAN_INVALIDATED", fields = [FieldKind.LONG])
object ServerClanInvalidatedPacket : OutboundPacket<Long> {
    override fun encode(payload: Long) = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_INVALIDATED) {
        writeLong(payload)
    }
}

/** Read-only balance notification, not an economy transaction or inventory grant. */
@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_COFFER_BALANCE, name = "SERVER_CLAN_COFFER_BALANCE", fields = [FieldKind.LONG, FieldKind.INT])
object ServerClanCofferBalancePacket : OutboundPacket<Pair<Long, Int>> {
    override fun encode(payload: Pair<Long, Int>) = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_COFFER_BALANCE) {
        writeLong(payload.first)
        writeInt(payload.second)
    }
}