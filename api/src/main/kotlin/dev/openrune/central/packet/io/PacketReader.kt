package dev.openrune.central.packet.io

import java.nio.charset.StandardCharsets

class PacketReader(private val data: ByteArray) {
    private var i: Int = 0

    private fun requireRemaining(n: Int) {
        require(i + n <= data.size) { "Packet underflow (need=$n remaining=${data.size - i})" }
    }

    fun remaining(): Int = data.size - i

    fun readBoolean(): Boolean = readByte() != 0

    fun readByte(): Int {
        requireRemaining(1)
        return data[i++].toInt() and 0xFF
    }

    fun readShort(): Int {
        requireRemaining(2)
        val hi = readByte()
        val lo = readByte()
        return (hi shl 8) or lo
    }

    fun readInt(): Int {
        requireRemaining(4)
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    fun readLong(): Long {
        val hi = readInt().toLong() and 0xFFFFFFFFL
        val lo = readInt().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    fun readString(): String {
        val len = readShort()
        requireRemaining(len)
        val s = String(data, i, len, StandardCharsets.UTF_8)
        i += len
        return s
    }

    fun readBytes(): ByteArray {
        val len = readInt()
        require(len >= 0) { "Negative BYTES length" }
        requireRemaining(len)
        return data.copyOfRange(i, i + len).also { i += len }
    }

    /**
     * Reads an int list with a u8 count prefix.
     */
    fun readIntListU8(): List<Int> {
        val count = readByte()
        val out = ArrayList<Int>(count)
        repeat(count) {
            out.add(readInt())
        }
        return out
    }

    fun requireFullyConsumed() {
        require(i == data.size) { "Packet has trailing bytes (remaining=${data.size - i})" }
    }
}