package dev.or2.central.clan

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource
import kotlin.random.Random

/** Database-only representation of durable clan hall economic state. */
data class ClanEconomyItemRow(
    val slot: Int,
    val itemId: Int,
    val quantity: Int,
    val vars: Int,
)

data class ClanEconomyBatchRow(
    val batchId: Long,
    val clanId: Long,
    val worldId: Int,
    val state: Int,
    val availableItemCount: Int,
)

data class ClanEconomyOperationRow(
    val operationId: UUID,
    val clanId: Long,
    val actorCharacterId: Int,
    val worldId: Int,
    val operationType: Int,
    val state: Int,
    val itemId: Int,
    val quantity: Int,
    val vars: Int,
    val sourceSlot: Int,
    val targetSlot: Int,
    val batchId: Long?,
)

data class ClanEconomyStateRow(
    val clanId: Long,
    val cofferBalance: Int,
    val partyChest: List<ClanEconomyItemRow>,
    val activeDrop: ClanEconomyBatchRow?,
)

/**
 * Durable clan hall economy repository.
 *
 * Every mutation takes a PostgreSQL advisory transaction lock keyed by clan id. This keeps
 * prepare/commit/abort and party-drop transitions serialized even when several worlds act on the
 * same clan concurrently.
 */
class CentralClanEconomyRepository(
    private val dataSource: DataSource,
) {
    fun snapshot(clanId: Long): ClanEconomyStateRow = transaction { conn ->
        advisoryLock(conn, clanId)
        finishExpiredDropBatch(conn, clanId)
        snapshot(conn, clanId)
    }

    fun operation(operationId: UUID): ClanEconomyOperationRow? =
        dataSource.connection.use { conn -> loadOperation(conn, operationId, forUpdate = false) }

    fun batchItems(batchId: Long): List<ClanEconomyItemRow> =
        dataSource.connection.use { conn -> loadBatchItems(conn, batchId) }

    fun preparedOperationsForActor(characterId: Int): List<ClanEconomyOperationRow> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT operation_id, clan_id, actor_character_id, world_id, operation_type, state,
                       item_id, quantity, item_vars, source_slot, target_slot, batch_id
                FROM clan_economy_operations
                WHERE actor_character_id = ? AND state = 0
                ORDER BY created_at, operation_id
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toOperation()) } }
            }
        }

    fun prepareCofferDeposit(
        operationId: UUID,
        clanId: Long,
        actorCharacterId: Int,
        worldId: Int,
        quantity: Int,
    ): ClanEconomyOperationRow? = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction null
        existingOrNull(conn, operationId)?.let { return@transaction it }
        require(quantity > 0)
        ensureCoffer(conn, clanId)
        val balance = conn.prepareStatement("SELECT coin_balance FROM clan_coffers WHERE clan_id = ? FOR UPDATE").use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
        val pendingDeposits = conn.prepareStatement(
            "SELECT COALESCE(sum(quantity), 0) FROM clan_economy_operations WHERE clan_id = ? AND state = 0 AND operation_type = 1",
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
        val pendingWithdrawals = conn.prepareStatement(
            "SELECT COALESCE(sum(quantity), 0) FROM clan_economy_operations WHERE clan_id = ? AND state = 0 AND operation_type = 2",
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
        // Prepared withdrawals have already reduced coin_balance, but they may still abort and restore
        // those coins. Reserve coffer headroom for that rollback before accepting new deposits.
        if (balance + pendingWithdrawals + pendingDeposits + quantity.toLong() > Int.MAX_VALUE.toLong()) {
            return@transaction null
        }
        insertOperation(
            conn, operationId, clanId, actorCharacterId, worldId,
            TYPE_COFFER_DEPOSIT, itemId = 0, quantity = quantity, vars = 0,
            sourceSlot = -1, targetSlot = -1, batchId = null,
        )
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    fun prepareCofferWithdraw(
        operationId: UUID,
        clanId: Long,
        actorCharacterId: Int,
        worldId: Int,
        quantity: Int,
    ): ClanEconomyOperationRow? = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction null
        existingOrNull(conn, operationId)?.let { return@transaction it }
        require(quantity > 0)
        ensureCoffer(conn, clanId)
        val changed = conn.prepareStatement(
            """
            UPDATE clan_coffers
            SET coin_balance = coin_balance - ?, updated_at = CURRENT_TIMESTAMP
            WHERE clan_id = ? AND coin_balance >= ?
            """.trimIndent(),
        ).use { ps ->
            ps.setInt(1, quantity)
            ps.setLong(2, clanId)
            ps.setInt(3, quantity)
            ps.executeUpdate()
        }
        if (changed != 1) return@transaction null
        insertOperation(
            conn, operationId, clanId, actorCharacterId, worldId,
            TYPE_COFFER_WITHDRAW, itemId = 0, quantity = quantity, vars = 0,
            sourceSlot = -1, targetSlot = -1, batchId = null,
        )
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    fun preparePartyChestDeposit(
        operationId: UUID,
        clanId: Long,
        actorCharacterId: Int,
        worldId: Int,
        itemId: Int,
        quantity: Int,
        vars: Int,
        sourceSlot: Int,
        stackable: Boolean,
    ): ClanEconomyOperationRow? = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction null
        finishExpiredDropBatch(conn, clanId)
        existingOrNull(conn, operationId)?.let { return@transaction it }
        require(itemId > 0 && quantity > 0)
        require(stackable || quantity == 1)
        if (hasOpenDropBatch(conn, clanId)) return@transaction null

        val committed = loadChest(conn, clanId)
        val prepared = loadPreparedPartyDeposits(conn, clanId)

        // Prefer an existing compatible stack if the committed + reserved total stays within Int.MAX.
        val stackSlot =
            if (stackable) {
                committed.firstOrNull { item ->
                    if (item.itemId != itemId || item.vars != vars) return@firstOrNull false
                    val reserved = prepared.filter { it.targetSlot == item.slot }.sumOf { it.quantity.toLong() }
                    item.quantity.toLong() + reserved + quantity.toLong() <= Int.MAX_VALUE.toLong()
                }?.slot
            } else {
                null
            }

        val targetSlot = stackSlot ?: run {
            val occupied = committed.mapTo(hashSetOf()) { it.slot }
            prepared.mapTo(occupied) { it.targetSlot }
            (0 until PARTY_CHEST_SLOTS).firstOrNull { it !in occupied }
        } ?: return@transaction null

        insertOperation(
            conn, operationId, clanId, actorCharacterId, worldId,
            TYPE_PARTY_CHEST_DEPOSIT, itemId, quantity, vars,
            sourceSlot, targetSlot, null,
        )
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    fun preparePartyDropStart(
        operationId: UUID,
        clanId: Long,
        actorCharacterId: Int,
        worldId: Int,
    ): ClanEconomyOperationRow? = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction null
        finishExpiredDropBatch(conn, clanId)
        existingOrNull(conn, operationId)?.let { return@transaction it }
        if (hasOpenDropBatch(conn, clanId)) return@transaction null
        if (hasPreparedPartyMutation(conn, clanId)) return@transaction null
        val chest = loadChest(conn, clanId)
        if (chest.isEmpty()) return@transaction null

        val batchId = conn.prepareStatement(
            "INSERT INTO clan_party_drop_batches (clan_id, world_id, state) VALUES (?, ?, 0) RETURNING id",
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.setInt(2, worldId)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
        conn.prepareStatement(
            """
            INSERT INTO clan_party_drop_items (batch_id, source_slot, item_id, quantity, vars, state)
            SELECT ?, slot, item_id, quantity, vars, 0
            FROM clan_party_chest_items
            WHERE clan_id = ?
            ORDER BY slot
            """.trimIndent(),
        ).use { ps -> ps.setLong(1, batchId); ps.setLong(2, clanId); ps.executeUpdate() }
        conn.prepareStatement("DELETE FROM clan_party_chest_items WHERE clan_id = ?").use { ps ->
            ps.setLong(1, clanId)
            ps.executeUpdate()
        }
        insertOperation(
            conn, operationId, clanId, actorCharacterId, worldId,
            TYPE_PARTY_DROP_START, itemId = 0, quantity = PARTY_DROP_LEVER_COST, vars = 0,
            sourceSlot = -1, targetSlot = -1, batchId = batchId,
        )
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    /**
     * Atomically consumes one reward portion from an active party-drop pool.
     *
     * Balloon rewards never cross the player's inventory save boundary: the world creates normal
     * player-owned ground loot only after this committed result is returned. Keeping the claim in a
     * single Central transaction avoids orphaned reserved rows while the stable operation id makes
     * a repeated request return the exact same item/quantity instead of consuming twice.
     */
    fun claimPartyDrop(
        operationId: UUID,
        clanId: Long,
        actorCharacterId: Int,
        worldId: Int,
        batchId: Long,
    ): ClanEconomyOperationRow? = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction null
        finishExpiredDropBatch(conn, clanId)
        existingOrNull(conn, operationId)?.let { return@transaction it }

        val batch = loadOpenBatch(conn, clanId) ?: return@transaction null
        if (batch.batchId != batchId || batch.worldId != worldId || batch.state != BATCH_ACTIVE) {
            return@transaction null
        }

        val item =
            conn.prepareStatement(
                """
                SELECT source_slot, item_id, quantity, vars
                FROM clan_party_drop_items
                WHERE batch_id = ? AND state = 0
                ORDER BY random()
                LIMIT 1
                FOR UPDATE
                """.trimIndent(),
            ).use { ps ->
                ps.setLong(1, batchId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        null
                    } else {
                        ClanEconomyItemRow(
                            slot = rs.getInt(1),
                            itemId = rs.getInt(2),
                            quantity = rs.getInt(3),
                            vars = rs.getInt(4),
                        )
                    }
                }
            } ?: return@transaction null

        // Non-stackable deposits are stored with quantity 1. Any quantity > 1 is therefore a
        // stackable row and may yield a random positive portion while leaving the remainder live.
        val claimQuantity =
            if (item.quantity <= 1) {
                1
            } else {
                Random.nextLong(1L, item.quantity.toLong() + 1L).toInt()
            }

        insertOperation(
            conn = conn,
            operationId = operationId,
            clanId = clanId,
            actorCharacterId = actorCharacterId,
            worldId = worldId,
            operationType = TYPE_PARTY_DROP_CLAIM,
            itemId = item.itemId,
            quantity = claimQuantity,
            vars = item.vars,
            sourceSlot = item.slot,
            targetSlot = -1,
            batchId = batchId,
        )

        if (claimQuantity == item.quantity) {
            conn.prepareStatement(
                """
                UPDATE clan_party_drop_items
                SET state = 2, reserved_by_operation = NULL
                WHERE batch_id = ? AND source_slot = ? AND state = 0
                """.trimIndent(),
            ).use { ps ->
                ps.setLong(1, batchId)
                ps.setInt(2, item.slot)
                check(ps.executeUpdate() == 1) { "party drop claim row disappeared" }
            }
        } else {
            conn.prepareStatement(
                """
                UPDATE clan_party_drop_items
                SET quantity = quantity - ?, reserved_by_operation = NULL
                WHERE batch_id = ? AND source_slot = ? AND state = 0 AND quantity > ?
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, claimQuantity)
                ps.setLong(2, batchId)
                ps.setInt(3, item.slot)
                ps.setInt(4, claimQuantity)
                check(ps.executeUpdate() == 1) { "party drop partial claim row disappeared" }
            }
        }

        setOperationState(conn, operationId, STATE_COMMITTED)
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    /**
     * Ends the visual drop window and returns every unclaimed quantity to its original chest slot.
     * The batch remains in history as FINISHED, but its transient drop rows are removed.
     */
    fun finishPartyDropWindow(clanId: Long, batchId: Long, worldId: Int): Boolean = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction false

        val state =
            conn.prepareStatement(
                "SELECT world_id, state FROM clan_party_drop_batches WHERE id = ? AND clan_id = ? FOR UPDATE",
            ).use { ps ->
                ps.setLong(1, batchId)
                ps.setLong(2, clanId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) null else rs.getInt(1) to rs.getInt(2)
                }
            } ?: return@transaction false

        if (state.second == BATCH_FINISHED) return@transaction true
        if (state.second != BATCH_ACTIVE || state.first != worldId) return@transaction false
        if (hasReservedBatchItem(conn, batchId)) return@transaction false

        restoreAvailableBatchToChest(conn, clanId, batchId)
        closeBatch(conn, batchId, BATCH_FINISHED)
        true
    }

    /**
     * OSRS-style Clan Hall destruction path. Coffer value is clan-owned and is intentionally kept;
     * Party Chest and active party-drop value are hall storage and are destroyed when the hall dies.
     */
    fun destroyHallPartyStorage(clanId: Long, worldId: Int): Boolean = transaction { conn ->
        advisoryLock(conn, clanId)
        if (!clanExists(conn, clanId)) return@transaction true

        // Never race an account-save-backed chest deposit / lever start or a legacy reserved claim.
        if (hasPreparedPartyMutation(conn, clanId)) return@transaction false

        val openBatch = loadOpenBatch(conn, clanId)
        if (openBatch != null && openBatch.worldId != worldId) {
            // Another world owns the live drop. Do not destroy its durable pool.
            return@transaction false
        }
        if (openBatch != null && hasReservedBatchItem(conn, openBatch.batchId)) return@transaction false

        conn.prepareStatement("DELETE FROM clan_party_chest_items WHERE clan_id = ?").use { ps ->
            ps.setLong(1, clanId)
            ps.executeUpdate()
        }

        if (openBatch != null) {
            conn.prepareStatement("DELETE FROM clan_party_drop_items WHERE batch_id = ?").use { ps ->
                ps.setLong(1, openBatch.batchId)
                ps.executeUpdate()
            }
            closeBatch(conn, openBatch.batchId, BATCH_ABORTED)
        }
        true
    }

    /** Commits an already prepared operation. Repeating this call is safe. */
    fun commit(operationId: UUID, actorCharacterId: Int): ClanEconomyOperationRow? = transaction { conn ->
        val initial = loadOperation(conn, operationId, forUpdate = false) ?: return@transaction null
        advisoryLock(conn, initial.clanId)
        val op = loadOperation(conn, operationId, forUpdate = true) ?: return@transaction null
        if (op.actorCharacterId != actorCharacterId) return@transaction null
        if (op.state == STATE_COMMITTED) return@transaction op
        if (op.state != STATE_PREPARED) return@transaction op

        when (op.operationType) {
            TYPE_COFFER_DEPOSIT -> {
                ensureCoffer(conn, op.clanId)
                val changed = conn.prepareStatement(
                    """
                    UPDATE clan_coffers
                    SET coin_balance = coin_balance + ?, updated_at = CURRENT_TIMESTAMP
                    WHERE clan_id = ? AND coin_balance + ? <= ?
                    """.trimIndent(),
                ).use { ps ->
                    ps.setInt(1, op.quantity); ps.setLong(2, op.clanId); ps.setInt(3, op.quantity); ps.setInt(4, Int.MAX_VALUE)
                    ps.executeUpdate()
                }
                check(changed == 1) { "coffer deposit overflow after reservation" }
            }
            TYPE_COFFER_WITHDRAW -> Unit // Balance was reserved at prepare.
            TYPE_PARTY_CHEST_DEPOSIT -> commitPartyDeposit(conn, op)
            TYPE_PARTY_DROP_START -> {
                val batchId = requireNotNull(op.batchId)
                val changed = conn.prepareStatement(
                    "UPDATE clan_party_drop_batches SET state = 1, expires_at = CURRENT_TIMESTAMP + INTERVAL '132 seconds', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND clan_id = ? AND state = 0",
                ).use { ps -> ps.setLong(1, batchId); ps.setLong(2, op.clanId); ps.executeUpdate() }
                check(changed == 1) { "party drop start batch not preparing" }
            }
            TYPE_PARTY_DROP_CLAIM -> Unit // Atomic claims are committed inside claimPartyDrop().
            else -> error("Unknown clan economy operation ${op.operationType}")
        }
        setOperationState(conn, operationId, STATE_COMMITTED)
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    /** Aborts an already prepared operation and reverses its Central-side reservation. Repeating is safe. */
    fun abort(operationId: UUID, actorCharacterId: Int): ClanEconomyOperationRow? = transaction { conn ->
        val initial = loadOperation(conn, operationId, forUpdate = false) ?: return@transaction null
        advisoryLock(conn, initial.clanId)
        val op = loadOperation(conn, operationId, forUpdate = true) ?: return@transaction null
        if (op.actorCharacterId != actorCharacterId) return@transaction null
        if (op.state == STATE_ABORTED || op.state == STATE_COMMITTED) return@transaction op

        when (op.operationType) {
            TYPE_COFFER_DEPOSIT -> Unit
            TYPE_COFFER_WITHDRAW -> {
                ensureCoffer(conn, op.clanId)
                conn.prepareStatement(
                    "UPDATE clan_coffers SET coin_balance = coin_balance + ?, updated_at = CURRENT_TIMESTAMP WHERE clan_id = ?",
                ).use { ps -> ps.setInt(1, op.quantity); ps.setLong(2, op.clanId); check(ps.executeUpdate() == 1) }
            }
            TYPE_PARTY_CHEST_DEPOSIT -> Unit
            TYPE_PARTY_DROP_START -> abortPartyDropStart(conn, op)
            TYPE_PARTY_DROP_CLAIM -> {
                val batchId = requireNotNull(op.batchId)
                conn.prepareStatement(
                    """
                    UPDATE clan_party_drop_items
                    SET state = 0, reserved_by_operation = NULL
                    WHERE batch_id = ? AND source_slot = ? AND state = 1 AND reserved_by_operation = ?
                    """.trimIndent(),
                ).use { ps ->
                    ps.setLong(1, batchId)
                    ps.setInt(2, op.sourceSlot)
                    ps.setObject(3, operationId)
                    check(ps.executeUpdate() == 1) { "party drop claim reservation missing during abort" }
                }
            }
            else -> error("Unknown clan economy operation ${op.operationType}")
        }
        setOperationState(conn, operationId, STATE_ABORTED)
        loadOperation(conn, operationId, forUpdate = false)!!
    }

    /** Returns true when deleting the clan would destroy current value or unresolved transfer state. */
    fun hasBlockingState(clanId: Long): Boolean = transaction { conn ->
        advisoryLock(conn, clanId)
        hasBlockingState(conn, clanId)
    }

    /**
     * Runs a clan deletion while holding the same global advisory lock used by economy prepares.
     * This closes the check/delete race: no transfer can become PREPARED between the empty-state
     * check and the clan row being deleted.
     */
    fun disbandIfClear(clanId: Long, disband: () -> Boolean): Boolean = transaction { conn ->
        advisoryLock(conn, clanId)
        if (hasBlockingState(conn, clanId)) return@transaction false
        disband()
    }

    private fun hasBlockingState(conn: Connection, clanId: Long): Boolean {
        val balance = conn.prepareStatement("SELECT coin_balance FROM clan_coffers WHERE clan_id = ?").use { ps ->
            ps.setLong(1, clanId); ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
        if (balance > 0L) return true
        if (exists(conn, "SELECT 1 FROM clan_party_chest_items WHERE clan_id = ? LIMIT 1", clanId)) return true
        if (exists(conn, "SELECT 1 FROM clan_party_drop_batches WHERE clan_id = ? AND state IN (0,1) LIMIT 1", clanId)) return true
        return exists(conn, "SELECT 1 FROM clan_economy_operations WHERE clan_id = ? AND state = 0 LIMIT 1", clanId)
    }

    private fun commitPartyDeposit(conn: Connection, op: ClanEconomyOperationRow) {
        val current = conn.prepareStatement(
            "SELECT item_id, quantity, vars FROM clan_party_chest_items WHERE clan_id = ? AND slot = ? FOR UPDATE",
        ).use { ps ->
            ps.setLong(1, op.clanId); ps.setInt(2, op.targetSlot)
            ps.executeQuery().use { rs -> if (rs.next()) Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3)) else null }
        }
        if (current == null) {
            conn.prepareStatement(
                "INSERT INTO clan_party_chest_items (clan_id, slot, item_id, quantity, vars) VALUES (?, ?, ?, ?, ?)",
            ).use { ps ->
                ps.setLong(1, op.clanId); ps.setInt(2, op.targetSlot); ps.setInt(3, op.itemId); ps.setInt(4, op.quantity); ps.setInt(5, op.vars)
                check(ps.executeUpdate() == 1)
            }
        } else {
            check(current.first == op.itemId && current.third == op.vars) { "party deposit target changed" }
            val next = current.second.toLong() + op.quantity.toLong()
            check(next <= Int.MAX_VALUE.toLong()) { "party deposit stack overflow" }
            conn.prepareStatement(
                "UPDATE clan_party_chest_items SET quantity = ? WHERE clan_id = ? AND slot = ?",
            ).use { ps -> ps.setLong(1, next); ps.setLong(2, op.clanId); ps.setInt(3, op.targetSlot); check(ps.executeUpdate() == 1) }
        }
    }

    private fun abortPartyDropStart(conn: Connection, op: ClanEconomyOperationRow) {
        val batchId = requireNotNull(op.batchId)
        val items = loadBatchItems(conn, batchId)
        // Deposits are blocked while a batch is preparing/active, so original source slots must be free.
        items.forEach { item ->
            conn.prepareStatement(
                "INSERT INTO clan_party_chest_items (clan_id, slot, item_id, quantity, vars) VALUES (?, ?, ?, ?, ?)",
            ).use { ps ->
                ps.setLong(1, op.clanId); ps.setInt(2, item.slot); ps.setInt(3, item.itemId); ps.setInt(4, item.quantity); ps.setInt(5, item.vars)
                check(ps.executeUpdate() == 1)
            }
        }
        conn.prepareStatement(
            "UPDATE clan_party_drop_batches SET state = 3, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND state = 0",
        ).use { ps -> ps.setLong(1, batchId); check(ps.executeUpdate() == 1) }
    }

    private fun finishExpiredDropBatch(conn: Connection, clanId: Long) {
        val expiredBatchId =
            conn.prepareStatement(
                """
                SELECT id
                FROM clan_party_drop_batches
                WHERE clan_id = ? AND state = 1 AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP
                ORDER BY id DESC
                LIMIT 1
                FOR UPDATE
                """.trimIndent(),
            ).use { ps ->
                ps.setLong(1, clanId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
            } ?: return

        if (hasReservedBatchItem(conn, expiredBatchId)) return
        restoreAvailableBatchToChest(conn, clanId, expiredBatchId)
        closeBatch(conn, expiredBatchId, BATCH_FINISHED)
    }

    private fun restoreAvailableBatchToChest(conn: Connection, clanId: Long, batchId: Long) {
        val items =
            conn.prepareStatement(
                """
                SELECT source_slot, item_id, quantity, vars
                FROM clan_party_drop_items
                WHERE batch_id = ? AND state = 0
                ORDER BY source_slot
                FOR UPDATE
                """.trimIndent(),
            ).use { ps ->
                ps.setLong(1, batchId)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(ClanEconomyItemRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)))
                        }
                    }
                }
            }

        items.forEach { item ->
            conn.prepareStatement(
                "INSERT INTO clan_party_chest_items (clan_id, slot, item_id, quantity, vars) VALUES (?, ?, ?, ?, ?)",
            ).use { ps ->
                ps.setLong(1, clanId)
                ps.setInt(2, item.slot)
                ps.setInt(3, item.itemId)
                ps.setInt(4, item.quantity)
                ps.setInt(5, item.vars)
                check(ps.executeUpdate() == 1) { "party drop return slot ${item.slot} was not free" }
            }
        }

        // Rows in state=2 are already claimed; rows in state=0 were just returned above.
        conn.prepareStatement("DELETE FROM clan_party_drop_items WHERE batch_id = ?").use { ps ->
            ps.setLong(1, batchId)
            ps.executeUpdate()
        }
    }

    private fun hasReservedBatchItem(conn: Connection, batchId: Long): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM clan_party_drop_items WHERE batch_id = ? AND state = 1 LIMIT 1",
        ).use { ps ->
            ps.setLong(1, batchId)
            ps.executeQuery().use { it.next() }
        }

    private fun closeBatch(conn: Connection, batchId: Long, state: Int) {
        conn.prepareStatement(
            "UPDATE clan_party_drop_batches SET state = ?, expires_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND state IN (0, 1)",
        ).use { ps ->
            ps.setInt(1, state)
            ps.setLong(2, batchId)
            ps.executeUpdate()
        }
    }

    private fun snapshot(conn: Connection, clanId: Long): ClanEconomyStateRow {
        val balance = conn.prepareStatement("SELECT coin_balance FROM clan_coffers WHERE clan_id = ?").use { ps ->
            ps.setLong(1, clanId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        return ClanEconomyStateRow(clanId, balance, loadChest(conn, clanId), loadOpenBatch(conn, clanId))
    }

    private fun loadChest(conn: Connection, clanId: Long): List<ClanEconomyItemRow> =
        conn.prepareStatement(
            "SELECT slot, item_id, quantity, vars FROM clan_party_chest_items WHERE clan_id = ? ORDER BY slot",
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(ClanEconomyItemRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4))) } }
        }

    private fun loadBatchItems(conn: Connection, batchId: Long): List<ClanEconomyItemRow> =
        conn.prepareStatement(
            "SELECT source_slot, item_id, quantity, vars FROM clan_party_drop_items WHERE batch_id = ? ORDER BY source_slot",
        ).use { ps ->
            ps.setLong(1, batchId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(ClanEconomyItemRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4))) } }
        }

    private fun loadOpenBatch(conn: Connection, clanId: Long): ClanEconomyBatchRow? =
        conn.prepareStatement(
            """
            SELECT b.id, b.clan_id, b.world_id, b.state,
                   (SELECT count(*) FROM clan_party_drop_items i WHERE i.batch_id = b.id AND i.state = 0) AS available_count
            FROM clan_party_drop_batches b
            WHERE b.clan_id = ? AND b.state IN (0,1)
            ORDER BY b.id DESC LIMIT 1
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    ClanEconomyBatchRow(
                        batchId = rs.getLong(1),
                        clanId = rs.getLong(2),
                        worldId = rs.getInt(3),
                        state = rs.getInt(4),
                        availableItemCount = rs.getInt(5),
                    )
                } else {
                    null
                }
            }
        }

    private fun hasOpenDropBatch(conn: Connection, clanId: Long): Boolean = loadOpenBatch(conn, clanId) != null

    private fun hasPreparedPartyMutation(conn: Connection, clanId: Long): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM clan_economy_operations WHERE clan_id = ? AND state = 0 AND operation_type IN (3,4,5) LIMIT 1",
        ).use { ps -> ps.setLong(1, clanId); ps.executeQuery().use { it.next() } }

    private fun loadPreparedPartyDeposits(conn: Connection, clanId: Long): List<ClanEconomyOperationRow> =
        conn.prepareStatement(
            """
            SELECT operation_id, clan_id, actor_character_id, world_id, operation_type, state,
                   item_id, quantity, item_vars, source_slot, target_slot, batch_id
            FROM clan_economy_operations
            WHERE clan_id = ? AND state = 0 AND operation_type = 3
            ORDER BY created_at, operation_id
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toOperation()) } }
        }

    private fun ensureCoffer(conn: Connection, clanId: Long) {
        conn.prepareStatement(
            "INSERT INTO clan_coffers (clan_id, coin_balance) VALUES (?, 0) ON CONFLICT (clan_id) DO NOTHING",
        ).use { ps -> ps.setLong(1, clanId); ps.executeUpdate() }
    }

    private fun insertOperation(
        conn: Connection,
        operationId: UUID,
        clanId: Long,
        actorCharacterId: Int,
        worldId: Int,
        operationType: Int,
        itemId: Int,
        quantity: Int,
        vars: Int,
        sourceSlot: Int,
        targetSlot: Int,
        batchId: Long?,
    ) {
        conn.prepareStatement(
            """
            INSERT INTO clan_economy_operations
                (operation_id, clan_id, actor_character_id, world_id, operation_type, state,
                 item_id, quantity, item_vars, source_slot, target_slot, batch_id)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, operationId); ps.setLong(2, clanId); ps.setInt(3, actorCharacterId); ps.setInt(4, worldId)
            ps.setInt(5, operationType); ps.setInt(6, itemId); ps.setInt(7, quantity); ps.setInt(8, vars)
            ps.setInt(9, sourceSlot); ps.setInt(10, targetSlot)
            if (batchId == null) ps.setNull(11, Types.BIGINT) else ps.setLong(11, batchId)
            check(ps.executeUpdate() == 1)
        }
    }

    private fun existingOrNull(conn: Connection, operationId: UUID): ClanEconomyOperationRow? =
        loadOperation(conn, operationId, forUpdate = false)

    private fun loadOperation(conn: Connection, operationId: UUID, forUpdate: Boolean): ClanEconomyOperationRow? {
        val sql = buildString {
            append("SELECT operation_id, clan_id, actor_character_id, world_id, operation_type, state, item_id, quantity, item_vars, source_slot, target_slot, batch_id FROM clan_economy_operations WHERE operation_id = ?")
            if (forUpdate) append(" FOR UPDATE")
        }
        return conn.prepareStatement(sql).use { ps ->
            ps.setObject(1, operationId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toOperation() else null }
        }
    }

    private fun setOperationState(conn: Connection, operationId: UUID, state: Int) {
        conn.prepareStatement(
            "UPDATE clan_economy_operations SET state = ?, updated_at = CURRENT_TIMESTAMP WHERE operation_id = ?",
        ).use { ps -> ps.setInt(1, state); ps.setObject(2, operationId); check(ps.executeUpdate() == 1) }
    }

    private fun advisoryLock(conn: Connection, clanId: Long) {
        conn.prepareStatement("SELECT pg_advisory_xact_lock(?)").use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { check(it.next()) }
        }
    }

    private fun clanExists(conn: Connection, clanId: Long): Boolean =
        conn.prepareStatement("SELECT 1 FROM clans WHERE id = ?").use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { it.next() }
        }

    private fun exists(conn: Connection, sql: String, clanId: Long): Boolean =
        conn.prepareStatement(sql).use { ps -> ps.setLong(1, clanId); ps.executeQuery().use { it.next() } }

    private fun ResultSet.toOperation(): ClanEconomyOperationRow = ClanEconomyOperationRow(
        operationId = getObject("operation_id", UUID::class.java),
        clanId = getLong("clan_id"),
        actorCharacterId = getInt("actor_character_id"),
        worldId = getInt("world_id"),
        operationType = getInt("operation_type"),
        state = getInt("state"),
        itemId = getInt("item_id"),
        quantity = getInt("quantity"),
        vars = getInt("item_vars"),
        sourceSlot = getInt("source_slot"),
        targetSlot = getInt("target_slot"),
        batchId = getLong("batch_id").let { if (wasNull()) null else it },
    )

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { conn ->
        val oldAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            val value = block(conn)
            conn.commit()
            value
        } catch (t: Throwable) {
            runCatching { conn.rollback() }
            throw t
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    private companion object {
        const val PARTY_CHEST_SLOTS = 216
        const val PARTY_DROP_LEVER_COST = 1_000

        const val TYPE_COFFER_DEPOSIT = 1
        const val TYPE_COFFER_WITHDRAW = 2
        const val TYPE_PARTY_CHEST_DEPOSIT = 3
        const val TYPE_PARTY_DROP_START = 4
        const val TYPE_PARTY_DROP_CLAIM = 5

        const val STATE_PREPARED = 0
        const val STATE_COMMITTED = 1
        const val STATE_ABORTED = 2

        const val BATCH_ACTIVE = 1
        const val BATCH_FINISHED = 2
        const val BATCH_ABORTED = 3
    }
}