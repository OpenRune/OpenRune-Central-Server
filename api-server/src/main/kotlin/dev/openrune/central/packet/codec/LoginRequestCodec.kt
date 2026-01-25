package dev.openrune.central.packet.codec

import dev.openrune.central.PlayerLoadResponse
import dev.openrune.central.details.DetailsStore
import dev.openrune.central.packet.IncomingPacketHandler
import dev.openrune.central.packet.PacketCallContext
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LoginRequestIncoming
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.world.WorldManager
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object LoginRequestCodec : PacketCodec<LoginRequestIncoming>, IncomingPacketHandler<LoginRequestIncoming> {

    private val logger = LoggerFactory.getLogger(LoginRequestCodec::class.java)

    override fun decodeBody(r: PacketReader, requestId: Long): LoginRequestIncoming {
        val username = r.readString()
        val password = r.readString()
        val xteas = r.readIntListU8()
        return LoginRequestIncoming(username = username, password = password, xteas = xteas, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: LoginRequestIncoming) {
        w.writeString(body.username)
        w.writeString(body.password)
        w.writeIntListU8(body.xteas)
    }

    override fun handle(call: PacketCallContext, body: LoginRequestIncoming) {

        val username = body.username.trim()
        val password = body.password
        val xteas = body.xteas

        if (username.isEmpty()) {
            call.respond(LoginResponseOutgoing(PlayerLoadResponse.MALFORMED, null, requestId = body.requestId))
            return
        }

        val stableUid = DetailsStore.stableUidForLoginUsername(username)
        if (WorldManager.isOnlineAnywhere(stableUid)) {
            call.respond(LoginResponseOutgoing(PlayerLoadResponse.ALREADY_ONLINE, null, requestId = body.requestId))
            return
        }

        if (xteas.isNotEmpty() && xteas.size != 4) {
            call.respond(LoginResponseOutgoing(result = PlayerLoadResponse.MALFORMED, login = null, requestId = body.requestId))
            return
        }

        if (password.isEmpty() && xteas.isEmpty()) {
            call.respond(LoginResponseOutgoing(result = PlayerLoadResponse.MALFORMED, login = null, requestId = body.requestId))
            return
        }

        val res = runBlocking {
            DetailsStore.loadOrCreate(usernameRaw = body.username, password = body.password, xteas = body.xteas)
        }

        if (res.result == PlayerLoadResponse.NEW_ACCOUNT || res.result == PlayerLoadResponse.LOAD) {
            val uid = res.login?.linkedAccounts?.firstOrNull()?.uid?.value ?: 0L
            WorldManager.setOnline(call.worldId, uid)
        }

        call.respond(LoginResponseOutgoing(result = res.result, login = res.login, requestId = body.requestId))
    }
}