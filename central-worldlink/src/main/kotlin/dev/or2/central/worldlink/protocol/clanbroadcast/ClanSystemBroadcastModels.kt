package dev.or2.central.worldlink.protocol.clanbroadcast

/** Not ordinary live chat: an event from the authoritative game-content hook. */
data class ClanSystemBroadcastRequest(val clanId: Long, val settingId: Int, val sourceIsMainGame: Boolean, val message: String)
data class ClanSystemBroadcastPush(val clanId: Long, val settingId: Int, val sourceIsMainGame: Boolean, val sourceWorldId: Int, val message: String)
object ClanSystemBroadcastLimits { const val MAX_MESSAGE_BYTES = 512 }
