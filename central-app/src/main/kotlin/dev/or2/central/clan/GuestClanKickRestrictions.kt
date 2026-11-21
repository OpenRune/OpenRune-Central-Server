package dev.or2.central.clan

/** Volatile Central authority for guest-chat kicks. Permanent bans remain in clan_bans. */
object GuestClanKickRestrictions {
    private const val RESTRICTION_MILLIS: Long = 60L * 60L * 1000L
    private val expiryByClan = mutableMapOf<Long, MutableMap<Int, Long>>()

    @Synchronized
    fun recordKick(clanId: Long, characterId: Int) {
        if (clanId <= 0L || characterId <= 0) return
        expiryByClan.getOrPut(clanId) { mutableMapOf() }[characterId] =
            System.currentTimeMillis() + RESTRICTION_MILLIS
    }

    @Synchronized
    fun isBlocked(clanId: Long, characterId: Int): Boolean {
        val members = expiryByClan[clanId] ?: return false
        val expiresAt = members[characterId] ?: return false
        if (expiresAt > System.currentTimeMillis()) return true
        members.remove(characterId)
        if (members.isEmpty()) expiryByClan.remove(clanId)
        return false
    }
}
