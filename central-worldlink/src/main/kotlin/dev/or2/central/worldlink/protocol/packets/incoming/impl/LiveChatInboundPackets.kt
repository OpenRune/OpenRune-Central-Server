package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.*
import dev.or2.central.worldlink.protocol.livechat.*

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_LIVE_CHAT_JOIN, name = "WORLD_LIVE_CHAT_JOIN", fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.LONG])
object WorldLiveChatJoinPacket : InboundPacket<LiveChatJoinRequest> {
    override fun decode(input: FrameReader) = LiveChatPackets.decodeJoin(input)
}

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_LIVE_CHAT_LEAVE, name = "WORLD_LIVE_CHAT_LEAVE", fields = [FieldKind.INT, FieldKind.BYTE])
object WorldLiveChatLeavePacket : InboundPacket<LiveChatLeaveRequest> {
    override fun decode(input: FrameReader) = LiveChatPackets.decodeLeave(input)
}

@WorldPacketIncoming(opcode = WorldOpcodes.OP_WORLD_LIVE_CHAT_MESSAGE, name = "WORLD_LIVE_CHAT_MESSAGE", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldLiveChatMessagePacket : InboundPacket<LiveChatMessageRequest> {
    override fun decode(input: FrameReader) = LiveChatPackets.decodeMessage(input)
}
