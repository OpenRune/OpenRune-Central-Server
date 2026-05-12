package dev.or2.central.server.net.protocol.dsl

class PacketBuilder(
    val opcode: Int,
    val name: String,
) {
    internal val fields = mutableListOf<FieldDef>()

    fun int(name: String) {
        fields += FieldDef.IntField(name)
    }

    fun long(name: String) {
        fields += FieldDef.LongField(name)
    }

    fun string(name: String, max: Int) {
        fields += FieldDef.StringField(name, max)
    }

    fun bytes(name: String, max: Int) {
        fields += FieldDef.ByteArrayField(name, max)
    }

    fun optionalInt(name: String) {
        fields += FieldDef.OptionalIntField(name)
    }
}