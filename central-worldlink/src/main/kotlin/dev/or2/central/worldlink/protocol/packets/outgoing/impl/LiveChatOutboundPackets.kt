package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.*
import dev.or2.central.worldlink.protocol.livechat.*

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_WORLD_LIVE_CHAT_RESULT, name = "WORLD_LIVE_CHAT_RESULT", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object WorldLiveChatResultPacket : OutboundPacket<LiveChatResult> {
    override fun encode(payload: LiveChatResult) = LiveChatPackets.encodeResult(payload)
    fun decode(input: FrameReader) = LiveChatPackets.decodeResult(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_LIVE_CHAT_ROSTER, name = "SERVER_LIVE_CHAT_ROSTER", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerLiveChatRosterPacket : OutboundPacket<LiveChatRosterPush> {
    override fun encode(payload: LiveChatRosterPush) = LiveChatPackets.encodeRoster(payload)
    fun decode(input: FrameReader) = LiveChatPackets.decodeRoster(input)
}

@WorldPacketOutgoing(opcode = WorldOpcodes.OP_SERVER_LIVE_CHAT_MESSAGE, name = "SERVER_LIVE_CHAT_MESSAGE", fields = [FieldKind.CLAN_VARIABLE_REQUIRED])
object ServerLiveChatMessagePacket : OutboundPacket<LiveChatMessagePush> {
    override fun encode(payload: LiveChatMessagePush) = LiveChatPackets.encodeMessagePush(payload)
    fun decode(input: FrameReader) = LiveChatPackets.decodeMessagePush(input)
}
