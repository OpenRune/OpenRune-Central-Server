package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.*
import dev.or2.central.worldlink.protocol.friendchat.*

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC, name = "WORLD_FRIENDCHAT_SYNC", fields = [FieldKind.INT])
object WorldFriendChatSyncPacket : InboundPacket<FriendChatCharacterPayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeCharacter(input)
}

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, name = "WORLD_FRIENDCHAT_RESOLVE", fields = [FieldKind.INT, FieldKind.STRING_96])
object WorldFriendChatResolvePacket : InboundPacket<FriendChatResolvePayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeResolve(input)
}

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_SETTINGS, name = "WORLD_FRIENDCHAT_SET_SETTINGS", fields = [FieldKind.INT, FieldKind.STRING_96, FieldKind.BYTE, FieldKind.BYTE, FieldKind.BYTE, FieldKind.BYTE, FieldKind.BYTE])
object WorldFriendChatSetSettingsPacket : InboundPacket<FriendChatSettingsPayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeSettings(input)
}

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_RANK, name = "WORLD_FRIENDCHAT_SET_RANK", fields = [FieldKind.INT, FieldKind.STRING_96, FieldKind.STRING_96, FieldKind.BYTE])
object WorldFriendChatSetRankPacket : InboundPacket<FriendChatRankPayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeRank(input)
}


@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, name = "WORLD_FRIENDCHAT_RESTORE", fields = [FieldKind.INT])
object WorldFriendChatRestorePacket : InboundPacket<FriendChatCharacterPayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeCharacter(input)
}


@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_CLEAR_RESTORE, name = "WORLD_FRIENDCHAT_CLEAR_RESTORE", fields = [FieldKind.INT])
object WorldFriendChatClearRestorePacket : InboundPacket<FriendChatCharacterPayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeCharacter(input)
}
@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_KICK, name = "WORLD_FRIENDCHAT_KICK", fields = [FieldKind.INT, FieldKind.LONG, FieldKind.STRING_96])
object WorldFriendChatKickPacket : InboundPacket<FriendChatKickPayload> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeKick(input)
}

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_FIELD, name = "WORLD_FRIENDCHAT_SET_FIELD", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.STRING_96])
object WorldFriendChatSetFieldPacket : InboundPacket<FriendChatFieldMutation> {
    override fun decode(input: FrameReader) = FriendChatPackets.decodeFieldMutation(input)
}