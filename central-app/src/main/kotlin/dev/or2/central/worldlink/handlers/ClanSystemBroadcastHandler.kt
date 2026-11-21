package dev.or2.central.worldlink.handlers

import dev.or2.central.clan.ClanStore
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.clanbroadcast.ClanSystemBroadcastPackets
import dev.or2.central.worldlink.protocol.clanbroadcast.ClanSystemBroadcastPush

/** Separate from live player chat. Source world is authenticated through HELLO. */
class ClanSystemBroadcastHandler(private val clans: ClanStore, private val connections: WorldConnectionRegistry) {
    fun handle(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone || !connection.channel.isActive || connection.worldId <= 0) return HandlerResult.Reply(ClanSystemBroadcastPackets.encodeAck(false))
        val request = try { ClanSystemBroadcastPackets.decodeRequest(input) } catch (_: Exception) {
            return HandlerResult.Reply(ClanSystemBroadcastPackets.encodeAck(false))
        }
        if (clans.get(request.clanId) == null || request.settingId !in 0..127) {
            return HandlerResult.Reply(ClanSystemBroadcastPackets.encodeAck(false))
        }
        val worlds = connections.registeredWorldIds().filter(connections::hasActivePushChannel)
        if (connection.worldId !in worlds) return HandlerResult.Reply(ClanSystemBroadcastPackets.encodeAck(false))
        val push = ClanSystemBroadcastPackets.encodePush(ClanSystemBroadcastPush(
            request.clanId, request.settingId, request.sourceIsMainGame, connection.worldId, request.message
        ))
        // Includes source world: there is no direct local delivery when Central is enabled.
        // The ACK means accepted for best-effort fan-out, not confirmed client delivery.
        // No local fallback or automatic retry: an uncertain outcome must not duplicate announcements.
        worlds.forEach { connections.push(it, push) }
        return HandlerResult.Reply(ClanSystemBroadcastPackets.encodeAck(true))
    }
}