package dev.or2.central.friendchat

import dev.or2.central.account.Rights
import dev.or2.central.social.OnlinePresenceIndex
import dev.or2.central.social.SocialGraphStore
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.friendchat.*
import java.sql.SQLException
import org.slf4j.LoggerFactory

/** Central authority for durable player Friends Chat configuration and explicit ranks. */
class FriendChatService(
    private val repository: CentralFriendChatRepository,
    private val presence: OnlinePresenceIndex,
    private val socialGraph: SocialGraphStore,
    /** Live paths (resolve, restore) read the settings in force; Setup (sync) reads the configured row. */
    private val effective: FriendChatEffectiveSettings,
) {
    private val log = LoggerFactory.getLogger(FriendChatService::class.java)

    sealed interface Reply {
        data class Ok(val frame: ByteArray) : Reply
        data class Fail(val frame: ByteArray) : Reply
    }

    fun handleSync(worldId: Int, payload: FriendChatCharacterPayload): Reply {
        if (!authorized(worldId, payload.characterId)) return syncFail(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
        return try {
            val row = repository.ensurePlayerChannel(payload.characterId)
                ?: return syncFail(WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND)
            Reply.Ok(FriendChatPackets.encodeSyncOk(row.toSnapshot()))
        } catch (t: Throwable) {
            log.warn("Friends Chat sync failed for character {}", payload.characterId, t)
            syncFail(WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
        }
    }

    fun handleResolve(worldId: Int, payload: FriendChatResolvePayload): Reply {
        if (!authorized(worldId, payload.characterId)) return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
        return try {
            val row = repository.resolveByOwnerName(payload.ownerName)?.let(effective::inForce)
                ?: return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND)
            val actor = repository.findCharacter(payload.characterId)
                ?: return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
            val rank = resolveBaseRank(row, payload.characterId)
            if (!actor.rights.isAtLeast(Rights.ADMINISTRATOR) && isBlocked(row, payload.characterId)) {
                return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
            }
            if (!actor.rights.isAtLeast(Rights.ADMINISTRATOR) && !canEnter(row, rank)) {
                return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_RANK_REQUIRED)
            }
            if (!repository.setRestoreTarget(payload.characterId, row.id)) {
                return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
            }
            Reply.Ok(FriendChatPackets.encodeResolveOk(FriendChatResolveReply(row.toSnapshot(), rank)))
        } catch (t: Throwable) {
            log.warn("Friends Chat resolve failed for character {} owner='{}'", payload.characterId, payload.ownerName, t)
            fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
        }
    }

    fun handleRestore(worldId: Int, payload: FriendChatCharacterPayload): Reply {
        if (!authorized(worldId, payload.characterId)) {
            return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
        }
        return try {
            val row = repository.loadRestoreTarget(payload.characterId)?.let(effective::inForce)
                ?: return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND)
            val actor = repository.findCharacter(payload.characterId)
                ?: return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
            val rank = resolveBaseRank(row, payload.characterId)
            if (!actor.rights.isAtLeast(Rights.ADMINISTRATOR) && isBlocked(row, payload.characterId)) {
                return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
            }
            if (!actor.rights.isAtLeast(Rights.ADMINISTRATOR) && !canEnter(row, rank)) {
                repository.clearRestoreTarget(payload.characterId)
                return fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, WorldOpcodes.FRIEND_CHAT_FAIL_RANK_REQUIRED)
            }
            Reply.Ok(
                FriendChatPackets.encodeRestoreOk(
                    FriendChatRestoreReply(
                        channelId = row.id,
                        ownerName = row.ownerNameKey,
                    )
                )
            )
        } catch (t: Throwable) {
            log.warn("Friends Chat restore lookup failed for character {}", payload.characterId, t)
            fail(WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
        }
    }

    fun handleClearRestore(worldId: Int, payload: FriendChatCharacterPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_FRIENDCHAT_CLEAR_RESTORE) {
            requireAuthorized(worldId, payload.characterId)
            if (!repository.clearRestoreTarget(payload.characterId)) {
                failNow(WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
            }
            0L
        }

    fun handleSetSettings(worldId: Int, payload: FriendChatSettingsPayload): Reply = mutation(WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_SETTINGS) {
        requireAuthorized(worldId, payload.characterId)
        val row = repository.ensurePlayerChannel(payload.characterId) ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND)
        if (row.channelType != FriendChatChannelTypeWire.PLAYER || row.ownerCharacterId != payload.characterId) {
            failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
        }
        val name = CentralFriendChatRepository.cleanChannelName(payload.displayName)
            ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NAME_INVALID)
        if (!validEnterTalk(payload.enterRank) || !validEnterTalk(payload.talkRank) || !validKick(payload.kickRank) || !validLoot(payload.lootShareRank)) {
            failNow(WorldOpcodes.FRIEND_CHAT_FAIL_BAD_RANK)
        }
        // Start (or join) the 60-second batch before writing, so live reads never see the new values early.
        effective.hold(row)
        if (!repository.updateSettings(row.id, name, payload.enterRank, payload.talkRank, payload.kickRank, payload.lootShareRank, payload.coinShareEnabled)) {
            failNow(WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
        }
        row.id
    }

    fun handleSetField(worldId: Int, payload: FriendChatFieldMutation): Reply {
        val action = WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_FIELD
        return try {
            requireAuthorized(worldId, payload.characterId)
            val row = repository.ensurePlayerChannel(payload.characterId)
                ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND)
            if (row.channelType != FriendChatChannelTypeWire.PLAYER || row.ownerCharacterId != payload.characterId) {
                failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
            }
            val value = when (payload.field) {
                FriendChatSettingField.DISPLAY_NAME -> CentralFriendChatRepository.cleanChannelName(payload.value)
                    ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NAME_INVALID)
                FriendChatSettingField.ENTER_RANK, FriendChatSettingField.TALK_RANK ->
                    payload.value.toIntOrNull()?.takeIf(::validEnterTalk)?.toString()
                        ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_BAD_RANK)
                FriendChatSettingField.KICK_RANK ->
                    payload.value.toIntOrNull()?.takeIf(::validKick)?.toString()
                        ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_BAD_RANK)
                FriendChatSettingField.LOOT_SHARE_RANK ->
                    payload.value.toIntOrNull()?.takeIf(::validLoot)?.toString()
                        ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_BAD_RANK)
                FriendChatSettingField.COIN_SHARE ->
                    payload.value.takeIf { it == "true" || it == "false" }
                        ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
                else -> failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
            }
            effective.hold(row)
            val changed = repository.updateSettingField(row.id, payload.field, value)
            Reply.Ok(FriendChatPackets.encodeFieldMutationOk(FriendChatFieldMutationReply(row.id, changed)))
        } catch (failure: FriendChatFailure) {
            fail(action, failure.reason)
        } catch (sql: SQLException) {
            log.warn("Friends Chat field mutation failed in PostgreSQL", sql)
            fail(action, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
        } catch (t: Throwable) {
            log.warn("Friends Chat field mutation failed", t)
            fail(action, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
        }
    }

    fun handleSetRank(worldId: Int, payload: FriendChatRankPayload): Reply = mutation(WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_RANK) {
        requireAuthorized(worldId, payload.characterId)
        if (!validAssignedRank(payload.rank)) failNow(WorldOpcodes.FRIEND_CHAT_FAIL_BAD_RANK)
        val channel = repository.resolveByOwnerName(payload.ownerName) ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND)
        val actor = repository.findCharacter(payload.characterId) ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
        val canManage = channel.ownerCharacterId == payload.characterId || actor.rights.isAtLeast(Rights.ADMINISTRATOR)
        if (!canManage) failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
        val target = repository.findCharacterByDisplayName(payload.targetName) ?: failNow(WorldOpcodes.FRIEND_CHAT_FAIL_TARGET_NOT_FOUND)
        if (target.characterId == channel.ownerCharacterId) failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
        repository.setRank(channel.id, target.characterId, payload.rank, payload.characterId)
        channel.id
    }

    fun updateDisplayName(characterId: Int, displayName: String) {
        runCatching { repository.updateOwnerNameKey(characterId, displayName) }
            .onFailure { log.warn("Unable to update Friends Chat owner key for character {}", characterId, it) }
    }

    private fun resolveBaseRank(channel: FriendChatChannelRow, characterId: Int): Int {
        val character = repository.findCharacter(characterId) ?: return -1
        // Jagex admin is rank 127, the same rule as LiveChatHandler.resolve, so join and roster agree.
        if (character.rights.isAtLeast(Rights.ADMINISTRATOR)) return 127
        if (channel.ownerCharacterId == characterId) return 7
        repository.explicitRank(channel.id, characterId)?.let { return it }
        val ownerId = channel.ownerCharacterId ?: return -1
        return if (repository.isFriend(ownerId, characterId)) 0 else -1
    }

    private fun isBlocked(channel: FriendChatChannelRow, characterId: Int): Boolean {
        val ownerId = channel.ownerCharacterId
        if (ownerId != null && (socialGraph.isIgnored(ownerId, characterId) || repository.isIgnored(ownerId, characterId))) return true
        return FriendChatKickRestrictions.isBlocked(channel.id, characterId)
    }

    private fun canEnter(channel: FriendChatChannelRow, rank: Int): Boolean {
        if (rank == 127 || rank == 7) return true
        if (channel.minJoin == -1) return true
        return rank >= channel.minJoin
    }

    private fun authorized(worldId: Int, characterId: Int): Boolean =
        characterId > 0 && presence.isOnline(characterId, worldId)

    private fun requireAuthorized(worldId: Int, characterId: Int) {
        if (!authorized(worldId, characterId)) failNow(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_LOGGED_IN)
    }

    private inline fun mutation(action: Int, block: () -> Long): Reply = try {
        Reply.Ok(FriendChatPackets.encodeOk(FriendChatMutationReply(action, block())))
    } catch (failure: FriendChatFailure) {
        fail(action, failure.reason)
    } catch (sql: SQLException) {
        log.warn("Friends Chat mutation {} failed in PostgreSQL", action, sql)
        fail(action, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
    } catch (t: Throwable) {
        log.warn("Friends Chat mutation {} failed", action, t)
        fail(action, WorldOpcodes.FRIEND_CHAT_FAIL_UNAVAILABLE)
    }

    private fun fail(action: Int, reason: Int): Reply.Fail =
        Reply.Fail(FriendChatPackets.encodeFail(FriendChatMutationFail(action, reason)))

    private fun syncFail(reason: Int): Reply.Fail = Reply.Fail(FriendChatPackets.encodeSyncFail(reason))
    private fun failNow(reason: Int): Nothing = throw FriendChatFailure(reason)

    private fun validEnterTalk(rank: Int): Boolean = rank in -1..7
    private fun validKick(rank: Int): Boolean = rank in 2..7
    private fun validLoot(rank: Int): Boolean = rank in -1..7
    private fun validAssignedRank(rank: Int): Boolean = rank == -1 || rank in 1..6

    private fun FriendChatChannelRow.toSnapshot() = FriendChatSnapshot(
        channelId = id,
        ownerCharacterId = ownerCharacterId ?: 0,
        ownerNameKey = ownerNameKey,
        displayName = displayName,
        channelType = channelType,
        groupingActivityId = groupingActivityId ?: 0,
        suggestedWorld = suggestedWorld ?: 0,
        enterRank = minJoin,
        talkRank = minTalk,
        kickRank = minKick,
        lootShareRank = lootShareRank,
        coinShareEnabled = coinShareEnabled,
        maxMembers = maxMembers,
    )

    private class FriendChatFailure(val reason: Int) : RuntimeException()
}