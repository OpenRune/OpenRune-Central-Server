package dev.or2.central.worldlink.protocol.livechat

/** Kind values are a wire contract. Persistent clan membership is not implied by live presence. */
object LiveChatKind {
    const val FRIENDS_CHAT = 0
    const val MAIN_CLAN = 1
    const val GUEST_CLAN = 2
    fun valid(value: Int): Boolean = value in 0..2
}

object LiveChatAction {
    const val JOIN = 1
    const val LEAVE = 2
    const val MESSAGE = 3
}

object LiveChatStatus {
    const val OK = 0
    const val NOT_ONLINE = 1
    const val NOT_ALLOWED = 2
    const val NOT_FOUND = 3
    const val FULL = 4
    const val NOT_JOINED = 5
    const val UNAVAILABLE = 6
}

object LiveChatLimits {
    const val MAX_MEMBERS = 500
    const val MAX_NAME_BYTES = 96
    const val MAX_MESSAGE_BYTES = 256
    const val MAX_ROSTER_BODY = 64 * 1024
}

data class LiveChatJoinRequest(val characterId: Int, val kind: Int, val channelId: Long)
data class LiveChatLeaveRequest(val characterId: Int, val kind: Int)
data class LiveChatMessageRequest(val characterId: Int, val kind: Int, val senderCrown: Int, val message: String)
data class LiveChatMemberWire(val characterId: Int, val displayName: String, val worldId: Int, val rank: Int)
/** Settings accompany Friends Chat rosters so an unchanged membership can still refresh its name. */
data class LiveFriendChatSettings(
    val ownerCharacterId: Int,
    val ownerNameKey: String,
    val displayName: String,
    val enterRank: Int,
    val talkRank: Int,
    val kickRank: Int,
    val lootShareRank: Int,
    val coinShareEnabled: Boolean,
    val maxMembers: Int,
)

data class LiveChatRosterPush(
    val kind: Int,
    val channelId: Long,
    val revision: Long,
    val members: List<LiveChatMemberWire>,
    val friendSettings: LiveFriendChatSettings? = null,
)
data class LiveChatResult(val action: Int, val status: Int, val kind: Int, val channelId: Long, val roster: LiveChatRosterPush? = null)
data class LiveChatMessagePush(
    val kind: Int,
    val channelId: Long,
    val senderCharacterId: Int,
    val senderDisplayName: String,
    val senderWorldId: Int,
    val senderRank: Int,
    val senderCrown: Int,
    val message: String,
)