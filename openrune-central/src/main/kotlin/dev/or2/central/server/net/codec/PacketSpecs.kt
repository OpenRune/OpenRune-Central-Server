package dev.or2.central.server.net.codec

import dev.or2.central.server.net.protocol.WorldProtocol
import dev.or2.central.server.net.protocol.dsl.PacketDef

object PacketSpecs {

    val inbound: Map<Int, PacketDef> =
        WorldProtocol.inbound.associateBy { it.opcode }

    val outbound: Map<Int, PacketDef> =
        WorldProtocol.outbound.associateBy { it.opcode }

    fun inbound(opcode: Int) = inbound[opcode]

    fun outbound(opcode: Int) = outbound[opcode]
}