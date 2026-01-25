package dev.openrune.central.packet.io

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PacketWriter {
    private val out = ByteArrayOutputStream()

    fun writeBoolean(v: Boolean) = writeByte(if (v) 1 else 0)

    fun writeByte(v: Int) {
        out.write(v and 0xFF)
    }

    fun writeShort(v: Int) {
        writeByte(v ushr 8)
        writeByte(v)
    }

    fun writeInt(v: Int) {
        writeByte(v ushr 24)
        writeByte(v ushr 16)
        writeByte(v ushr 8)
        writeByte(v)
    }

    fun writeLong(v: Long) {
        writeInt((v ushr 32).toInt())
        writeInt((v and 0xFFFFFFFFL).toInt())
    }

    fun writeString(v: String) {
        val bytes = v.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 0xFFFF) { "STRING too long (max 65535 bytes)" }
        writeShort(bytes.size)
        out.write(bytes)
    }

    fun writeBytes(v: ByteArray) {
        writeInt(v.size)
        out.write(v)
    }

    /**
     * Writes an int list with a u8 count prefix (0..255).
     */
    fun writeIntListU8(values: List<Int>) {
        require(values.size <= 0xFF) { "int list too long (max 255)" }
        writeByte(values.size)
        for (v in values) {
            writeInt(v)
        }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

