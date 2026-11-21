package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.pvpa.*
import dev.or2.central.worldlink.protocol.*

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_PVPA_GROUPING_RESULT, name = "WORLD_PVPA_GROUPING_RESULT", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldPvPAGroupingResultPacket : OutboundPacket<PvPAGroupingResult> {
    override fun encode(payload: PvPAGroupingResult) = PvPAGroupingPackets.encodeResult(payload)
    fun decode(input: FrameReader) = PvPAGroupingPackets.decodeResult(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_PVPA_GROUPING_ROSTER, name = "SERVER_PVPA_GROUPING_ROSTER", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerPvPAGroupingRosterPacket : OutboundPacket<PvPAGroupSnapshot> {
    override fun encode(payload: PvPAGroupSnapshot) = PvPAGroupingPackets.encodeRoster(payload)
    fun decode(input: FrameReader) = PvPAGroupingPackets.decodeRoster(input)
}
