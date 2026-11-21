package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.pvpa.*
import dev.or2.central.worldlink.protocol.*

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_PVPA_GROUPING, name = "WORLD_PVPA_GROUPING", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldPvPAGroupingPacket : InboundPacket<PvPAGroupingCommand> {
    override fun decode(input: FrameReader) = PvPAGroupingPackets.decodeCommand(input)
}
