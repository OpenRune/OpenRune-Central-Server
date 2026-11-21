package dev.or2.central.friendchat

/** Volatile OSRS Friends Chat kick restrictions. A channel empties => all its restrictions end. */
object FriendChatKickRestrictions {
    private const val DURATION_MS = 60L * 60L * 1000L
    private val expiresAt = mutableMapOf<Long, MutableMap<Int, Long>>()

    @Synchronized
    fun recordKick(channelId: Long, characterId: Int) {
        if (channelId <= 0L || characterId <= 0) return
        expiresAt.getOrPut(channelId) { mutableMapOf() }[characterId] =
            System.currentTimeMillis() + DURATION_MS
    }

    @Synchronized
    fun isBlocked(channelId: Long, characterId: Int): Boolean {
        val channel = expiresAt[channelId] ?: return false
        val deadline = channel[characterId] ?: return false
        if (deadline > System.currentTimeMillis()) return true
        channel.remove(characterId)
        if (channel.isEmpty()) expiresAt.remove(channelId)
        return false
    }

    @Synchronized
    fun clearChannel(channelId: Long) {
        expiresAt.remove(channelId)
    }
}
