package dev.openrune.central.packet.registry

import dev.openrune.central.packet.OutgoingPacketIds
import dev.openrune.central.packet.codec.LoginResponseCodec
import dev.openrune.central.packet.codec.LogoutResponseCodec
import dev.openrune.central.packet.codec.LogsResponseCodec
import dev.openrune.central.packet.codec.PacketCodec
import dev.openrune.central.packet.codec.PlayerSaveLoadResponseCodec
import dev.openrune.central.packet.codec.PlayerSaveResponseCodec
import dev.openrune.central.packet.io.FieldType
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.packet.model.LogoutResponseOutgoing
import dev.openrune.central.packet.model.LogsResponseOutgoing
import dev.openrune.central.packet.model.OutgoingPacketBody
import dev.openrune.central.packet.model.PlayerSaveLoadResponseOutgoing
import dev.openrune.central.packet.model.PlayerSaveOutgoing
import kotlin.reflect.KClass


enum class OutgoingPackets(
    val packetId: Int,
    val clazz: KClass<out OutgoingPacketBody>,
    val types: List<FieldType>,
    private val codec: PacketCodec<out OutgoingPacketBody>
) {
    LOGIN_RESPONSE(
        OutgoingPacketIds.LOGIN_RESPONSE,
        LoginResponseOutgoing::class,
        listOf(FieldType.LONG, FieldType.INT),
        LoginResponseCodec
    ),
    PLAYER_SAVE_LOAD_RESPONSE(
        OutgoingPacketIds.PLAYER_SAVE_LOAD_RESPONSE,
        PlayerSaveLoadResponseOutgoing::class,
        listOf(FieldType.LONG, FieldType.INT),
        PlayerSaveLoadResponseCodec
    ),
    LOGOUT(
        OutgoingPacketIds.LOGOUT,
        LogoutResponseOutgoing::class,
        listOf(FieldType.LONG, FieldType.INT),
        LogoutResponseCodec
    ),
    LOGS_RESPONSE(
        OutgoingPacketIds.LOGS_RESPONSE,
        LogsResponseOutgoing::class,
        listOf(FieldType.LONG, FieldType.INT),
        LogsResponseCodec
    ),
    PLAYER_SAVE_RESPONSE(
        OutgoingPacketIds.PLAYER_SAVE,
        PlayerSaveOutgoing::class,
        listOf(FieldType.LONG, FieldType.INT),
        PlayerSaveResponseCodec
    ),
    ;

    val minPayloadSize: Int = types.sumOf { it.minSize }

    fun encode(body: OutgoingPacketBody): ByteArray {
        require(clazz.isInstance(body)) { "Body class mismatch: expected=$clazz got=${body::class}" }
        val w = PacketWriter()
        @Suppress("UNCHECKED_CAST")
        (codec as PacketCodec<OutgoingPacketBody>).encode(w, body)
        val payload = w.toByteArray()
        require(payload.size >= minPayloadSize) { "Encoded payload smaller than minPayloadSize" }
        return payload
    }

    fun decode(payload: ByteArray): OutgoingPacketBody {
        val r = PacketReader(payload)
        @Suppress("UNCHECKED_CAST")
        val pkt = (codec as PacketCodec<OutgoingPacketBody>).decode(r)
        r.requireFullyConsumed()
        return pkt
    }

    companion object {
        private val byId = entries.associateBy { it.packetId }
        private val byClass = entries.associateBy { it.clazz }

        fun byId(id: Int): OutgoingPackets? = byId[id]
        fun byClass(clazz: KClass<out OutgoingPacketBody>): OutgoingPackets? = byClass[clazz]
    }
}

