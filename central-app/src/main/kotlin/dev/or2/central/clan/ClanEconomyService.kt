package dev.or2.central.clan

import dev.or2.central.social.OnlinePresenceIndex
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.protocol.outboundFrame
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.claneconomy.*
import java.sql.SQLException
import java.util.UUID
import org.slf4j.LoggerFactory

/** Central authority for clan-owned economic value and idempotent hall transfers. */
class ClanEconomyService(
    private val repository: CentralClanEconomyRepository,
    private val store: ClanStore,
    private val presence: OnlinePresenceIndex,
    private val connections: WorldConnectionRegistry,
) {
    private val log = LoggerFactory.getLogger(ClanEconomyService::class.java)

    sealed interface Reply {
        data class Ok(val frame: ByteArray) : Reply
        data class Fail(val frame: ByteArray) : Reply
    }

    fun handleSync(worldId: Int, payload: ClanEconomySyncPayload): Reply {
        if (!authorized(worldId, payload.characterId)) return syncFail(ClanEconomyFailure.NOT_LOGGED_IN)
        val state = if (payload.clanId > 0L) store.get(payload.clanId) ?: return syncFail(ClanEconomyFailure.CLAN_NOT_FOUND) else null
        return try {
            val receiptIds = payload.durableReceipts.map(::receiptUuid).toSet()

            // A durable receipt proves the game DB committed the matching player inventory mutation.
            // Unknown or already-aborted receipts are intentionally NOT discarded: they require manual
            // reconciliation rather than risking duplication/loss.
            for (id in receiptIds) {
                val op = repository.operation(id) ?: return syncFail(ClanEconomyFailure.CONFLICT)
                if (op.actorCharacterId != payload.characterId) {
                    return syncFail(ClanEconomyFailure.CONFLICT)
                }
                when (op.state) {
                    ClanEconomyOperationState.PREPARED -> {
                        val committed = repository.commit(id, payload.characterId)
                        if (committed?.operationType in setOf(ClanEconomyOperationType.COFFER_DEPOSIT, ClanEconomyOperationType.COFFER_WITHDRAW)) {
                            broadcastCofferBalance(committed!!.clanId)
                        }
                    }
                    ClanEconomyOperationState.COMMITTED -> Unit
                    ClanEconomyOperationState.ABORTED -> return syncFail(ClanEconomyFailure.CONFLICT)
                    else -> return syncFail(ClanEconomyFailure.CONFLICT)
                }
            }

            // Prepared operations without a durable game-DB receipt never crossed the player-save
            // boundary, so their Central reservations are safe to reverse.
            repository.preparedOperationsForActor(payload.characterId)
                .filter { it.operationId !in receiptIds }
                .forEach { repository.abort(it.operationId, payload.characterId) }

            Reply.Ok(
                ClanEconomyPackets.encodeSyncReply(
                    ClanEconomySyncReply(
                        ok = true,
                        reason = 0,
                        snapshot = state?.let { repository.snapshot(it.clan.clanId).toSnapshot() },
                        resolvedReceipts = payload.durableReceipts,
                    ),
                ),
            )
        } catch (t: Throwable) {
            log.warn("Clan economy sync failed for character {} clan {}", payload.characterId, payload.clanId, t)
            syncFail(ClanEconomyFailure.UNAVAILABLE)
        }
    }

    fun handlePrepare(worldId: Int, payload: ClanEconomyPreparePayload): Reply = guarded(payload.operationId, WorldOpcodes.OP_WORLD_CLAN_ECONOMY_PREPARE) {
        requireAuthorized(worldId, payload.characterId)
        val state = store.get(payload.clanId) ?: failNow(ClanEconomyFailure.CLAN_NOT_FOUND)
        validateDuplicate(payload.operationId, payload.characterId, payload.clanId, payload.operationType)?.let { return@guarded success(it, WorldOpcodes.OP_WORLD_CLAN_ECONOMY_PREPARE) }

        val row = when (payload.operationType) {
            ClanEconomyOperationType.COFFER_DEPOSIT -> {
                requirePermission(state, payload.characterId, ClanRules.DEPOSIT_INTO_COFFER)
                if (payload.quantity <= 0) failNow(ClanEconomyFailure.INVALID_REQUEST)
                repository.prepareCofferDeposit(uuid(payload.operationId), payload.clanId, payload.characterId, worldId, payload.quantity)
                    ?: failNow(ClanEconomyFailure.NO_SPACE)
            }
            ClanEconomyOperationType.COFFER_WITHDRAW -> {
                requirePermission(state, payload.characterId, ClanRules.WITHDRAW_FROM_COFFER)
                if (payload.quantity <= 0) failNow(ClanEconomyFailure.INVALID_REQUEST)
                repository.prepareCofferWithdraw(uuid(payload.operationId), payload.clanId, payload.characterId, worldId, payload.quantity)
                    ?: failNow(ClanEconomyFailure.INSUFFICIENT_FUNDS)
            }
            ClanEconomyOperationType.PARTY_CHEST_DEPOSIT -> {
                requirePermission(state, payload.characterId, ClanRules.DEPOSIT_IN_PARTY_CHEST)
                if (payload.itemId <= 0 || payload.quantity <= 0 || payload.sourceSlot < 0) failNow(ClanEconomyFailure.INVALID_REQUEST)
                if (!payload.stackable && payload.quantity != 1) failNow(ClanEconomyFailure.INVALID_REQUEST)
                val before = repository.snapshot(payload.clanId)
                if (before.activeDrop != null) failNow(ClanEconomyFailure.DROP_ALREADY_ACTIVE)
                repository.preparePartyChestDeposit(
                    uuid(payload.operationId), payload.clanId, payload.characterId, worldId,
                    payload.itemId, payload.quantity, payload.vars, payload.sourceSlot, payload.stackable,
                ) ?: failNow(ClanEconomyFailure.NO_SPACE)
            }
            ClanEconomyOperationType.PARTY_DROP_CLAIM -> {
                requirePermission(state, payload.characterId, ClanRules.PARTY_BALLOON_DROPS)
                if (payload.referenceId <= 0L) failNow(ClanEconomyFailure.INVALID_REQUEST)
                repository.claimPartyDrop(
                    uuid(payload.operationId), payload.clanId, payload.characterId, worldId, payload.referenceId,
                ) ?: failNow(ClanEconomyFailure.EMPTY)
            }
            else -> failNow(ClanEconomyFailure.INVALID_REQUEST)
        }
        success(row, WorldOpcodes.OP_WORLD_CLAN_ECONOMY_PREPARE)
    }

    fun handlePartyDropPrepare(worldId: Int, payload: ClanPartyDropPreparePayload): Reply =
        guarded(payload.operationId, WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE) {
            if (worldId <= 0) failNow(ClanEconomyFailure.NOT_LOGGED_IN)
            store.get(payload.clanId) ?: failNow(ClanEconomyFailure.CLAN_NOT_FOUND)

            when (payload.action) {
                ClanPartyDropAction.START -> {
                    requireAuthorized(worldId, payload.characterId)
                    val state = store.get(payload.clanId) ?: failNow(ClanEconomyFailure.CLAN_NOT_FOUND)
                    requirePermission(state, payload.characterId, ClanRules.PULL_PARTY_CHEST_LEVER)
                    validateDuplicate(
                        payload.operationId,
                        payload.characterId,
                        payload.clanId,
                        ClanEconomyOperationType.PARTY_DROP_START,
                    )?.let {
                        return@guarded success(it, WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE)
                    }

                    val before = repository.snapshot(payload.clanId)
                    if (before.activeDrop != null) failNow(ClanEconomyFailure.DROP_ALREADY_ACTIVE)
                    if (before.partyChest.isEmpty()) failNow(ClanEconomyFailure.EMPTY)

                    val row =
                        repository.preparePartyDropStart(
                            uuid(payload.operationId),
                            payload.clanId,
                            payload.characterId,
                            worldId,
                        ) ?: failNow(ClanEconomyFailure.CONFLICT)
                    success(row, WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE)
                }

                ClanPartyDropAction.FINISH_WINDOW -> {
                    if (payload.characterId != 0 || payload.batchId <= 0L) {
                        failNow(ClanEconomyFailure.INVALID_REQUEST)
                    }
                    if (!repository.finishPartyDropWindow(payload.clanId, payload.batchId, worldId)) {
                        failNow(ClanEconomyFailure.CONFLICT)
                    }
                    lifecycleSuccess(payload, batchId = payload.batchId)
                }

                ClanPartyDropAction.DESTROY_HALL -> {
                    if (payload.characterId != 0 || payload.batchId != 0L) {
                        failNow(ClanEconomyFailure.INVALID_REQUEST)
                    }
                    if (!repository.destroyHallPartyStorage(payload.clanId, worldId)) {
                        failNow(ClanEconomyFailure.CONFLICT)
                    }
                    lifecycleSuccess(payload, batchId = 0L)
                }

                else -> failNow(ClanEconomyFailure.INVALID_REQUEST)
            }
        }

    fun handleCommit(worldId: Int, payload: ClanEconomyFinalizePayload): Reply =
        finalize(worldId, payload, WorldOpcodes.OP_WORLD_CLAN_ECONOMY_COMMIT, commit = true)

    fun handleAbort(worldId: Int, payload: ClanEconomyFinalizePayload): Reply =
        finalize(worldId, payload, WorldOpcodes.OP_WORLD_CLAN_ECONOMY_ABORT, commit = false)

    private fun finalize(worldId: Int, payload: ClanEconomyFinalizePayload, requestOpcode: Int, commit: Boolean): Reply =
        guarded(payload.operationId, requestOpcode) {
            requireAuthorized(worldId, payload.characterId)
            val row = if (commit) repository.commit(uuid(payload.operationId), payload.characterId) else repository.abort(uuid(payload.operationId), payload.characterId)
            if (row == null) failNow(ClanEconomyFailure.CONFLICT)
            if (commit && row.state == ClanEconomyOperationState.ABORTED) failNow(ClanEconomyFailure.CONFLICT)
            if (commit && row.state == ClanEconomyOperationState.COMMITTED &&
                row.operationType in setOf(ClanEconomyOperationType.COFFER_DEPOSIT, ClanEconomyOperationType.COFFER_WITHDRAW)) {
                broadcastCofferBalance(row.clanId)
            }
            success(row, requestOpcode)
        }

    private fun broadcastCofferBalance(clanId: Long) {
        val balance = repository.snapshot(clanId).cofferBalance
        val frame = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_COFFER_BALANCE) {
            writeLong(clanId)
            writeInt(balance)
        }
        connections.registeredWorldIds().forEach { connections.push(it, frame) }
    }

    private fun validateDuplicate(id: ClanEconomyOperationId, actor: Int, clanId: Long, type: Int): ClanEconomyOperationRow? {
        val existing = repository.operation(uuid(id)) ?: return null
        if (existing.actorCharacterId != actor || existing.clanId != clanId || existing.operationType != type) {
            failNow(ClanEconomyFailure.CONFLICT)
        }
        if (existing.state == ClanEconomyOperationState.ABORTED) failNow(ClanEconomyFailure.CONFLICT)
        return existing
    }

    private fun success(row: ClanEconomyOperationRow, requestOpcode: Int): Reply.Ok {
        val batchId = row.batchId
        val items = if (row.operationType == ClanEconomyOperationType.PARTY_DROP_START && batchId != null) {
            repository.batchItems(batchId).map { ClanEconomyItem(it.slot, it.itemId, it.quantity, it.vars) }
        } else emptyList()
        return Reply.Ok(
            ClanEconomyPackets.encodeOperationReply(
                ClanEconomyOperationReply(
                    ok = true,
                    reason = 0,
                    requestOpcode = requestOpcode,
                    operationId = operationId(row.operationId),
                    clanId = row.clanId,
                    operationType = row.operationType,
                    itemId = row.itemId,
                    quantity = row.quantity,
                    vars = row.vars,
                    targetSlot = row.targetSlot,
                    batchId = row.batchId ?: 0L,
                    batchItems = items,
                ),
            ),
        )
    }

    private fun lifecycleSuccess(payload: ClanPartyDropPreparePayload, batchId: Long): Reply.Ok =
        Reply.Ok(
            ClanEconomyPackets.encodeOperationReply(
                ClanEconomyOperationReply(
                    ok = true,
                    reason = 0,
                    requestOpcode = WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE,
                    operationId = payload.operationId,
                    clanId = payload.clanId,
                    operationType = ClanEconomyOperationType.PARTY_DROP_START,
                    batchId = batchId,
                ),
            ),
        )

    private inline fun guarded(id: ClanEconomyOperationId, requestOpcode: Int, block: () -> Reply): Reply = try {
        block()
    } catch (failure: EconomyFailure) {
        fail(id, requestOpcode, failure.reason)
    } catch (sql: SQLException) {
        log.warn("Clan economy request {} failed in PostgreSQL", requestOpcode, sql)
        fail(id, requestOpcode, ClanEconomyFailure.UNAVAILABLE)
    } catch (t: Throwable) {
        log.warn("Clan economy request {} failed", requestOpcode, t)
        fail(id, requestOpcode, ClanEconomyFailure.UNAVAILABLE)
    }

    private fun fail(id: ClanEconomyOperationId, requestOpcode: Int, reason: Int): Reply.Fail = Reply.Fail(
        ClanEconomyPackets.encodeOperationReply(
            ClanEconomyOperationReply(
                ok = false,
                reason = reason,
                requestOpcode = requestOpcode,
                operationId = id,
                clanId = 0L,
                operationType = 0,
            ),
        ),
    )

    private fun syncFail(reason: Int): Reply.Fail = Reply.Fail(
        ClanEconomyPackets.encodeSyncReply(ClanEconomySyncReply(false, reason, null, emptyList())),
    )

    private fun requireAuthorized(worldId: Int, characterId: Int) {
        if (!authorized(worldId, characterId)) failNow(ClanEconomyFailure.NOT_LOGGED_IN)
    }

    private fun authorized(worldId: Int, characterId: Int): Boolean =
        worldId > 0 && characterId > 0 && presence.isOnline(characterId, worldId)

    private fun requirePermission(state: ClanStateRow, actorCharacterId: Int, settingId: Int) {
        if (!hasPermission(state, actorCharacterId, settingId)) failNow(ClanEconomyFailure.NOT_ALLOWED)
    }

    private fun hasPermission(
        state: ClanStateRow,
        actorCharacterId: Int,
        settingId: Int,
    ): Boolean {
        // Clan hall economy uses the real clan rank; staff get no override here.
        if (state.clan.ownerCharacterId == actorCharacterId) {
            return true
        }

        val actorRank =
            state.members
                .firstOrNull { it.characterId == actorCharacterId }
                ?.rank
                ?: ClanRules.GUEST

        val required =
            state.permissions[settingId] ?: ClanRules.OWNER

        if (required == ClanRules.NOBODY) return false
        if (required == ClanRules.GUEST) return true

        return actorRank >= required
    }

    private fun ClanEconomyStateRow.toSnapshot(): ClanEconomySnapshot = ClanEconomySnapshot(
        clanId = clanId,
        cofferBalance = cofferBalance,
        partyChest = partyChest.map { ClanEconomyItem(it.slot, it.itemId, it.quantity, it.vars) },
        activeDrop =
            activeDrop?.let {
                ClanPartyDropBatchSnapshot(
                    batchId = it.batchId,
                    worldId = it.worldId,
                    state = it.state,
                    availableItemCount = it.availableItemCount,
                )
            },
    )

    private fun receiptUuid(id: ClanEconomyReceiptId): UUID = UUID(id.high, id.low)
    private fun uuid(id: ClanEconomyOperationId): UUID = UUID(id.high, id.low)
    private fun operationId(id: UUID): ClanEconomyOperationId = ClanEconomyOperationId(id.mostSignificantBits, id.leastSignificantBits)

    private fun failNow(reason: Int): Nothing = throw EconomyFailure(reason)
    private class EconomyFailure(val reason: Int) : RuntimeException()
}