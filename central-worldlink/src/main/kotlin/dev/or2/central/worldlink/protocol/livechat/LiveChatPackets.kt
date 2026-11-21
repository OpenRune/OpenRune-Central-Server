package dev.or2.central.worldlink.protocol.livechat

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.FrameWriter
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame

/** The same codecs are used by Central and every world. No client protocol packets live here. */
object LiveChatPackets {
    fun encodeJoin(request: LiveChatJoinRequest): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_LIVE_CHAT_JOIN) {
            writeInt(request.characterId); writeByte(request.kind); writeLong(request.channelId)
        }

    fun decodeJoin(input: FrameReader): LiveChatJoinRequest =
        LiveChatJoinRequest(input.readInt(), input.readUnsignedByte(), input.readLong()).also {
            input.requireFullyConsumed()
            require(it.characterId > 0 && LiveChatKind.valid(it.kind) && it.channelId > 0L)
        }

    fun encodeLeave(request: LiveChatLeaveRequest): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_LIVE_CHAT_LEAVE) {
            writeInt(request.characterId); writeByte(request.kind)
        }

    fun decodeLeave(input: FrameReader): LiveChatLeaveRequest =
        LiveChatLeaveRequest(input.readInt(), input.readUnsignedByte()).also {
            input.requireFullyConsumed()
            require(it.characterId > 0 && LiveChatKind.valid(it.kind))
        }

    fun encodeMessage(request: LiveChatMessageRequest): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_LIVE_CHAT_MESSAGE) {
            writeInt(request.characterId); writeByte(request.kind); writeByte(request.senderCrown)
            writeUtf8Truncated(request.message, LiveChatLimits.MAX_MESSAGE_BYTES)
        }

    fun decodeMessage(input: FrameReader): LiveChatMessageRequest =
        LiveChatMessageRequest(input.readInt(), input.readUnsignedByte(), input.readUnsignedByte(), input.readUtf8LenPrefixed()).also {
            input.requireFullyConsumed()
            require(it.characterId > 0 && LiveChatKind.valid(it.kind) && it.senderCrown in 0..255)
            require(it.message.isNotBlank() && it.message.toByteArray(Charsets.UTF_8).size <= LiveChatLimits.MAX_MESSAGE_BYTES)
        }

    fun encodeResult(reply: LiveChatResult): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_LIVE_CHAT_RESULT) {
            writeByte(reply.action); writeByte(reply.status); writeByte(reply.kind); writeLong(reply.channelId)
            writeByte(if (reply.roster == null) 0 else 1)
            reply.roster?.let { writeRoster(it) }
        }

    fun decodeResult(input: FrameReader): LiveChatResult {
        val action = input.readUnsignedByte()
        val status = input.readUnsignedByte()
        val kind = input.readUnsignedByte()
        val channelId = input.readLong()
        val roster = if (input.readUnsignedByte() != 0) input.readRoster() else null
        input.requireFullyConsumed()
        require(action in LiveChatAction.JOIN..LiveChatAction.MESSAGE && LiveChatKind.valid(kind))
        require(roster == null || roster.kind == kind && roster.channelId == channelId)
        return LiveChatResult(action, status, kind, channelId, roster)
    }

    fun encodeRoster(push: LiveChatRosterPush): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_LIVE_CHAT_ROSTER) { writeRoster(push) }

    fun decodeRoster(input: FrameReader): LiveChatRosterPush = input.readRoster().also { input.requireFullyConsumed() }

    fun encodeMessagePush(push: LiveChatMessagePush): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_LIVE_CHAT_MESSAGE) {
            writeByte(push.kind); writeLong(push.channelId); writeInt(push.senderCharacterId)
            writeUtf8Truncated(push.senderDisplayName, LiveChatLimits.MAX_NAME_BYTES)
            writeInt(push.senderWorldId); writeByte(push.senderRank); writeByte(push.senderCrown)
            writeUtf8Truncated(push.message, LiveChatLimits.MAX_MESSAGE_BYTES)
        }

    fun decodeMessagePush(input: FrameReader): LiveChatMessagePush {
        val push = LiveChatMessagePush(
            input.readUnsignedByte(), input.readLong(), input.readInt(),
            input.readUtf8LenPrefixed(), input.readInt(), input.readSignedByte(), input.readUnsignedByte(),
            input.readUtf8LenPrefixed(),
        )
        input.requireFullyConsumed()
        require(LiveChatKind.valid(push.kind) && push.channelId > 0L)
        require(push.senderCharacterId > 0 && push.senderWorldId > 0 && push.senderCrown in 0..255)
        require(push.senderDisplayName.toByteArray(Charsets.UTF_8).size <= LiveChatLimits.MAX_NAME_BYTES)
        require(push.message.isNotBlank() && push.message.toByteArray(Charsets.UTF_8).size <= LiveChatLimits.MAX_MESSAGE_BYTES)
        return push
    }

    private fun FrameWriter.writeRoster(roster: LiveChatRosterPush) {
        require(LiveChatKind.valid(roster.kind) && roster.channelId > 0L)
        require(roster.members.size <= LiveChatLimits.MAX_MEMBERS)
        writeByte(roster.kind); writeLong(roster.channelId); writeLong(roster.revision)
        writeShort(roster.members.size)
        roster.members.forEach { member ->
            require(member.characterId > 0 && member.worldId > 0)
            writeInt(member.characterId)
            writeUtf8Truncated(member.displayName, LiveChatLimits.MAX_NAME_BYTES)
            writeInt(member.worldId)
            writeByte(member.rank)
        }
        val settings = roster.friendSettings
        require(settings == null || roster.kind == LiveChatKind.FRIENDS_CHAT)
        writeByte(if (settings == null) 0 else 1)
        if (settings != null) {
            writeInt(settings.ownerCharacterId)
            writeUtf8Truncated(settings.ownerNameKey, LiveChatLimits.MAX_NAME_BYTES)
            writeUtf8Truncated(settings.displayName, LiveChatLimits.MAX_NAME_BYTES)
            writeByte(settings.enterRank)
            writeByte(settings.talkRank)
            writeByte(settings.kickRank)
            writeByte(settings.lootShareRank)
            writeByte(if (settings.coinShareEnabled) 1 else 0)
            writeShort(settings.maxMembers)
        }
    }

    private fun FrameReader.readRoster(): LiveChatRosterPush {
        val kind = readUnsignedByte()
        val channelId = readLong()
        val revision = readLong()
        val count = readUnsignedShort()
        require(LiveChatKind.valid(kind) && channelId > 0L && count <= LiveChatLimits.MAX_MEMBERS)
        val members = List(count) {
            LiveChatMemberWire(readInt(), readUtf8LenPrefixed(), readInt(), readSignedByte())
        }
        require(members.all { it.characterId > 0 && it.worldId > 0 &&
                it.displayName.toByteArray(Charsets.UTF_8).size <= LiveChatLimits.MAX_NAME_BYTES })
        val settings = if (readUnsignedByte() == 0) null else LiveFriendChatSettings(
            ownerCharacterId = readInt(),
            ownerNameKey = readUtf8LenPrefixed(),
            displayName = readUtf8LenPrefixed(),
            enterRank = readSignedByte(),
            talkRank = readSignedByte(),
            kickRank = readSignedByte(),
            lootShareRank = readSignedByte(),
            coinShareEnabled = readUnsignedByte() != 0,
            maxMembers = readUnsignedShort(),
        )
        require(settings == null || kind == LiveChatKind.FRIENDS_CHAT)
        return LiveChatRosterPush(kind, channelId, revision, members, settings)
    }
}