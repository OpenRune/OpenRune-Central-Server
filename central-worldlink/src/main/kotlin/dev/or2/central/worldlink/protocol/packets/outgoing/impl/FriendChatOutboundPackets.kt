package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.*
import dev.or2.central.worldlink.protocol.friendchat.*

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC_OK, name = "WORLD_FRIENDCHAT_SYNC_OK", fields = [FieldKind.FRIEND_CHAT_VARIABLE_REQUIRED])
object WorldFriendChatSyncOkPacket : OutboundPacket<FriendChatSnapshot> {
    override fun encode(payload: FriendChatSnapshot) = FriendChatPackets.encodeSyncOk(payload)
    fun decode(input: FrameReader) = FriendChatPackets.decodeSyncOk(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC_FAIL, name = "WORLD_FRIENDCHAT_SYNC_FAIL", fields = [FieldKind.BYTE])
object WorldFriendChatSyncFailPacket : OutboundPacket<Int> {
    override fun encode(payload: Int) = FriendChatPackets.encodeSyncFail(payload)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE_OK, name = "WORLD_FRIENDCHAT_RESOLVE_OK", fields = [FieldKind.FRIEND_CHAT_VARIABLE_REQUIRED])
object WorldFriendChatResolveOkPacket : OutboundPacket<FriendChatResolveReply> {
    override fun encode(payload: FriendChatResolveReply) = FriendChatPackets.encodeResolveOk(payload)
    fun decode(input: FrameReader) = FriendChatPackets.decodeResolveOk(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_OK, name = "WORLD_FRIENDCHAT_OK", fields = [FieldKind.BYTE, FieldKind.LONG])
object WorldFriendChatOkPacket : OutboundPacket<FriendChatMutationReply> {
    override fun encode(payload: FriendChatMutationReply) = FriendChatPackets.encodeOk(payload)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_FAIL, name = "WORLD_FRIENDCHAT_FAIL", fields = [FieldKind.BYTE, FieldKind.BYTE])
object WorldFriendChatFailPacket : OutboundPacket<FriendChatMutationFail> {
    override fun encode(payload: FriendChatMutationFail) = FriendChatPackets.encodeFail(payload)
}


@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE_OK, name = "WORLD_FRIENDCHAT_RESTORE_OK", fields = [FieldKind.LONG, FieldKind.STRING_96])
object WorldFriendChatRestoreOkPacket : OutboundPacket<FriendChatRestoreReply> {
    override fun encode(payload: FriendChatRestoreReply) = FriendChatPackets.encodeRestoreOk(payload)
    fun decode(input: FrameReader) = FriendChatPackets.decodeRestoreOk(input)
}
@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_FIELD_OK, name = "WORLD_FRIENDCHAT_SET_FIELD_OK", fields = [FieldKind.LONG, FieldKind.BYTE])
object WorldFriendChatSetFieldOkPacket : OutboundPacket<FriendChatFieldMutationReply> {
    override fun encode(payload: FriendChatFieldMutationReply) = FriendChatPackets.encodeFieldMutationOk(payload)
}
