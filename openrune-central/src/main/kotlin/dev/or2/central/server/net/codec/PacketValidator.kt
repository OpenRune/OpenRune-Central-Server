package dev.or2.central.server.net.codec

import dev.or2.central.server.net.protocol.dsl.FieldDef
import dev.or2.central.server.net.protocol.dsl.PacketDef

object PacketValidator {

    fun validateInbound(opcode: Int, bodySize: Int): String? {
        val def = PacketSpecs.inbound(opcode) ?: return "unknown_opcode"

        val max = estimateMax(def)
        val min = estimateMin(def)

        if (bodySize < min) return "too_short"
        if (bodySize > max) return "too_long"

        return null
    }

    private fun estimateMax(def: PacketDef): Int {
        return def.fields.sumOf {
            when (it) {
                is FieldDef.IntField -> 4
                is FieldDef.LongField -> 8
                is FieldDef.StringField -> it.max * 4
                is FieldDef.ByteArrayField -> it.max
                is FieldDef.OptionalIntField -> 5
            }
        }
    }

    private fun estimateMin(def: PacketDef): Int {
        return def.fields.sumOf {
            when (it) {
                is FieldDef.OptionalIntField -> 0
                else -> 0
            }.toInt()
        }
    }
}