package dev.openrune.central.packet.routing

import dev.openrune.central.config.WorldConfig
import dev.openrune.central.packet.CentralPacketServer
import dev.openrune.central.packet.CentralReceivedPacket
import dev.openrune.central.packet.PacketCallContext
import dev.openrune.central.packet.model.OutgoingPacketBody
import dev.openrune.central.packet.registry.OutgoingPackets


/**
 * Context passed to packet handlers (similar to Ktor's `call`).
 */
class PacketCall(
    val world: WorldConfig,
    val raw: CentralReceivedPacket
) : PacketCallContext {
    override val worldId: Int get() = world.id
    val opcode: Int get() = raw.opcode
    val seq: Long get() = raw.seq

    fun respond(type: OutgoingPackets, body: OutgoingPacketBody): Boolean {
        val payload = type.encode(body)
        return CentralPacketServer.sendToWorld(worldId, type.packetId, payload)
    }

    override fun respond(body: OutgoingPacketBody): Boolean {
        val type =
            OutgoingPackets.byClass(body::class)
                ?: throw IllegalArgumentException("No OutgoingPackets entry for ${body::class}")
        return respond(type, body)
    }
}