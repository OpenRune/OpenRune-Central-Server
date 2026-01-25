package dev.openrune.central.packet

import dev.openrune.central.packet.model.IncomingPacketBody

/**
 * Optional handler hook for server-side (world -> central) packets.
 *
 * We keep this separate from [dev.openrune.central.packet.codec.PacketCodec] so we can avoid enum-based `encode(...)`
 * and also avoid a combined "definition" type.
 */
interface IncomingPacketHandler<T : IncomingPacketBody> {
    fun handle(call: PacketCallContext, body: T)
}

