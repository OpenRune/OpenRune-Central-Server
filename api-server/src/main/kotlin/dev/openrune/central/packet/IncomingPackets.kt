package dev.openrune.central.packet

import dev.openrune.central.packet.codec.LoginRequestCodec
import dev.openrune.central.packet.codec.LogoutRequestCodec
import dev.openrune.central.packet.codec.LogsRequestCodec
import dev.openrune.central.packet.codec.PacketCodec
import dev.openrune.central.packet.codec.PlayerSaveLoadRequestCodec
import dev.openrune.central.packet.codec.PlayerSaveUpsertRequestCodec
import dev.openrune.central.packet.io.FieldType
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.model.IncomingPacketBody
import dev.openrune.central.packet.model.LoginRequestIncoming
import dev.openrune.central.packet.model.LogsRequestIncoming
import dev.openrune.central.packet.model.LogoutIncoming
import dev.openrune.central.packet.model.PlayerSaveLoadRequestIncoming
import dev.openrune.central.packet.model.PlayerSaveUpsertRequestIncoming
import kotlin.reflect.KClass

enum class IncomingPackets(
    val packetId: Int,
    val clazz: KClass<out IncomingPacketBody>,
    val types: List<FieldType>,
    private val codec: PacketCodec<out IncomingPacketBody>,
    private val handler: IncomingPacketHandler<out IncomingPacketBody>? = null,
) {
    LOGIN_REQUEST(
        IncomingPacketIds.LOGIN_REQUEST,
        LoginRequestIncoming::class,
        // requestId is encoded first by PacketCodec
        listOf(FieldType.LONG, FieldType.STRING, FieldType.STRING, FieldType.INT_LIST_U8),
        LoginRequestCodec,
        handler = LoginRequestCodec
    ),
    LOGOUT_REQUEST(
        IncomingPacketIds.LOGOUT_REQUEST,
        LogoutIncoming::class,
        // requestId is encoded first by PacketCodec
        listOf(FieldType.LONG, FieldType.LONG, FieldType.STRING, FieldType.INT_LIST_U8),
        LogoutRequestCodec,
        handler = LogoutRequestCodec
    ),
    PLAYER_SAVE_LOAD_REQUEST(
        IncomingPacketIds.PLAYER_SAVE_LOAD_REQUEST,
        PlayerSaveLoadRequestIncoming::class,
        // requestId is encoded first by PacketCodec
        listOf(FieldType.LONG, FieldType.LONG, FieldType.STRING),
        PlayerSaveLoadRequestCodec,
        handler = PlayerSaveLoadRequestCodec
    ),
    PLAYER_SAVE_UPSERT_REQUEST(
        IncomingPacketIds.PLAYER_SAVE_UPSERT_REQUEST,
        PlayerSaveUpsertRequestIncoming::class,
        // requestId is encoded first by PacketCodec
        listOf(FieldType.LONG, FieldType.LONG, FieldType.STRING, FieldType.STRING),
        PlayerSaveUpsertRequestCodec,
        handler = PlayerSaveUpsertRequestCodec
    ),
    LOGS_REQUEST(
        IncomingPacketIds.LOGS_REQUEST,
        LogsRequestIncoming::class,
        // requestId is encoded first by PacketCodec
        listOf(FieldType.LONG, FieldType.STRING),
        LogsRequestCodec,
        handler = LogsRequestCodec
    ),
    ;

    val minPayloadSize: Int = types.sumOf { it.minSize }

    fun decode(payload: ByteArray): IncomingPacketBody {
        require(payload.size >= minPayloadSize) {
            "Payload too small for $name (id=$packetId): size=${payload.size} min=$minPayloadSize types=$types"
        }
        val r = PacketReader(payload)
        @Suppress("UNCHECKED_CAST")
        val pkt = (codec as PacketCodec<IncomingPacketBody>).decode(r)
        r.requireFullyConsumed()
        return pkt
    }

    fun handle(call: PacketCallContext, body: IncomingPacketBody) {
        val h = handler ?: return
        require(clazz.isInstance(body)) { "Body class mismatch: expected=$clazz got=${body::class}" }
        @Suppress("UNCHECKED_CAST")
        (h as IncomingPacketHandler<IncomingPacketBody>).handle(call, body)
    }

    companion object {
        private val byId = entries.associateBy { it.packetId }
        private val byClass = entries.associateBy { it.clazz }

        fun byId(id: Int): IncomingPackets? = byId[id]
        fun byClass(clazz: KClass<out IncomingPacketBody>): IncomingPackets? = byClass[clazz]
    }
}