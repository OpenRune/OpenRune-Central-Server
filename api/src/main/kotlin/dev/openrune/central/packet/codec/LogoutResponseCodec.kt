package dev.openrune.central.packet.codec

import dev.openrune.central.LogsResponse
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LogoutResponseOutgoing
import dev.openrune.central.packet.model.LogsResponseOutgoing

object LogoutResponseCodec : PacketCodec<LogoutResponseOutgoing> {

    override fun decodeBody(r: PacketReader, requestId: Long): LogoutResponseOutgoing {
        val result = LogsResponse.entries[r.readInt()]
        val error = if (r.remaining() > 0) r.readString() else null
        return LogoutResponseOutgoing(result = result, error = error, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: LogoutResponseOutgoing) {
        w.writeInt(body.result.ordinal)
        if (body.error != null) {
            w.writeString(body.error)
        }
    }
}

