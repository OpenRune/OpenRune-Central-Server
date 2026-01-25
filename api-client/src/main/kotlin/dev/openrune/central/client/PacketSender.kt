package dev.openrune.central.client

import dev.openrune.central.packet.IncomingPacketIds
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LoginRequestIncoming
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.packet.model.LogsRequestIncoming
import dev.openrune.central.packet.model.LogsResponseOutgoing
import dev.openrune.central.packet.model.LogoutIncoming
import dev.openrune.central.packet.model.LogoutResponseOutgoing
import dev.openrune.central.packet.model.PlayerSaveLoadRequestIncoming
import dev.openrune.central.packet.model.PlayerSaveLoadResponseOutgoing
import dev.openrune.central.packet.model.PlayerSaveOutgoing
import dev.openrune.central.packet.model.PlayerSaveUpsertRequestIncoming
import dev.openrune.central.packet.registry.OutgoingPackets

/**
 * Client-side (world-side) sender for packets that are *incoming to central*.
 *
 * This intentionally lives in `api-client` so the shared `api` module does NOT need to
 * depend on server-only registries like `IncomingPackets`.
 */
class PacketSender internal constructor(
    private val conn: CentralConnection
) {

    private fun sendWithRequestId(opcode: Int, requestId: Long, writeBody: PacketWriter.() -> Unit) {
        conn.sendPacket(opcode) {
            writeLong(requestId)
            writeBody()
        }
    }

    fun sendLoginRequest(req: LoginRequestIncoming) {
        sendWithRequestId(IncomingPacketIds.LOGIN_REQUEST, req.requestId) {
            writeString(req.username)
            writeString(req.password)
            writeIntListU8(req.xteas)
        }
    }

    /**
     * Send a login request and handle the next LOGIN_RESPONSE as the callback.
     *
     * Note: this is a "next response wins" helper (no correlation id in the protocol).
     */
    fun sendLoginRequest(req: LoginRequestIncoming, onResponse: (LoginResponseOutgoing) -> Unit) {
        val requestId = conn.nextRequestId()
        val fut = conn.awaitResponse<LoginResponseOutgoing>(OutgoingPackets.LOGIN_RESPONSE, requestId)
        sendLoginRequest(req.copy(requestId = requestId))
        fut.whenComplete { pkt, _ ->
            if (pkt != null) onResponse(pkt)
        }
    }

    fun sendLogoutRequest(req: LogoutIncoming) {
        sendWithRequestId(IncomingPacketIds.LOGOUT_REQUEST, req.requestId) {
            writeLong(req.uid.value)
            writeString(req.account)
            writeIntListU8(req.previousXteas)
        }
    }

    fun sendPlayerLogout(req: LogoutIncoming, onResponse: (LogoutResponseOutgoing) -> Unit) {
        val requestId = conn.nextRequestId()
        val fut = conn.awaitResponse<LogoutResponseOutgoing>(OutgoingPackets.LOGOUT, requestId)
        sendLogoutRequest(req.copy(requestId = requestId))
        fut.whenComplete { pkt, _ ->
            if (pkt != null) onResponse(pkt)
        }
    }

    fun sendPlayerSaveLoadRequest(req: PlayerSaveLoadRequestIncoming) {
        sendWithRequestId(IncomingPacketIds.PLAYER_SAVE_LOAD_REQUEST, req.requestId) {
            writeLong(req.uid)
            writeString(req.account)
        }
    }

    /**
     * Send a player save load request and handle the next PLAYER_SAVE_LOAD_RESPONSE as the callback.
     *
     * Note: this is a "next response wins" helper (no correlation id in the protocol).
     */
    fun sendPlayerSaveLoadRequest(req: PlayerSaveLoadRequestIncoming, onResponse: (PlayerSaveLoadResponseOutgoing) -> Unit) {
        val requestId = conn.nextRequestId()
        val fut = conn.awaitResponse<PlayerSaveLoadResponseOutgoing>(OutgoingPackets.PLAYER_SAVE_LOAD_RESPONSE, requestId)
        sendPlayerSaveLoadRequest(req.copy(requestId = requestId))
        fut.whenComplete { pkt, _ ->
            if (pkt != null) onResponse(pkt)
        }
    }

    fun sendPlayerSave(req: PlayerSaveUpsertRequestIncoming) {
        sendWithRequestId(IncomingPacketIds.PLAYER_SAVE_UPSERT_REQUEST, req.requestId) {
            writeLong(req.uid)
            writeString(req.account)
            writeString(req.data)
        }
    }

    /**
     * Send a logs request and handle the next LOGS_RESPONSE as the callback.
     *
     * Note: this is a "next response wins" helper (no correlation id in the protocol).
     */
    fun sendPlayerSave(req: PlayerSaveUpsertRequestIncoming, onResponse: (PlayerSaveOutgoing) -> Unit) {
        val requestId = conn.nextRequestId()
        val fut = conn.awaitResponse<PlayerSaveOutgoing>(OutgoingPackets.PLAYER_SAVE_RESPONSE, requestId)

        println(req.data)

        sendPlayerSave(req.copy(requestId = requestId))
        fut.whenComplete { pkt, _ ->
            if (pkt != null) onResponse(pkt)
        }
    }

    fun sendLogsRequest(req: LogsRequestIncoming) {
        sendWithRequestId(IncomingPacketIds.LOGS_REQUEST, req.requestId) {
            writeString(req.data)
        }
    }

    /**
     * Send a logs request and handle the next LOGS_RESPONSE as the callback.
     *
     * Note: this is a "next response wins" helper (no correlation id in the protocol).
     */
    fun sendLogsRequest(req: LogsRequestIncoming, onResponse: (LogsResponseOutgoing) -> Unit) {
        val requestId = conn.nextRequestId()
        val fut = conn.awaitResponse<LogsResponseOutgoing>(OutgoingPackets.LOGS_RESPONSE, requestId)
        sendLogsRequest(req.copy(requestId = requestId))
        fut.whenComplete { pkt, _ ->
            if (pkt != null) onResponse(pkt)
        }
    }

    /**
     * Escape hatch for custom packets without touching the registry.
     */
    fun sendRaw(opcode: Int, writePayload: PacketWriter.() -> Unit) {
        conn.sendPacket(opcode, writePayload)
    }
}

