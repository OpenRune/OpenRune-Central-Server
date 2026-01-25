package dev.openrune.central.packet

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom

/**
 * Minimal binary protocol shared by central + world servers.
 *
 * Transport framing is done by Netty (length field: 4 bytes, big-endian).
 * The payload below is the content inside that frame.
 */
object PacketProtocol {
    const val VERSION: Byte = 1

    /**
     * Safety limit for a single framed payload (not counting the length field).
     */
    const val MAX_FRAME_BYTES: Int = 1024 * 1024 // 1 MiB

    const val TYPE_AUTH: Byte = 1
    const val TYPE_AUTH_OK: Byte = 2
    const val TYPE_AUTH_FAIL: Byte = 3

    /**
     * Signed world -> central packet.
     */
    const val TYPE_DATA: Byte = 10

    /**
     * Unsigned central -> world packet.
     */
    const val TYPE_PUSH: Byte = 11

    private val rng = SecureRandom()

    fun randomNonce16(): ByteArray = ByteArray(16).also { rng.nextBytes(it) }
}

data class OutgoingPacket(
    val opcode: Int,
    val payload: ByteArray
)

data class IncomingPacket(
    val opcode: Int,
    val payload: ByteArray,
    val seq: Long
)

/**
 * Bytes that are signed/verified during the initial connection auth.
 *
 * This binds auth to:
 * - protocol version
 * - world id
 * - timestamp (anti-replay window)
 * - nonce (uniqueness)
 */
fun buildPacketAuthPayload(
    version: Byte,
    worldId: Int,
    timestampMs: Long,
    nonce16: ByteArray
): ByteArray {
    require(nonce16.size == 16) { "nonce must be 16 bytes" }
    val baos = ByteArrayOutputStream(1 + 4 + 8 + 16)
    DataOutputStream(baos).use { out ->
        out.writeByte(version.toInt())
        out.writeInt(worldId)
        out.writeLong(timestampMs)
        out.write(nonce16)
    }
    return baos.toByteArray()
}

/**
 * Bytes that are signed/verified for each world -> central packet.
 *
 * This prevents tampering/replay by binding the signature to:
 * - protocol version
 * - world id (implicit session identity)
 * - monotonic sequence
 * - opcode + payload
 */
fun buildPacketDataPayload(
    version: Byte,
    worldId: Int,
    seq: Long,
    opcode: Int,
    payload: ByteArray
): ByteArray {
    val baos = ByteArrayOutputStream(1 + 4 + 8 + 4 + 4 + payload.size)
    DataOutputStream(baos).use { out ->
        out.writeByte(version.toInt())
        out.writeInt(worldId)
        out.writeLong(seq)
        out.writeInt(opcode)
        out.writeInt(payload.size)
        out.write(payload)
    }
    return baos.toByteArray()
}

