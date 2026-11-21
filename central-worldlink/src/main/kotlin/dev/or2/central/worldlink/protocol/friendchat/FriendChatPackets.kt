package dev.or2.central.worldlink.protocol.friendchat

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.FrameWriter
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame

object FriendChatPackets {
    fun decodeCharacter(input: FrameReader): FriendChatCharacterPayload {
        val out = FriendChatCharacterPayload(input.readInt())
        input.requireFullyConsumed()
        return out
    }

    fun decodeResolve(input: FrameReader): FriendChatResolvePayload {
        val out = FriendChatResolvePayload(input.readInt(), input.readUtf8LenPrefixed())
        input.requireFullyConsumed()
        return out
    }

    fun decodeSettings(input: FrameReader): FriendChatSettingsPayload {
        val out = FriendChatSettingsPayload(
            characterId = input.readInt(),
            displayName = input.readUtf8LenPrefixed(),
            enterRank = input.readSignedByte(),
            talkRank = input.readSignedByte(),
            kickRank = input.readSignedByte(),
            lootShareRank = input.readSignedByte(),
            coinShareEnabled = input.readUnsignedByte() != 0,
        )
        input.requireFullyConsumed()
        return out
    }

    fun decodeFieldMutation(input: FrameReader): FriendChatFieldMutation =
        FriendChatFieldMutation(input.readInt(), input.readUnsignedByte(), input.readUtf8LenPrefixed()).also {
            input.requireFullyConsumed()
            require(it.characterId > 0 && it.field in 1..6 && it.value.toByteArray(Charsets.UTF_8).size <= 96)
        }

    fun encodeFieldMutationOk(reply: FriendChatFieldMutationReply): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_FIELD_OK) {
            writeLong(reply.channelId)
            writeByte(if (reply.changed) 1 else 0)
        }

    fun decodeFieldMutationOk(input: FrameReader): FriendChatFieldMutationReply =
        FriendChatFieldMutationReply(input.readLong(), input.readUnsignedByte() != 0).also {
            input.requireFullyConsumed()
        }

    fun decodeRank(input: FrameReader): FriendChatRankPayload {
        val out = FriendChatRankPayload(
            characterId = input.readInt(),
            ownerName = input.readUtf8LenPrefixed(),
            targetName = input.readUtf8LenPrefixed(),
            rank = input.readSignedByte(),
        )
        input.requireFullyConsumed()
        return out
    }


    fun decodeKick(input: FrameReader): FriendChatKickPayload {
        val out = FriendChatKickPayload(input.readInt(), input.readLong(), input.readUtf8LenPrefixed())
        input.requireFullyConsumed()
        require(out.characterId > 0 && out.channelId > 0L &&
                out.targetName.isNotBlank() &&
                out.targetName.toByteArray(Charsets.UTF_8).size <= FriendChatLimits.OWNER_NAME_MAX_UTF8)
        return out
    }

    fun encodeSyncOk(snapshot: FriendChatSnapshot): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC_OK) { writeSnapshot(snapshot) }

    fun decodeSyncOk(input: FrameReader): FriendChatSnapshot {
        val out = input.readSnapshot()
        input.requireFullyConsumed()
        return out
    }

    fun encodeSyncFail(reason: Int): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC_FAIL) { writeByte(reason) }

    fun decodeSyncFail(input: FrameReader): Int {
        val out = input.readUnsignedByte()
        input.requireFullyConsumed()
        return out
    }

    fun encodeResolveOk(payload: FriendChatResolveReply): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE_OK) {
            writeSnapshot(payload.snapshot)
            writeByte(payload.resolvedRank)
        }

    fun decodeResolveOk(input: FrameReader): FriendChatResolveReply {
        val out = FriendChatResolveReply(input.readSnapshot(), input.readSignedByte())
        input.requireFullyConsumed()
        return out
    }

    fun encodeRestoreOk(payload: FriendChatRestoreReply): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE_OK) {
            writeLong(payload.channelId)
            writeUtf8Truncated(payload.ownerName, FriendChatLimits.OWNER_NAME_MAX_UTF8)
        }

    fun decodeRestoreOk(input: FrameReader): FriendChatRestoreReply {
        val out = FriendChatRestoreReply(
            channelId = input.readLong(),
            ownerName = input.readUtf8LenPrefixed(),
        )
        input.requireFullyConsumed()
        return out
    }

    fun encodeOk(payload: FriendChatMutationReply): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_OK) {
            writeByte(payload.action)
            writeLong(payload.channelId)
        }

    fun decodeMutationReply(input: FrameReader): FriendChatMutationReply {
        val out = FriendChatMutationReply(input.readUnsignedByte(), input.readLong())
        input.requireFullyConsumed()
        return out
    }

    fun encodeFail(payload: FriendChatMutationFail): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_FAIL) {
            writeByte(payload.action)
            writeByte(payload.reason)
        }

    fun decodeMutationFail(input: FrameReader): FriendChatMutationFail {
        val out = FriendChatMutationFail(input.readUnsignedByte(), input.readUnsignedByte())
        input.requireFullyConsumed()
        return out
    }

    private fun FrameWriter.writeSnapshot(s: FriendChatSnapshot) {
        writeLong(s.channelId)
        writeInt(s.ownerCharacterId)
        writeUtf8Truncated(s.ownerNameKey, FriendChatLimits.OWNER_NAME_MAX_UTF8)
        writeUtf8Truncated(s.displayName, FriendChatLimits.DISPLAY_NAME_MAX_UTF8)
        writeByte(s.channelType)
        writeInt(s.groupingActivityId)
        writeInt(s.suggestedWorld)
        writeByte(s.enterRank)
        writeByte(s.talkRank)
        writeByte(s.kickRank)
        writeByte(s.lootShareRank)
        writeByte(if (s.coinShareEnabled) 1 else 0)
        writeShort(s.maxMembers)
    }

    private fun FrameReader.readSnapshot(): FriendChatSnapshot = FriendChatSnapshot(
        channelId = readLong(),
        ownerCharacterId = readInt(),
        ownerNameKey = readUtf8LenPrefixed(),
        displayName = readUtf8LenPrefixed(),
        channelType = readUnsignedByte(),
        groupingActivityId = readInt(),
        suggestedWorld = readInt(),
        enterRank = readSignedByte(),
        talkRank = readSignedByte(),
        kickRank = readSignedByte(),
        lootShareRank = readSignedByte(),
        coinShareEnabled = readUnsignedByte() != 0,
        maxMembers = readUnsignedShort(),
    )
}

