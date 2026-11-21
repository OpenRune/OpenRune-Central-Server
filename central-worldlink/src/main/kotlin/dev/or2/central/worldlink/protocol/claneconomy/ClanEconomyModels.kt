package dev.or2.central.worldlink.protocol.claneconomy

/** 128-bit idempotency key. The two longs are UUID raw bits and are safe to retry verbatim. */
data class ClanEconomyOperationId(val high: Long, val low: Long)

data class ClanEconomyReceiptId(val high: Long, val low: Long)

data class ClanEconomyItem(
    val slot: Int,
    val itemId: Int,
    val quantity: Int,
    val vars: Int,
)

data class ClanPartyDropBatchSnapshot(
    val batchId: Long,
    val worldId: Int,
    val state: Int,
    val availableItemCount: Int,
)

data class ClanEconomySnapshot(
    val clanId: Long,
    val cofferBalance: Int,
    val partyChest: List<ClanEconomyItem>,
    val activeDrop: ClanPartyDropBatchSnapshot?,
)

data class ClanEconomySyncPayload(
    val characterId: Int,
    val clanId: Long,
    val durableReceipts: List<ClanEconomyReceiptId>,
)

data class ClanEconomySyncReply(
    val ok: Boolean,
    val reason: Int,
    val snapshot: ClanEconomySnapshot?,
    val resolvedReceipts: List<ClanEconomyReceiptId> = emptyList(),
)

data class ClanEconomyPreparePayload(
    val characterId: Int,
    val clanId: Long,
    val operationId: ClanEconomyOperationId,
    val operationType: Int,
    val itemId: Int = 0,
    val quantity: Int = 0,
    val vars: Int = 0,
    val sourceSlot: Int = -1,
    val stackable: Boolean = false,
    val referenceId: Long = 0L,
)

data class ClanPartyDropPreparePayload(
    val characterId: Int,
    val clanId: Long,
    val operationId: ClanEconomyOperationId,
    val action: Int = ClanPartyDropAction.START,
    val batchId: Long = 0L,
)

data class ClanEconomyFinalizePayload(
    val characterId: Int,
    val operationId: ClanEconomyOperationId,
)

data class ClanEconomyOperationReply(
    val ok: Boolean,
    val reason: Int,
    val requestOpcode: Int,
    val operationId: ClanEconomyOperationId,
    val clanId: Long,
    val operationType: Int,
    val itemId: Int = 0,
    val quantity: Int = 0,
    val vars: Int = 0,
    val targetSlot: Int = -1,
    val batchId: Long = 0L,
    val batchItems: List<ClanEconomyItem> = emptyList(),
)

object ClanPartyDropAction {
    /** Player-authorized lever pull. */
    const val START: Int = 0

    /** World-runtime expiry: return every unclaimed quantity to the Party Chest. */
    const val FINISH_WINDOW: Int = 1

    /** Clan Hall instance destruction: delete Party Chest + active drop-pool value. */
    const val DESTROY_HALL: Int = 2
}

object ClanEconomyOperationType {
    const val COFFER_DEPOSIT: Int = 1
    const val COFFER_WITHDRAW: Int = 2
    const val PARTY_CHEST_DEPOSIT: Int = 3
    const val PARTY_DROP_START: Int = 4
    const val PARTY_DROP_CLAIM: Int = 5
}

object ClanEconomyOperationState {
    const val PREPARED: Int = 0
    const val COMMITTED: Int = 1
    const val ABORTED: Int = 2
}

object ClanPartyDropBatchState {
    const val PREPARING: Int = 0
    const val ACTIVE: Int = 1
    const val FINISHED: Int = 2
    const val ABORTED: Int = 3
}

object ClanEconomyFailure {
    const val NOT_ALLOWED: Int = 1
    const val NOT_LOGGED_IN: Int = 2
    const val CLAN_NOT_FOUND: Int = 3
    const val INVALID_REQUEST: Int = 4
    const val INSUFFICIENT_FUNDS: Int = 5
    const val NO_SPACE: Int = 6
    const val EMPTY: Int = 7
    const val DROP_ALREADY_ACTIVE: Int = 8
    const val DROP_NOT_ACTIVE: Int = 9
    const val CONFLICT: Int = 10
    const val UNAVAILABLE: Int = 11
}