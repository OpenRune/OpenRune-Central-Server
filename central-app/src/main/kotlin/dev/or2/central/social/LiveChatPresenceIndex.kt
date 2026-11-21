package dev.or2.central.social

import dev.or2.central.friendchat.FriendChatKickRestrictions

/**
 * Central-side volatile live-chat projection. Persistent clan/friends-chat settings do not live here.
 *
 * This class has no socket, database or OSRS-packet dependencies. Callers must
 * authenticate the character/world and resolve channel permissions before invoking [join].
 * Every mutation is serialized by the monitor, making snapshots and recipient-world sets coherent.
 */
class LiveChatPresenceIndex {
    enum class Kind { FRIEND_CHAT, MAIN_CLAN, GUEST_CLAN }

    data class ChannelKey(val kind: Kind, val id: Long) {
        init { require(id > 0L) { "Live channel ids must be positive." } }
    }

    data class Member(
        val characterId: Int,
        val displayName: String,
        val worldId: Int,
        val rank: Int,
    ) {
        init {
            require(characterId > 0)
            require(worldId > 0)
            require(displayName.isNotBlank())
        }
    }

    data class Roster(val channel: ChannelKey, val revision: Long, val members: List<Member>)

    sealed interface JoinResult {
        data class Joined(val roster: Roster, val replacedChannel: ChannelKey?) : JoinResult
        data class Full(val roster: Roster) : JoinResult
        data object StaleWorld : JoinResult
    }

    private val byChannel = mutableMapOf<ChannelKey, LinkedHashMap<Int, Member>>()
    private val channelByCharacterAndKind = mutableMapOf<Pair<Int, Kind>, ChannelKey>()
    // Distinguish Central process lifetimes. Old worlds may retain the last accepted revision
    // across a Central restart; a fresh process must not restart the wire revision at zero.
    private val revisionEpoch = System.currentTimeMillis() * 1_000_000L
    private val revisions = mutableMapOf<ChannelKey, Long>()

    /**
     * Supply the authoritative online world id from OnlinePresenceIndex. When it differs from
     * [member.worldId], a delayed request from the previous world must NOT change the roster.
     * A character may occupy one live channel per [Kind].
     */
    @Synchronized
    fun join(channel: ChannelKey, member: Member, authoritativeWorldId: Int?, capacity: Int): JoinResult {
        if (authoritativeWorldId != member.worldId) return JoinResult.StaleWorld
        require(capacity > 0)
        val playerKey = member.characterId to channel.kind
        val previous = channelByCharacterAndKind[playerKey]
        val target = byChannel[channel]
        if (target?.containsKey(member.characterId) != true && (target?.size ?: 0) >= capacity) {
            return JoinResult.Full(snapshotLocked(channel))
        }
        if (previous != null && previous != channel) removeLocked(previous, member.characterId)
        val members = byChannel.getOrPut(channel) { linkedMapOf() }
        if (members[member.characterId] != member) {
            members[member.characterId] = member
            bump(channel)
        }
        channelByCharacterAndKind[playerKey] = channel
        return JoinResult.Joined(snapshotLocked(channel), previous?.takeIf { it != channel })
    }

    /** Do not remove a newly-hopped character when an old world sends its delayed leave. */
    @Synchronized
    fun leave(kind: Kind, characterId: Int, departingWorldId: Int): Roster? {
        if (characterId <= 0 || departingWorldId <= 0) return null
        val channel = channelByCharacterAndKind[characterId to kind] ?: return null
        if (byChannel[channel]?.get(characterId)?.worldId != departingWorldId) return null
        removeLocked(channel, characterId)
        return snapshotLocked(channel)
    }

    /** Returns the channels changed, for publishing fresh rosters after a world is disconnected. */
    @Synchronized
    fun removeWorld(worldId: Int): List<Roster> {
        if (worldId <= 0) return emptyList()
        val changed = linkedSetOf<ChannelKey>()
        for ((channel, members) in byChannel.toList()) {
            for (member in members.values.toList()) {
                if (member.worldId == worldId) {
                    removeLocked(channel, member.characterId)
                    changed += channel
                }
            }
        }
        return changed.map(::snapshotLocked)
    }

    /** Snapshot the currently occupied channel keys for timer-driven reconciliation. */
    @Synchronized
    fun activeChannels(): List<ChannelKey> = byChannel.keys.toList()

    /** Settings-only mutations need a newer roster revision even when membership is unchanged. */
    @Synchronized
    fun markSettingsChanged(channel: ChannelKey): Roster {
        if (byChannel[channel].isNullOrEmpty()) return snapshotLocked(channel)
        bump(channel)
        return snapshotLocked(channel)
    }

    @Synchronized
    fun snapshot(channel: ChannelKey): Roster = snapshotLocked(channel)

    @Synchronized
    fun currentChannel(kind: Kind, characterId: Int): ChannelKey? =
        channelByCharacterAndKind[characterId to kind]

    /** A clan's main and guest channels are one list on live, so each also reaches the other's worlds. */
    @Synchronized
    fun recipientWorlds(channel: ChannelKey): Set<Int> {
        val worlds = worldsOf(channel)
        clanSibling(channel)?.let { worlds += worldsOf(it) }
        return worlds
    }

    fun clanSibling(channel: ChannelKey): ChannelKey? = when (channel.kind) {
        Kind.MAIN_CLAN -> ChannelKey(Kind.GUEST_CLAN, channel.id)
        Kind.GUEST_CLAN -> ChannelKey(Kind.MAIN_CLAN, channel.id)
        Kind.FRIEND_CHAT -> null
    }

    private fun worldsOf(channel: ChannelKey): MutableSet<Int> =
        byChannel[channel]?.values?.mapTo(linkedSetOf()) { it.worldId } ?: linkedSetOf()

    @Synchronized
    fun isMember(channel: ChannelKey, characterId: Int, worldId: Int): Boolean =
        byChannel[channel]?.get(characterId)?.worldId == worldId

    private fun snapshotLocked(channel: ChannelKey): Roster =
        Roster(channel, revisions[channel] ?: revisionEpoch, byChannel[channel]?.values?.toList() ?: emptyList())

    private fun removeLocked(channel: ChannelKey, characterId: Int) {
        val members = byChannel[channel] ?: return
        if (members.remove(characterId) == null) return
        channelByCharacterAndKind.remove(characterId to channel.kind, channel)
        if (members.isEmpty()) {
            byChannel.remove(channel)
            if (channel.kind == Kind.FRIEND_CHAT) {
                FriendChatKickRestrictions.clearChannel(channel.id)
            }
        }
        bump(channel)
    }

    private fun bump(channel: ChannelKey) {
        revisions[channel] = (revisions[channel] ?: revisionEpoch) + 1L
    }
}