object GameToCentralFriendChatPackets {
    fun sync(characterId: Int): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC) { writeInt(characterId) }

    fun resolve(characterId: Int, ownerName: String): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE) {
            writeInt(characterId)
            writeUtf8Truncated(ownerName, FriendChatLimits.OWNER_NAME_MAX_UTF8)
        }

    fun setSettings(payload: FriendChatSettingsPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_SETTINGS) {
            writeInt(payload.characterId)
            writeUtf8Truncated(payload.displayName, FriendChatLimits.DISPLAY_NAME_MAX_UTF8)
            writeByte(payload.enterRank)
            writeByte(payload.talkRank)
            writeByte(payload.kickRank)
            writeByte(payload.lootShareRank)
            writeByte(if (payload.coinShareEnabled) 1 else 0)
        }

    fun setField(payload: FriendChatFieldMutation): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_FIELD) {
            writeInt(payload.characterId)
            writeByte(payload.field)
            writeUtf8Truncated(payload.value, FriendChatLimits.DISPLAY_NAME_MAX_UTF8)
        }

    fun setRank(payload: FriendChatRankPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_RANK) {
            writeInt(payload.characterId)
            writeUtf8Truncated(payload.ownerName, FriendChatLimits.OWNER_NAME_MAX_UTF8)
            writeUtf8Truncated(payload.targetName, FriendChatLimits.OWNER_NAME_MAX_UTF8)
            writeByte(payload.rank)
        }

    fun kick(payload: FriendChatKickPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_KICK) {
            writeInt(payload.characterId)
            writeLong(payload.channelId)
            writeUtf8Truncated(payload.targetName, FriendChatLimits.OWNER_NAME_MAX_UTF8)
        }

    fun restore(characterId: Int): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE) { writeInt(characterId) }

    fun clearRestore(characterId: Int): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_FRIENDCHAT_CLEAR_RESTORE) { writeInt(characterId) }
}