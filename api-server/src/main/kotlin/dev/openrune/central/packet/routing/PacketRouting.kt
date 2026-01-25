package dev.openrune.central.packet.routing

import dev.openrune.central.AppState
import dev.openrune.central.config.WorldConfig
import dev.openrune.central.packet.CentralPacketServer
import dev.openrune.central.packet.CentralReceivedPacket
import dev.openrune.central.packet.IncomingPackets
import dev.openrune.central.packet.model.IncomingPacketBody
import dev.openrune.central.packet.model.OutgoingPacketBody
import dev.openrune.central.packet.registry.OutgoingPackets
import dev.openrune.central.packet.PacketCallContext
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class PacketRouter {
    private val logger = LoggerFactory.getLogger(PacketRouter::class.java)
    private val handlersById = LinkedHashMap<Int, MutableList<PacketHandler<*>>>()

    fun register(handler: PacketHandler<*>) {
        handlersById.getOrPut(handler.type.packetId) { mutableListOf() }.add(handler)
    }

    /**
     * Register a handler with a lambda (Ktor-route-like).
     */
    fun <T : IncomingPacketBody> on(type: IncomingPackets, clazz: KClass<T>, fn: (PacketCall, T) -> Unit) {
        register(LambdaPacketHandler(clazz, type, fn))
    }

    fun dispatch(raw: CentralReceivedPacket) {
        val world = AppState.worldsById[raw.worldId] ?: return
        val call = PacketCall(world, raw)

        val spec = IncomingPackets.byId(raw.opcode) ?: run {
            logger.debug("No IncomingPackets spec for opcode={} (world={})", raw.opcode, raw.worldId)
            return
        }

        val decoded =
            try {
                spec.decode(raw.payload)
            } catch (t: Throwable) {
                logger.warn("Decode failed for opcode={} (world={}, seq={})", raw.opcode, raw.worldId, raw.seq, t)
                return
            }

        // Default enum/codec handler (optional)
        try {
            spec.handle(call, decoded)
        } catch (t: Throwable) {
            logger.error("Default handler error for opcode={} (world={}, seq={})", raw.opcode, raw.worldId, raw.seq, t)
        }

        val handlers = handlersById[raw.opcode]
        if (handlers.isNullOrEmpty()) {
            logger.debug("No handler registered for opcode={} (world={})", raw.opcode, raw.worldId)
            return
        }

        for (h in handlers) {
            try {
                h.tryHandle(call, decoded)
            } catch (t: Throwable) {
                logger.error("Handler error for opcode={} (world={}, seq={})", raw.opcode, raw.worldId, raw.seq, t)
            }
        }
    }
}



