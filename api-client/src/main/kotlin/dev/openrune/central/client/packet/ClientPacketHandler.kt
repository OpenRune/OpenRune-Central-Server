package dev.openrune.central.client.packet

import dev.openrune.central.packet.model.OutgoingPacketBody
import dev.openrune.central.packet.registry.OutgoingPackets
import kotlin.reflect.KClass

/**
 * Abstract handler base class, similar to Ktor route blocks but for incoming packets on the client.
 */
abstract class ClientPacketHandler<T : OutgoingPacketBody>(
    private val bodyClass: KClass<T>
) {
    abstract val type: OutgoingPackets

    fun tryHandle(call: ClientPacketCall, decoded: OutgoingPacketBody) {
        if (!bodyClass.isInstance(decoded)) return
        @Suppress("UNCHECKED_CAST")
        handle(call, decoded as T)
    }

    protected abstract fun handle(call: ClientPacketCall, packet: T)
}