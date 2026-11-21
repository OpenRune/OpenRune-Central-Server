package dev.or2.central.worldlink.protocol.claneconomy

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.FrameWriter
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame

object ClanEconomyPackets {
    fun encodeSync(payload: ClanEconomySyncPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_ECONOMY_SYNC) {
            writeInt(payload.characterId)
            writeLong(payload.clanId)
            val receipts = payload.durableReceipts.distinct().take(ClanEconomyLimits.MAX_DURABLE_RECEIPTS)
            writeShort(receipts.size)
            receipts.forEach { writeLong(it.high); writeLong(it.low) }
        }

    fun decodeSync(input: FrameReader): ClanEconomySyncPayload {
        val characterId = input.readInt()
        val clanId = input.readLong()
        val count = input.readUnsignedShort()
        if (count > ClanEconomyLimits.MAX_DURABLE_RECEIPTS) throw PacketDecodeException("too many economy receipts")
        val receipts = List(count) { ClanEconomyReceiptId(input.readLong(), input.readLong()) }
        input.requireFullyConsumed()
        return ClanEconomySyncPayload(characterId, clanId, receipts)
    }

    fun encodeSyncReply(payload: ClanEconomySyncReply): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_ECONOMY_SYNC_REPLY) {
            writeByte(if (payload.ok) 1 else 0)
            writeByte(payload.reason)
            writeByte(if (payload.snapshot != null) 1 else 0)
            payload.snapshot?.let { writeSnapshot(it) }
            val receipts = payload.resolvedReceipts.distinct().take(ClanEconomyLimits.MAX_DURABLE_RECEIPTS)
            writeShort(receipts.size)
            receipts.forEach { writeLong(it.high); writeLong(it.low) }
        }

    fun decodeSyncReply(input: FrameReader): ClanEconomySyncReply {
        val ok = input.readUnsignedByte() != 0
        val reason = input.readUnsignedByte()
        val snapshot = if (input.readUnsignedByte() != 0) input.readSnapshot() else null
        val receiptCount = input.readUnsignedShort()
        if (receiptCount > ClanEconomyLimits.MAX_DURABLE_RECEIPTS) throw PacketDecodeException("too many resolved economy receipts")
        val receipts = List(receiptCount) { ClanEconomyReceiptId(input.readLong(), input.readLong()) }
        input.requireFullyConsumed()
        return ClanEconomySyncReply(ok, reason, snapshot, receipts)
    }

    fun encodePrepare(payload: ClanEconomyPreparePayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_ECONOMY_PREPARE) {
            writeInt(payload.characterId)
            writeLong(payload.clanId)
            writeOperationId(payload.operationId)
            writeByte(payload.operationType)
            writeInt(payload.itemId)
            writeInt(payload.quantity)
            writeInt(payload.vars)
            writeInt(payload.sourceSlot)
            writeByte(if (payload.stackable) 1 else 0)
            writeLong(payload.referenceId)
        }

    fun decodePrepare(input: FrameReader): ClanEconomyPreparePayload {
        val value = ClanEconomyPreparePayload(
            characterId = input.readInt(),
            clanId = input.readLong(),
            operationId = input.readOperationId(),
            operationType = input.readUnsignedByte(),
            itemId = input.readInt(),
            quantity = input.readInt(),
            vars = input.readInt(),
            sourceSlot = input.readInt(),
            stackable = input.readUnsignedByte() != 0,
            referenceId = input.readLong(),
        )
        input.requireFullyConsumed()
        return value
    }

    fun encodePartyDropPrepare(payload: ClanPartyDropPreparePayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_PARTY_DROP_PREPARE) {
            writeInt(payload.characterId)
            writeLong(payload.clanId)
            writeOperationId(payload.operationId)
            writeByte(payload.action)
            writeLong(payload.batchId)
        }

    fun decodePartyDropPrepare(input: FrameReader): ClanPartyDropPreparePayload {
        val value =
            ClanPartyDropPreparePayload(
                characterId = input.readInt(),
                clanId = input.readLong(),
                operationId = input.readOperationId(),
                action = input.readUnsignedByte(),
                batchId = input.readLong(),
            )
        input.requireFullyConsumed()
        return value
    }

    fun encodeFinalize(opcode: Int, payload: ClanEconomyFinalizePayload): ByteArray =
        outboundFrame(opcode) {
            writeInt(payload.characterId)
            writeOperationId(payload.operationId)
        }

    fun decodeFinalize(input: FrameReader): ClanEconomyFinalizePayload {
        val value = ClanEconomyFinalizePayload(input.readInt(), input.readOperationId())
        input.requireFullyConsumed()
        return value
    }

    fun encodeOperationReply(payload: ClanEconomyOperationReply): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CLAN_ECONOMY_RESULT) {
            writeByte(if (payload.ok) 1 else 0)
            writeByte(payload.reason)
            writeByte(payload.requestOpcode)
            writeOperationId(payload.operationId)
            writeLong(payload.clanId)
            writeByte(payload.operationType)
            writeInt(payload.itemId)
            writeInt(payload.quantity)
            writeInt(payload.vars)
            writeInt(payload.targetSlot)
            writeLong(payload.batchId)
            val items = payload.batchItems.take(ClanEconomyLimits.PARTY_CHEST_SLOTS)
            writeByte(items.size)
            items.forEach { writeItem(it) }
        }

    fun decodeOperationReply(input: FrameReader): ClanEconomyOperationReply {
        val ok = input.readUnsignedByte() != 0
        val reason = input.readUnsignedByte()
        val requestOpcode = input.readUnsignedByte()
        val operationId = input.readOperationId()
        val clanId = input.readLong()
        val operationType = input.readUnsignedByte()
        val itemId = input.readInt()
        val quantity = input.readInt()
        val vars = input.readInt()
        val targetSlot = input.readInt()
        val batchId = input.readLong()
        val count = input.readUnsignedByte()
        if (count > ClanEconomyLimits.PARTY_CHEST_SLOTS) throw PacketDecodeException("too many party drop items")
        val items = List(count) { input.readItem() }
        input.requireFullyConsumed()
        return ClanEconomyOperationReply(ok, reason, requestOpcode, operationId, clanId, operationType, itemId, quantity, vars, targetSlot, batchId, items)
    }

    private fun FrameWriter.writeOperationId(id: ClanEconomyOperationId) {
        writeLong(id.high)
        writeLong(id.low)
    }

    private fun FrameReader.readOperationId(): ClanEconomyOperationId = ClanEconomyOperationId(readLong(), readLong())

    private fun FrameWriter.writeItem(item: ClanEconomyItem) {
        writeByte(item.slot)
        writeInt(item.itemId)
        writeInt(item.quantity)
        writeInt(item.vars)
    }

    private fun FrameReader.readItem(): ClanEconomyItem =
        ClanEconomyItem(readUnsignedByte(), readInt(), readInt(), readInt())

    private fun FrameWriter.writeSnapshot(snapshot: ClanEconomySnapshot) {
        writeLong(snapshot.clanId)
        writeInt(snapshot.cofferBalance)
        val chest = snapshot.partyChest.take(ClanEconomyLimits.PARTY_CHEST_SLOTS)
        writeByte(chest.size)
        chest.forEach { writeItem(it) }
        val batch = snapshot.activeDrop
        writeByte(if (batch != null) 1 else 0)
        if (batch != null) {
            writeLong(batch.batchId)
            writeInt(batch.worldId)
            writeByte(batch.state)
            writeByte(batch.availableItemCount)
        }
    }

    private fun FrameReader.readSnapshot(): ClanEconomySnapshot {
        val clanId = readLong()
        val balance = readInt()
        val count = readUnsignedByte()
        if (count > ClanEconomyLimits.PARTY_CHEST_SLOTS) throw PacketDecodeException("too many chest items")
        val chest = List(count) { readItem() }
        val batch =
            if (readUnsignedByte() != 0) {
                ClanPartyDropBatchSnapshot(readLong(), readInt(), readUnsignedByte(), readUnsignedByte())
            } else {
                null
            }
        return ClanEconomySnapshot(clanId, balance, chest, batch)
    }
}

object GameToCentralClanEconomyPackets {
    fun sync(payload: ClanEconomySyncPayload): ByteArray = ClanEconomyPackets.encodeSync(payload)
    fun prepare(payload: ClanEconomyPreparePayload): ByteArray = ClanEconomyPackets.encodePrepare(payload)
    fun preparePartyDrop(payload: ClanPartyDropPreparePayload): ByteArray = ClanEconomyPackets.encodePartyDropPrepare(payload)
    fun commit(payload: ClanEconomyFinalizePayload): ByteArray = ClanEconomyPackets.encodeFinalize(WorldOpcodes.OP_WORLD_CLAN_ECONOMY_COMMIT, payload)
    fun abort(payload: ClanEconomyFinalizePayload): ByteArray = ClanEconomyPackets.encodeFinalize(WorldOpcodes.OP_WORLD_CLAN_ECONOMY_ABORT, payload)
}