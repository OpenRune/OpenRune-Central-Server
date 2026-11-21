package dev.or2.central.worldlink.handlers

import dev.or2.central.clan.GuestClanKickRestrictions
import dev.or2.central.clan.ClanStore
import dev.or2.central.clan.CentralClanRepository
import dev.or2.central.clan.ClanAdminAuthorizer
import dev.or2.central.friendchat.CentralFriendChatRepository
import dev.or2.central.friendchat.FriendChatEffectiveSettings
import dev.or2.central.friendchat.FriendChatKickRestrictions
import dev.or2.central.social.LiveChatPresenceIndex
import dev.or2.central.social.OnlinePresenceIndex
import dev.or2.central.social.SocialGraphStore
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.livechat.*
import dev.or2.central.worldlink.protocol.friendchat.FriendChatKickPayload
import dev.or2.central.worldlink.protocol.packets.incoming.impl.*
import org.slf4j.LoggerFactory

/** Central's authenticated and authoritative live-chat transport. */
class LiveChatHandler(
    private val presence: OnlinePresenceIndex,
    private val live: LiveChatPresenceIndex,
    private val friends: CentralFriendChatRepository,
    private val clans: ClanStore,
    private val clanRepository: CentralClanRepository,
    private val admins: ClanAdminAuthorizer,
    private val connections: WorldConnectionRegistry,
    private val socialGraph: SocialGraphStore,
    /** Friends Chat settings in force (Setup changes take effect 60 seconds after a batch starts). */
    private val effectiveFriendSettings: FriendChatEffectiveSettings,
) {
    private val log = LoggerFactory.getLogger(LiveChatHandler::class.java)

    fun handle(connection: WorldConnection, opcode: Int, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) return respond(LiveChatAction.JOIN, LiveChatKind.FRIENDS_CHAT, 0, LiveChatStatus.NOT_ALLOWED)
        return try {
            when (opcode) {
                WorldOpcodes.OP_WORLD_LIVE_CHAT_JOIN -> join(connection.worldId, WorldLiveChatJoinPacket.decode(input))
                WorldOpcodes.OP_WORLD_LIVE_CHAT_LEAVE -> leave(connection.worldId, WorldLiveChatLeavePacket.decode(input))
                WorldOpcodes.OP_WORLD_LIVE_CHAT_MESSAGE -> message(connection.worldId, WorldLiveChatMessagePacket.decode(input))
                else -> respond(LiveChatAction.JOIN, LiveChatKind.FRIENDS_CHAT, 0, LiveChatStatus.NOT_ALLOWED)
            }
        } catch (e: IllegalArgumentException) {
            log.debug("Invalid live-chat request", e)
            HandlerResult.CloseSilent
        } catch (t: Exception) {
            log.warn("Live-chat request failed opcode={} world={}", opcode, connection.worldId, t)
            respond(actionFor(opcode), LiveChatKind.FRIENDS_CHAT, 0, LiveChatStatus.UNAVAILABLE)
        }
    }

    private fun join(worldId: Int, request: LiveChatJoinRequest): HandlerResult {
        if (!presence.isOnline(request.characterId, worldId)) return respond(LiveChatAction.JOIN, request.kind, request.channelId, LiveChatStatus.NOT_ONLINE)
        val resolved = resolve(request.kind, request.channelId, request.characterId)
            ?: return respond(LiveChatAction.JOIN, request.kind, request.channelId, LiveChatStatus.NOT_ALLOWED)
        val channel = key(request.kind, request.channelId)
        val previous = live.currentChannel(channel.kind, request.characterId)
        val before = previous?.let(live::recipientWorlds).orEmpty() + live.recipientWorlds(channel)
        val joined = live.join(channel, LiveChatPresenceIndex.Member(request.characterId, resolved.name, worldId, resolved.rank), presence.worldFor(request.characterId), resolved.capacity)
        return when (joined) {
            is LiveChatPresenceIndex.JoinResult.StaleWorld -> respond(LiveChatAction.JOIN, request.kind, request.channelId, LiveChatStatus.NOT_ONLINE)
            is LiveChatPresenceIndex.JoinResult.Full -> respond(LiveChatAction.JOIN, request.kind, request.channelId, LiveChatStatus.FULL)
            is LiveChatPresenceIndex.JoinResult.Joined -> {
                joined.replacedChannel?.let { publish(it, before) }
                publish(channel, before + worldId)
                HandlerResult.Reply(LiveChatPackets.encodeResult(LiveChatResult(LiveChatAction.JOIN, LiveChatStatus.OK, request.kind, request.channelId, roster(joined.roster))))
            }
        }
    }

    private fun leave(worldId: Int, request: LiveChatLeaveRequest): HandlerResult {
        val kind = kind(request.kind)
        val old = live.currentChannel(kind, request.characterId)
        val worlds = old?.let(live::recipientWorlds).orEmpty()
        val updated = live.leave(kind, request.characterId, worldId)
        if (old != null && updated != null) publish(old, worlds + worldId)
        return respond(LiveChatAction.LEAVE, request.kind, old?.id ?: 0L,
            if (updated == null) LiveChatStatus.NOT_JOINED else LiveChatStatus.OK)
    }

    private fun message(worldId: Int, request: LiveChatMessageRequest): HandlerResult {
        if (!presence.isOnline(request.characterId, worldId)) return respond(LiveChatAction.MESSAGE, request.kind, 0L, LiveChatStatus.NOT_ONLINE)
        val channel = live.currentChannel(kind(request.kind), request.characterId)
            ?: return respond(LiveChatAction.MESSAGE, request.kind, 0L, LiveChatStatus.NOT_JOINED)
        if (!live.isMember(channel, request.characterId, worldId)) return respond(LiveChatAction.MESSAGE, request.kind, channel.id, LiveChatStatus.NOT_JOINED)
        // Resolve rank again: a stale roster must never grant permission after a demotion/ban.
        val actor = resolve(request.kind, channel.id, request.characterId)
            ?: return respond(LiveChatAction.MESSAGE, request.kind, channel.id, LiveChatStatus.NOT_ALLOWED)
        if (!actor.canTalk) return respond(LiveChatAction.MESSAGE, request.kind, channel.id, LiveChatStatus.NOT_ALLOWED)
        val payload = LiveChatMessagePush(request.kind, channel.id, request.characterId, actor.name, worldId, actor.rank, request.senderCrown, request.message)
        val frame = LiveChatPackets.encodeMessagePush(payload)
        val worlds = live.recipientWorlds(channel)
        if (worlds.any { !connections.hasActivePushChannel(it) }) return respond(LiveChatAction.MESSAGE, request.kind, channel.id, LiveChatStatus.UNAVAILABLE)
        worlds.forEach { connections.push(it, frame) }
        return respond(LiveChatAction.MESSAGE, request.kind, channel.id, LiveChatStatus.OK)
    }

    /**
     * Recheck every live participant against the existing authoritative channel rules.
     * Call only after a successful persistent moderation/settings mutation.
     * No game-client membership or clan persistence is changed here.
     */
    fun reconcileChannel(kindCode: Int, channelId: Long) {
        if (channelId <= 0L) return
        val channel = key(kindCode, channelId)
        val before = live.snapshot(channel)
        if (before.members.isEmpty()) return
        val previousWorlds = before.members.mapTo(linkedSetOf()) { it.worldId }
        for (member in before.members) {
            val authoritativeWorld = presence.worldFor(member.characterId)
            val actor = resolve(kindCode, channelId, member.characterId)
            if (authoritativeWorld != member.worldId || actor == null) {
                live.leave(channel.kind, member.characterId, member.worldId)
            } else if (actor.rank != member.rank || actor.name != member.displayName) {
                live.join(
                    channel,
                    LiveChatPresenceIndex.Member(member.characterId, actor.name, member.worldId, actor.rank),
                    authoritativeWorld,
                    actor.capacity,
                )
            }
        }
        if (live.snapshot(channel).revision != before.revision) publish(channel, previousWorlds)
    }

    /** Publish fresh settings to all worlds hosting this channel, regardless of roster changes. */
    fun publishFriendChatSettings(channelId: Long) {
        if (channelId <= 0L) return
        val channel = key(LiveChatKind.FRIENDS_CHAT, channelId)
        val recipients = live.recipientWorlds(channel)
        if (recipients.isEmpty()) return
        live.markSettingsChanged(channel)
        publish(channel, recipients)
    }

    /**
     * Recheck an owner's currently occupied Friends Chat after their Ignore List changes.
     * No changes to clan channels, friend-chat settings, or persistent membership.
     */
    fun reconcileFriendChatOwnerIgnore(ownerCharacterId: Int) {
        if (ownerCharacterId <= 0) return
        val channels = live.activeChannels().filter {
            it.kind == LiveChatPresenceIndex.Kind.FRIEND_CHAT
        }
        for (channel in channels) {
            if (friends.loadById(channel.id)?.ownerCharacterId != ownerCharacterId) continue
            reconcileChannel(LiveChatKind.FRIENDS_CHAT, channel.id)
        }
    }

    /**
     * Ownership timers can disband a clan without entering ClanHandler. Reconcile
     * occupied main/guest channels after timer processing, including channels
     * whose persistent clan has just been deleted.
     */
    fun reconcileTimedClanChanges() {
        val active = live.activeChannels().filter {
            it.kind == LiveChatPresenceIndex.Kind.MAIN_CLAN ||
                    it.kind == LiveChatPresenceIndex.Kind.GUEST_CLAN
        }
        for (channel in active) {
            runCatching { reconcileChannel(wireKind(channel.kind), channel.id) }
                .onFailure { log.warn("Failed timer live-chat reconciliation for {}", channel, it) }
        }
    }

    /**
     * Guest selection CLEAR returns clanId=0, so the old live channel must be
     * obtained by character ID. This revalidates rather than blindly removing:
     * selection and live chat remain two distinct states.
     */
    fun reconcileGuestSelection(characterId: Int) {
        if (characterId <= 0) return
        val previous = live.currentChannel(LiveChatPresenceIndex.Kind.GUEST_CLAN, characterId)
            ?: return
        reconcileChannel(LiveChatKind.GUEST_CLAN, previous.id)
    }

    /**
     * Returns null only after Central has approved and applied the kick.
     * This runs on Central's request handler thread, never the game thread.
     */
    fun kickFriendChat(worldId: Int, request: FriendChatKickPayload): Int? {
        val denial = WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED
        if (!presence.isOnline(request.characterId, worldId)) return WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN
        val channel = key(LiveChatKind.FRIENDS_CHAT, request.channelId)
        if (!live.isMember(channel, request.characterId, worldId)) return denial
        val settings = effectiveFriendSettings.load(request.channelId) ?: return WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND
        val roster = live.snapshot(channel)
        val kicker = roster.members.firstOrNull { it.characterId == request.characterId } ?: return denial
        val authoritativeKicker = resolve(LiveChatKind.FRIENDS_CHAT, request.channelId, kicker.characterId) ?: return denial
        val target = roster.members.firstOrNull { it.displayName.equals(request.targetName.trim(), ignoreCase = true) }
            ?: return WorldOpcodes.FRIEND_CHAT_FAIL_TARGET_NOT_FOUND
        if (target.characterId == request.characterId) return denial
        val authoritativeTarget = resolve(LiveChatKind.FRIENDS_CHAT, request.channelId, target.characterId)
            ?: return WorldOpcodes.FRIEND_CHAT_FAIL_TARGET_NOT_FOUND
        val mod = admins.isJagexModerator(request.characterId)
        val targetMod = admins.isJagexModerator(target.characterId)
        if (targetMod || (!mod &&
                    (authoritativeKicker.rank < settings.minKick ||
                            authoritativeKicker.rank <= authoritativeTarget.rank))) return denial
        val targetWorld = presence.worldFor(target.characterId)
            ?: return WorldOpcodes.FRIEND_CHAT_FAIL_TARGET_NOT_FOUND
        if (targetWorld != target.worldId) return WorldOpcodes.FRIEND_CHAT_FAIL_TARGET_NOT_FOUND

        // Clear remembered auto-rejoin before publishing removal. Failure must not produce a partial kick.
        if (!friends.clearRestoreTarget(target.characterId)) return WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE
        val recipientWorlds = live.recipientWorlds(channel)
        live.leave(channel.kind, target.characterId, targetWorld)
            ?: return WorldOpcodes.FRIEND_CHAT_FAIL_TARGET_NOT_FOUND
        // A kick does not restrict rejoining once the channel is empty.
        if (live.snapshot(channel).members.isNotEmpty()) {
            FriendChatKickRestrictions.recordKick(request.channelId, target.characterId)
        }
        publish(channel, recipientWorlds + targetWorld)
        return null
    }

    /** Caller supplies worlds that also require an empty/replaced roster. */
    private fun publish(channel: LiveChatPresenceIndex.ChannelKey, priorWorlds: Collection<Int>) {
        val state = live.snapshot(channel)
        val wire = roster(state)
        val frame = LiveChatPackets.encodeRoster(wire)
        val worlds = (priorWorlds + state.members.map { it.worldId } + live.recipientWorlds(channel)).distinct()
        worlds.forEach { connections.push(it, frame) }
        // Each world builds one clan list from both rosters, so it must hold the other one too.
        live.clanSibling(channel)?.let { sibling ->
            val siblingFrame = LiveChatPackets.encodeRoster(roster(live.snapshot(sibling)))
            worlds.forEach { connections.push(it, siblingFrame) }
        }
    }

    private fun roster(roster: LiveChatPresenceIndex.Roster) = LiveChatRosterPush(
        wireKind(roster.channel.kind), roster.channel.id, roster.revision,
        roster.members.map { LiveChatMemberWire(it.characterId, it.displayName, it.worldId, it.rank) },
        friendSettings = if (roster.channel.kind == LiveChatPresenceIndex.Kind.FRIEND_CHAT) {
            effectiveFriendSettings.load(roster.channel.id)?.let { row ->
                LiveFriendChatSettings(
                    ownerCharacterId = row.ownerCharacterId ?: 0,
                    ownerNameKey = row.ownerNameKey,
                    displayName = row.displayName,
                    enterRank = row.minJoin,
                    talkRank = row.minTalk,
                    kickRank = row.minKick,
                    lootShareRank = row.lootShareRank,
                    coinShareEnabled = row.coinShareEnabled,
                    maxMembers = row.maxMembers,
                )
            }
        } else null,
    )

    private data class Actor(val name: String, val rank: Int, val canTalk: Boolean, val capacity: Int)

    private fun resolve(kind: Int, channelId: Long, characterId: Int): Actor? { return when (kind) {
        LiveChatKind.FRIENDS_CHAT -> {
            val row = effectiveFriendSettings.load(channelId) ?: return null
            val player = friends.findCharacter(characterId) ?: return null
            val isMod = admins.isJagexModerator(characterId)
            val rank = when {
                isMod -> 127
                row.ownerCharacterId == characterId -> 7
                else -> friends.explicitRank(row.id, characterId) ?: if (row.ownerCharacterId?.let { friends.isFriend(it, characterId) } == true) 0 else -1
            }
            if (!isMod && row.ownerCharacterId?.let { (socialGraph.isIgnored(it, characterId) || friends.isIgnored(it, characterId)) } == true) return null
            if (!isMod && FriendChatKickRestrictions.isBlocked(row.id, characterId)) return null
            if (!isMod && rank < row.minJoin) return null
            Actor(player.displayName, rank, isMod || rank >= row.minTalk, row.maxMembers.coerceIn(1, LiveChatLimits.MAX_MEMBERS))
        }
        LiveChatKind.MAIN_CLAN -> {
            val state = clans.get(channelId) ?: return null
            val member = state.members.firstOrNull { it.characterId == characterId } ?: return null
            val isMod = admins.isJagexModerator(characterId)
            Actor(member.displayName, if (isMod) 127 else member.rank, isMod || member.rank >= state.clan.talkRank, LiveChatLimits.MAX_MEMBERS)
        }
        LiveChatKind.GUEST_CLAN -> {
            val state = clans.get(channelId) ?: return null
            val player = clanRepository.findCharacter(characterId) ?: return null
            val isMod = admins.isJagexModerator(characterId)
            if (clanRepository.guestClanIdForCharacter(characterId) != channelId) return null
            if (state.members.any { it.characterId == characterId }) return null
            if (!isMod && (!state.clan.allowUnaffined || state.bans.any { it.characterId == characterId || it.displayName.equals(player.displayName, true) })) return null
            if (!isMod && GuestClanKickRestrictions.isBlocked(channelId, characterId)) return null
            Actor(player.displayName, if (isMod) 127 else -1, isMod || state.clan.talkRank <= -1, LiveChatLimits.MAX_MEMBERS)
        }
        else -> null
    } }

    private fun key(kind: Int, channelId: Long) = LiveChatPresenceIndex.ChannelKey(kind(kind), channelId)
    private fun kind(kind: Int) = when (kind) {
        LiveChatKind.FRIENDS_CHAT -> LiveChatPresenceIndex.Kind.FRIEND_CHAT
        LiveChatKind.MAIN_CLAN -> LiveChatPresenceIndex.Kind.MAIN_CLAN
        LiveChatKind.GUEST_CLAN -> LiveChatPresenceIndex.Kind.GUEST_CLAN
        else -> throw IllegalArgumentException("Invalid live chat kind")
    }
    private fun wireKind(kind: LiveChatPresenceIndex.Kind) = when (kind) {
        LiveChatPresenceIndex.Kind.FRIEND_CHAT -> LiveChatKind.FRIENDS_CHAT
        LiveChatPresenceIndex.Kind.MAIN_CLAN -> LiveChatKind.MAIN_CLAN
        LiveChatPresenceIndex.Kind.GUEST_CLAN -> LiveChatKind.GUEST_CLAN
    }
    private fun actionFor(opcode: Int) = when (opcode) {
        WorldOpcodes.OP_WORLD_LIVE_CHAT_LEAVE -> LiveChatAction.LEAVE
        WorldOpcodes.OP_WORLD_LIVE_CHAT_MESSAGE -> LiveChatAction.MESSAGE
        else -> LiveChatAction.JOIN
    }
    private fun respond(action: Int, kind: Int, channelId: Long, status: Int): HandlerResult =
        HandlerResult.Reply(LiveChatPackets.encodeResult(LiveChatResult(action, status, kind, channelId)))
}