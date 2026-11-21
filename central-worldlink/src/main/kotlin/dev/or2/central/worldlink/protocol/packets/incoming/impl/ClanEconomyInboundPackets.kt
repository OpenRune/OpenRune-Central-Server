package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyFinalizePayload
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyPackets
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyPreparePayload
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomySyncPayload
import dev.or2.central.worldlink.protocol.claneconomy.ClanPartyDropPreparePayload

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ECONOMY_SYNC,
    name = "WORLD_CLAN_ECONOMY_SYNC",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanEconomySyncPacket : InboundPacket<ClanEconomySyncPayload> {
    override fun decode(input: FrameReader) = ClanEconomyPackets.decodeSync(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ECONOMY_PREPARE,
    name = "WORLD_CLAN_ECONOMY_PREPARE",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanEconomyPreparePacket : InboundPacket<ClanEconomyPreparePayload> {
    override fun decode(input: FrameReader) = ClanEconomyPackets.decodePrepare(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE,
    name = "WORLD_CLAN_PARTY_DROP_PREPARE",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanPartyDropPreparePacket : InboundPacket<ClanPartyDropPreparePayload> {
    override fun decode(input: FrameReader) = ClanEconomyPackets.decodePartyDropPrepare(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ECONOMY_COMMIT,
    name = "WORLD_CLAN_ECONOMY_COMMIT",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanEconomyCommitPacket : InboundPacket<ClanEconomyFinalizePayload> {
    override fun decode(input: FrameReader) = ClanEconomyPackets.decodeFinalize(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CLAN_ECONOMY_ABORT,
    name = "WORLD_CLAN_ECONOMY_ABORT",
    fields = [FieldKind.CLAN_ECONOMY_VARIABLE_REQUIRED],
)
object WorldClanEconomyAbortPacket : InboundPacket<ClanEconomyFinalizePayload> {
    override fun decode(input: FrameReader) = ClanEconomyPackets.decodeFinalize(input)
}
