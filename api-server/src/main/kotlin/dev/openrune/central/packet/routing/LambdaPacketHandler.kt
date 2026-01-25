package dev.openrune.central.packet.routing

import dev.openrune.central.packet.IncomingPackets
import dev.openrune.central.packet.model.IncomingPacketBody
import kotlin.reflect.KClass

/**
 * Lambda-based handler to avoid boilerplate subclasses.
 */
class LambdaPacketHandler<T : IncomingPacketBody>(
    bodyClass: KClass<T>,
    override val type: IncomingPackets,
    private val fn: (PacketCall, T) -> Unit
) : PacketHandler<T>(bodyClass) {
    override fun handle(call: PacketCall, packet: T) = fn(call, packet)
}
