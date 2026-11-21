package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyOperationReply
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyPackets
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomySyncReply

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ECONOMY_SYNC_REPLY,
    name = "WORLD_CLAN_ECONOMY_SYNC_REPLY",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanEconomySyncReplyPacket : OutboundPacket<ClanEconomySyncReply> {
    override fun encode(payload: ClanEconomySyncReply) = ClanEconomyPackets.encodeSyncReply(payload)
    fun decode(input: FrameReader) = ClanEconomyPackets.decodeSyncReply(input)
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ECONOMY_RESULT,
    name = "WORLD_CLAN_ECONOMY_RESULT",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanEconomyResultPacket : OutboundPacket<ClanEconomyOperationReply> {
    override fun encode(payload: ClanEconomyOperationReply) = ClanEconomyPackets.encodeOperationReply(payload)
    fun decode(input: FrameReader) = ClanEconomyPackets.decodeOperationReply(input)
}