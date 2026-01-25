package dev.openrune.central.packet.codec

import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.PacketBody

/**
 * Packet codec: defines how to encode/decode a body.
 */
interface PacketCodec<T : PacketBody> {
    /**
     * Decode packet-specific fields (requestId already read).
     */
    fun decodeBody(r: PacketReader, requestId: Long): T

    /**
     * Encode packet-specific fields (requestId is written automatically).
     */
    fun encodeBody(w: PacketWriter, body: T)

    /**
     * Standard wrapper: requestId is always encoded first.
     */
    fun decode(r: PacketReader): T {
        val requestId = if (r.remaining() >= 8) r.readLong() else 0L
        return decodeBody(r, requestId)
    }

    /**
     * Standard wrapper: requestId is always encoded first.
     */
    fun encode(w: PacketWriter, body: T) {
        w.writeLong(body.requestId)
        encodeBody(w, body)
    }
}

