package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.*
import dev.or2.central.worldlink.protocol.clanbroadcast.*

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_CLAN_SYSTEM_BROADCAST, name = "WORLD_CLAN_SYSTEM_BROADCAST", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldClanSystemBroadcastPacket : InboundPacket<ClanSystemBroadcastRequest> {
    override fun decode(input: FrameReader) = ClanSystemBroadcastPackets.decodeRequest(input)
}