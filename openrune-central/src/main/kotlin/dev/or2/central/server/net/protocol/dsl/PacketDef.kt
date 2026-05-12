package dev.or2.central.server.net.protocol.dsl

data class PacketDef(
    val opcode: Int,
    val name: String,
    val fields: List<FieldDef>,
)