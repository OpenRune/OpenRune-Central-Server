package dev.or2.central.server.net.protocol

import dev.or2.central.server.net.protocol.dsl.PacketBuilder
import dev.or2.central.server.net.protocol.dsl.PacketDef

object WorldProtocol {

    val inbound = mutableListOf<PacketDef>()
    val outbound = mutableListOf<PacketDef>()

    fun inbound(opcode: Int, name: String, block: PacketBuilder.() -> Unit) {
        val b = PacketBuilder(opcode, name).apply(block)
        inbound += PacketDef(opcode, name, b.fields)
    }

    fun outbound(opcode: Int, name: String, block: PacketBuilder.() -> Unit) {
        val b = PacketBuilder(opcode, name).apply(block)
        outbound += PacketDef(opcode, name, b.fields)
    }
}