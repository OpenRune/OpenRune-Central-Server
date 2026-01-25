package dev.openrune.central.packet.routing

import dev.openrune.central.packet.IncomingPackets
import dev.openrune.central.packet.model.IncomingPacketBody
import kotlin.reflect.KClass

/**
 * Base class for handlers, similar in spirit to Ktor route blocks.
 */
abstract class PacketHandler<T : IncomingPacketBody>(
    private val bodyClass: KClass<T>
) {
    abstract val type: IncomingPackets

    fun tryHandle(call: PacketCall, decoded: IncomingPacketBody) {
        if (!bodyClass.isInstance(decoded)) return
        @Suppress("UNCHECKED_CAST")
        handle(call, decoded as T)
    }

    protected abstract fun handle(call: PacketCall, packet: T)
}