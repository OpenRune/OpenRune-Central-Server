package dev.openrune.central.packet.codec

import dev.openrune.central.LogsResponse
import dev.openrune.central.PlayerUID
import dev.openrune.central.details.DetailsStore
import dev.openrune.central.packet.IncomingPacketHandler
import dev.openrune.central.packet.PacketCallContext
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.packet.model.LogoutIncoming
import dev.openrune.central.packet.model.LogoutResponseOutgoing
import dev.openrune.central.world.WorldManager
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object LogoutRequestCodec : PacketCodec<LogoutIncoming>, IncomingPacketHandler<LogoutIncoming> {

    private val logger = LoggerFactory.getLogger(LogoutRequestCodec::class.java)

    override fun decodeBody(r: PacketReader, requestId: Long): LogoutIncoming {
        val uid = PlayerUID(r.readLong())
        val account = r.readString()
        val previousXteas = r.readIntListU8()
        return LogoutIncoming(uid = uid, account = account, previousXteas = previousXteas, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: LogoutIncoming) {
        w.writeLong(body.uid.value)
        w.writeString(body.account)
        w.writeIntListU8(body.previousXteas)
    }

    override fun handle(call: PacketCallContext, body: LogoutIncoming) {


        val account = body.account.trim()
        if (body.uid.value == 0L) {
            logger.info("logout: malformed uid=${body.uid} accountLen=${account.length} xteasSize=${body.previousXteas.size} (world=${call.worldId})")
            call.respond(LogoutResponseOutgoing(LogsResponse.FAILED,"malformed uid == 0"))
            return
        }

        if (body.previousXteas.isNotEmpty() && body.previousXteas.size != 4) {
            logger.info("logout: malformed uid=${body.uid} accountLen=${account.length} xteasSize=${body.previousXteas.size} (world=${call.worldId})")
            call.respond(LogoutResponseOutgoing(LogsResponse.FAILED,"malformed uid"))
            return
        }

        runBlocking {
            DetailsStore.saveLogout(body.uid.value, account, body.previousXteas)
            call.respond(LogoutResponseOutgoing(LogsResponse.SUCCESS))
            WorldManager.setOffline(call.worldId, body.uid.value)
        }
    }
}