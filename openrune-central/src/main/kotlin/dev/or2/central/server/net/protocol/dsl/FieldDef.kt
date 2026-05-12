package dev.or2.central.server.net.protocol.dsl

sealed class FieldDef {
    data class IntField(val name: String) : FieldDef()
    data class LongField(val name: String) : FieldDef()
    data class ByteArrayField(val name: String, val max: Int) : FieldDef()
    data class StringField(val name: String, val max: Int) : FieldDef()
    data class OptionalIntField(val name: String) : FieldDef()
}