package dev.openrune.central.packet.codec

import dev.openrune.central.AppState
import dev.openrune.central.PlayerSaveLoadResponse
import dev.openrune.central.details.DetailsStore
import dev.openrune.central.packet.IncomingPacketHandler
import dev.openrune.central.packet.PacketCallContext
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.PlayerSaveLoadRequestIncoming
import dev.openrune.central.packet.model.PlayerSaveLoadResponseOutgoing
import dev.openrune.central.storage.JsonBucket
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object PlayerSaveLoadRequestCodec : PacketCodec<PlayerSaveLoadRequestIncoming>, IncomingPacketHandler<PlayerSaveLoadRequestIncoming> {

    private val logger = LoggerFactory.getLogger(PlayerSaveLoadRequestCodec::class.java)

    override fun decodeBody(r: PacketReader, requestId: Long): PlayerSaveLoadRequestIncoming {
        val uid = r.readLong()
        val account = r.readString()
        return PlayerSaveLoadRequestIncoming(uid = uid, account = account, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: PlayerSaveLoadRequestIncoming) {
        w.writeLong(body.uid)
        w.writeString(body.account)
    }

    override fun handle(call: PacketCallContext, body: PlayerSaveLoadRequestIncoming) {
        val account = body.account.trim()
        if (body.uid == 0L) {
            logger.info("player/load: malformed uid=${body.uid} accountLen=${account.length} (world=${call.worldId})")
            call.respond(PlayerSaveLoadResponseOutgoing(result = PlayerSaveLoadResponse.MALFORMED, data = null, requestId = body.requestId))
            return
        }

        val result = runBlocking {
            val key = DetailsStore.playerSaveKey(body.uid, account) ?: return@runBlocking PlayerSaveLoadResponseOutgoing(
                result = PlayerSaveLoadResponse.NOT_FOUND,
                data = null,
                requestId = body.requestId
            )

            val data = AppState.storage.get(JsonBucket.PLAYER_SAVES, key) ?: return@runBlocking PlayerSaveLoadResponseOutgoing(
                result = PlayerSaveLoadResponse.NOT_FOUND,
                data = null,
                requestId = body.requestId
            )
            PlayerSaveLoadResponseOutgoing(result = PlayerSaveLoadResponse.LOADED, data = data, requestId = body.requestId)
        }

        call.respond(result)
    }
}

