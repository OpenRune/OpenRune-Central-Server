package dev.or2.central.worldlink.protocol.friendchat

object FriendChatChannelTypeWire {
    const val PLAYER: Int = 0
    const val OFFICIAL_GROUPING: Int = 1
}

data class FriendChatSnapshot(
    val channelId: Long,
    val ownerCharacterId: Int,
    val ownerNameKey: String,
    val displayName: String,
    val channelType: Int,
    val groupingActivityId: Int,
    val suggestedWorld: Int,
    val enterRank: Int,
    val talkRank: Int,
    val kickRank: Int,
    val lootShareRank: Int,
    val coinShareEnabled: Boolean,
    val maxMembers: Int,
)

data class FriendChatCharacterPayload(val characterId: Int)
data class FriendChatResolvePayload(val characterId: Int, val ownerName: String)
data class FriendChatResolveReply(val snapshot: FriendChatSnapshot, val resolvedRank: Int)
data class FriendChatRestoreReply(val channelId: Long, val ownerName: String)
data class FriendChatSettingsPayload(
    val characterId: Int,
    val displayName: String,
    val enterRank: Int,
    val talkRank: Int,
    val kickRank: Int,
    val lootShareRank: Int,
    val coinShareEnabled: Boolean,
)
data class FriendChatRankPayload(
    val characterId: Int,
    val ownerName: String,
    val targetName: String,
    val rank: Int,
)
data class FriendChatMutationReply(val action: Int, val channelId: Long)
data class FriendChatMutationFail(val action: Int, val reason: Int)
/** Authoritative moderation request. Channel id prevents kicking from a stale channel selection. */
data class FriendChatKickPayload(val characterId: Int, val channelId: Long, val targetName: String)

/** Fields for an atomic single-field settings update. */
object FriendChatSettingField {
    const val DISPLAY_NAME = 1
    const val ENTER_RANK = 2
    const val TALK_RANK = 3
    const val KICK_RANK = 4
    const val LOOT_SHARE_RANK = 5
    const val COIN_SHARE = 6
}
data class FriendChatFieldMutation(val characterId: Int, val field: Int, val value: String)
data class FriendChatFieldMutationReply(val channelId: Long, val changed: Boolean)
