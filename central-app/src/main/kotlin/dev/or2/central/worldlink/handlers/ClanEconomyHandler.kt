package dev.or2.central.worldlink.handlers

import dev.or2.central.clan.ClanEconomyService
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyFailure
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyOperationId
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyOperationReply
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomyPackets
import dev.or2.central.worldlink.protocol.claneconomy.ClanEconomySyncReply
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldClanEconomyAbortPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldClanEconomyCommitPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldClanEconomyPreparePacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldClanEconomySyncPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldClanPartyDropPreparePacket

class ClanEconomyHandler(
    private val service: ClanEconomyService,
) {
    fun handle(connection: WorldConnection, opcode: Int, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) return notAllowed(opcode)
        val reply = try {
            when (opcode) {
                WorldOpcodes.OP_WORLD_CLAN_ECONOMY_SYNC -> service.handleSync(connection.worldId, WorldClanEconomySyncPacket.decode(input))
                WorldOpcodes.OP_WORLD_CLAN_ECONOMY_PREPARE -> service.handlePrepare(connection.worldId, WorldClanEconomyPreparePacket.decode(input))
                WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE -> service.handlePartyDropPrepare(connection.worldId, WorldClanPartyDropPreparePacket.decode(input))
                WorldOpcodes.OP_WORLD_CLAN_ECONOMY_COMMIT -> service.handleCommit(connection.worldId, WorldClanEconomyCommitPacket.decode(input))
                WorldOpcodes.OP_WORLD_CLAN_ECONOMY_ABORT -> service.handleAbort(connection.worldId, WorldClanEconomyAbortPacket.decode(input))
                else -> return notAllowed(opcode)
            }
        } catch (_: PacketDecodeException) {
            return notAllowed(opcode)
        } catch (_: IllegalArgumentException) {
            return notAllowed(opcode)
        }
        return when (reply) {
            is ClanEconomyService.Reply.Ok -> HandlerResult.Reply(reply.frame)
            is ClanEconomyService.Reply.Fail -> HandlerResult.Reply(reply.frame)
        }
    }

    private fun notAllowed(opcode: Int): HandlerResult {
        val frame = if (opcode == WorldOpcodes.OP_WORLD_CLAN_ECONOMY_SYNC) {
            ClanEconomyPackets.encodeSyncReply(ClanEconomySyncReply(false, ClanEconomyFailure.NOT_ALLOWED, null))
        } else {
            ClanEconomyPackets.encodeOperationReply(
                ClanEconomyOperationReply(
                    ok = false,
                    reason = ClanEconomyFailure.NOT_ALLOWED,
                    requestOpcode = opcode,
                    operationId = ClanEconomyOperationId(0L, 0L),
                    clanId = 0L,
                    operationType = 0,
                ),
            )
        }
        return HandlerResult.Reply(frame)
    }
}
