package dev.openrune.central.packet.codec

import dev.openrune.central.AppState
import dev.openrune.central.LogsResponse
import dev.openrune.central.details.DetailsStore
import dev.openrune.central.packet.IncomingPacketHandler
import dev.openrune.central.packet.PacketCallContext
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.PlayerSaveOutgoing
import dev.openrune.central.packet.model.PlayerSaveUpsertRequestIncoming
import dev.openrune.central.storage.JsonBucket
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object PlayerSaveUpsertRequestCodec : PacketCodec<PlayerSaveUpsertRequestIncoming>, IncomingPacketHandler<PlayerSaveUpsertRequestIncoming> {

    private val logger = LoggerFactory.getLogger(PlayerSaveUpsertRequestCodec::class.java)

    override fun decodeBody(r: PacketReader, requestId: Long): PlayerSaveUpsertRequestIncoming {
        val uid = r.readLong()
        val account = r.readString()
        val data = r.readString()
        return PlayerSaveUpsertRequestIncoming(uid = uid, account = account, data = data, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: PlayerSaveUpsertRequestIncoming) {
        w.writeLong(body.uid)
        w.writeString(body.account)
        w.writeString(body.data)
    }

    override fun handle(call: PacketCallContext, body: PlayerSaveUpsertRequestIncoming) {
        val account = body.account.trim()
        if (body.uid == 0L || body.data.isBlank()) {
            logger.info("player/save: malformed uid=${body.uid} accountLen=${account.length} dataLen=${body.data.length} (world=${call.worldId})")
            call.respond(PlayerSaveOutgoing(LogsResponse.SUCCESS,"player/save: malformed uid=${body.uid}"))
            return
        }

        runBlocking {
            val key = DetailsStore.playerSaveKey(body.uid, account)
            if (key == null) {
                logger.info("player/save: key not found uid=${body.uid} accountLen=${account.length} (world=${call.worldId})")
                call.respond(PlayerSaveOutgoing(LogsResponse.SUCCESS,"Player/save: key not found uid=${body.uid}"))
                return@runBlocking
            }

            call.respond(PlayerSaveOutgoing(LogsResponse.SUCCESS))
            AppState.storage.upsert(JsonBucket.PLAYER_SAVES, key, body.data)
        }
    }
}

