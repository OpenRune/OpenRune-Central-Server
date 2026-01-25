package dev.openrune.central.client.packet

import dev.openrune.central.packet.model.OutgoingPacketBody
import java.util.concurrent.ConcurrentHashMap

class ClientPacketRouter {
    private val handlersById = ConcurrentHashMap<Int, MutableList<ClientPacketHandler<*>>>()

    fun register(handler: ClientPacketHandler<*>) {
        handlersById.compute(handler.type.packetId) { _, list ->
            (list ?: mutableListOf()).also { it.add(handler) }
        }
    }

    fun dispatch(call: ClientPacketCall, opcode: Int, decoded: OutgoingPacketBody) {
        val handlers = handlersById[opcode] ?: return
        for (h in handlers) {
            h.tryHandle(call, decoded)
        }
    }
}
