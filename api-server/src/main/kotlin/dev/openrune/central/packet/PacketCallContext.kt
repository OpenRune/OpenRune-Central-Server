package dev.openrune.central.packet

import dev.openrune.central.packet.model.OutgoingPacketBody

/**
 * Minimal context exposed to packet handlers that live in the shared `:api` module.
 *
 * This is implemented by the server-side `PacketCall` (api-server) and gives handlers a way to
 * respond without depending on Ktor/Netty/server classes.
 */
interface PacketCallContext {
    val worldId: Int

    fun respond(body: OutgoingPacketBody): Boolean
}