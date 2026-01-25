package dev.openrune.central.packet.codec

import dev.openrune.central.PlayerSaveLoadResponse
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.PlayerSaveLoadResponseOutgoing

object PlayerSaveLoadResponseCodec : PacketCodec<PlayerSaveLoadResponseOutgoing> {

    override fun decodeBody(r: PacketReader, requestId: Long): PlayerSaveLoadResponseOutgoing {
        val result = PlayerSaveLoadResponse.entries[r.readInt()]
        val data = if (r.remaining() > 0) r.readString() else null
        return PlayerSaveLoadResponseOutgoing(result = result, data = data, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: PlayerSaveLoadResponseOutgoing) {
        w.writeInt(body.result.ordinal)
        if (body.data != null) {
            w.writeString(body.data)
        }
    }
}

