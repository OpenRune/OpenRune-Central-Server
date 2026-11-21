package dev.or2.central.worldlink.handlers

import dev.or2.central.pvpa.*
import dev.or2.central.worldlink.protocol.pvpa.*
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldPvPAGroupingPacket

/** Central-authenticated mutations and roster fanout; gameplay stays on the world. */
class PvPAGroupingHandler(
    private val authority: PvPAGroupingAuthority,
    private val state: PvPAGroupingState,
    private val connections: WorldConnectionRegistry,
) {
    @Synchronized
    fun handle(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone || connection.worldId <= 0) return HandlerResult.CloseSilent
        val request = try { WorldPvPAGroupingPacket.decode(input) }
        catch (_: IllegalArgumentException) { return HandlerResult.CloseSilent }
        // Record the old audience BEFORE mutating the Central state. A group switch
        // removes the departing player from the old group's updated roster, so
        // deriving recipients after execution would omit that player's former world.
        val before = state.forCharacter(request.characterId)
        val oldAudience = before?.members?.mapTo(linkedSetOf()) { it.worldId }.orEmpty()
        val result = authority.execute(connection.worldId, request)
        if (result.status == 0 && (request.action != PvPAGroupingWire.RESTORE || before != result.snapshot)) {
            val affected = linkedMapOf<PvPAGroupKey, Pair<PvPAGroupSnapshot, Set<Int>>>()
            before?.let { old ->
                val updatedOld = state.forGroup(old.key)
                affected[old.key] = updatedOld to
                        (oldAudience + updatedOld.members.map { it.worldId } + connection.worldId)
            }
            result.snapshot?.let { updatedNew ->
                val previousRecipients = affected[updatedNew.key]?.second.orEmpty()
                affected[updatedNew.key] = updatedNew to
                        (previousRecipients + updatedNew.members.map { it.worldId } + connection.worldId)
            }
            affected.values.forEach { (snapshot, recipients) ->
                val frame = PvPAGroupingPackets.encodeRoster(snapshot)
                recipients.forEach { worldId -> connections.push(worldId, frame) }
            }
        }
        return HandlerResult.Reply(PvPAGroupingPackets.encodeResult(result))
    }
}