package dev.or2.central.worldlink.protocol.clanbroadcast

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame

object ClanSystemBroadcastPackets {
    fun encodeRequest(p: ClanSystemBroadcastRequest): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SYSTEM_BROADCAST) {
        writeLong(p.clanId); writeInt(p.settingId); writeByte(if (p.sourceIsMainGame) 1 else 0)
        writeUtf8Truncated(p.message, ClanSystemBroadcastLimits.MAX_MESSAGE_BYTES)
    }
    fun decodeRequest(r: FrameReader): ClanSystemBroadcastRequest = ClanSystemBroadcastRequest(
        r.readLong(), r.readInt(), r.readUnsignedByte().also { require(it in 0..1) } == 1, r.readUtf8LenPrefixed()
    ).also { r.requireFullyConsumed(); validate(it.clanId, it.message) }
    fun encodeAck(success: Boolean): ByteArray = outboundFrame(WorldOpcodes.OP_WORLD_CLAN_SYSTEM_BROADCAST_ACK) { writeByte(if (success) 1 else 0) }
    fun decodeAck(r: FrameReader): Boolean {
        val status = r.readUnsignedByte()
        r.requireFullyConsumed()
        require(status in 0..1)
        return status == 1
    }
    fun encodePush(p: ClanSystemBroadcastPush): ByteArray = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_SYSTEM_BROADCAST) {
        writeLong(p.clanId); writeInt(p.settingId); writeByte(if (p.sourceIsMainGame) 1 else 0)
        writeInt(p.sourceWorldId); writeUtf8Truncated(p.message, ClanSystemBroadcastLimits.MAX_MESSAGE_BYTES)
    }
    fun decodePush(r: FrameReader): ClanSystemBroadcastPush = ClanSystemBroadcastPush(
        r.readLong(), r.readInt(), r.readUnsignedByte().also { require(it in 0..1) } == 1, r.readInt(), r.readUtf8LenPrefixed()
    ).also { r.requireFullyConsumed(); validate(it.clanId, it.message); require(it.sourceWorldId > 0) }
    private fun validate(clanId: Long, message: String) {
        require(clanId > 0L && message.isNotBlank() && message.toByteArray(Charsets.UTF_8).size <= ClanSystemBroadcastLimits.MAX_MESSAGE_BYTES)
    }
}