package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.*
import dev.or2.central.worldlink.protocol.clanbroadcast.*

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_CLAN_SYSTEM_BROADCAST_ACK, name = "WORLD_CLAN_SYSTEM_BROADCAST_ACK", fields = [FieldKind.BYTE])
object WorldClanSystemBroadcastAckPacket : OutboundPacket<Boolean> {
    override fun encode(payload: Boolean) = ClanSystemBroadcastPackets.encodeAck(payload)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_CLAN_SYSTEM_BROADCAST, name = "SERVER_CLAN_SYSTEM_BROADCAST", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerClanSystemBroadcastPacket : OutboundPacket<ClanSystemBroadcastPush> {
    override fun encode(payload: ClanSystemBroadcastPush) = ClanSystemBroadcastPackets.encodePush(payload)
}