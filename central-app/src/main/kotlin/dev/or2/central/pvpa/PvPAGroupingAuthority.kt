package dev.or2.central.pvpa

import dev.or2.central.worldlink.protocol.pvpa.*

/**
 * Serializes authenticated mutations. The presence verifier must consult Central's
 * OnlinePresenceIndex for each request; never accept a client-supplied world ID.
 *
 * No invitations, arena sessions, RS client ranks, or database writes occur here.
 */
class PvPAGroupingAuthority(
    private val state: PvPAGroupingState,
    private val isOnline: (characterId: Int, worldId: Int) -> Boolean,
) {
    @Synchronized
    fun execute(worldId: Int, command: PvPAGroupingCommand): PvPAGroupingResult {
        if (worldId <= 0 || command.characterId <= 0 || !isOnline(command.characterId, worldId)) {
            return failure(command, PvPAGroupingWire.NOT_ALLOWED)
        }
        val before = state.forCharacter(command.characterId)
        val member = before?.members?.firstOrNull { it.characterId == command.characterId }
        if (member != null && member.worldId != worldId) {
            if (command.action != PvPAGroupingWire.RESTORE) return failure(command, PvPAGroupingWire.NOT_ALLOWED)
            // OnlinePresenceIndex already verified current ownership on this world.
            // Reconcile a reconnect/hop; do not trust a world ID supplied by the client.
            state.transferVerified(command.characterId, member.worldId, worldId)
        }
        val current = state.forCharacter(command.characterId)
        val snapshot = when (command.action) {
            PvPAGroupingWire.LOAD -> {
                val mode = PvPAGroupingPresentation.modeFromCode(command.mode)
                    ?: return failure(command, PvPAGroupingWire.BAD_REQUEST)
                if (command.region !in 0..2 || command.displayName.isBlank() ||
                    command.displayName.toByteArray(Charsets.UTF_8).size > PvPAGroupingWire.MAX_DISPLAY_NAME_UTF8) {
                    return failure(command, PvPAGroupingWire.BAD_REQUEST)
                }
                val key = PvPAGroupKey(mode, command.region)
                val group = state.forGroup(key)
                val alreadyThere = group.members.any { it.characterId == command.characterId }
                if (!alreadyThere && group.members.size >= PvPAGroupingWire.MAX_MEMBERS) {
                    return failure(command, PvPAGroupingWire.CAPACITY)
                }
                state.load(command.characterId, command.displayName, worldId, key)
            }
            PvPAGroupingWire.SIGN_UP -> state.signUp(command.characterId, worldId)
            PvPAGroupingWire.WITHDRAW -> state.withdraw(command.characterId, worldId)
            PvPAGroupingWire.LEAVE -> state.leave(command.characterId, worldId)
            PvPAGroupingWire.RESTORE -> current
            else -> return failure(command, PvPAGroupingWire.BAD_REQUEST)
        }
        // RESTORE with no membership is a successful, authoritative negative result.
        // SIGN_UP / WITHDRAW / LEAVE still require an existing membership.
        if (snapshot == null && command.action != PvPAGroupingWire.RESTORE &&
            command.action != PvPAGroupingWire.LEAVE) {
            return failure(command, PvPAGroupingWire.NOT_JOINED)
        }
        val now = state.forCharacter(command.characterId)
        return PvPAGroupingResult(command.action, 0, snapshot,
            now?.members?.firstOrNull { it.characterId == command.characterId })
    }
    private fun failure(command: PvPAGroupingCommand, status: Int) = PvPAGroupingResult(
        command.action, status, state.forCharacter(command.characterId),
        state.forCharacter(command.characterId)?.members?.firstOrNull { it.characterId == command.characterId },
    )
}
