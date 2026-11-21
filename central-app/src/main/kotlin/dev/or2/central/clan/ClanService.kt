package dev.or2.central.clan

import dev.or2.central.social.LiveChatPresenceIndex
import dev.or2.central.social.OnlinePresenceIndex
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame
import dev.or2.central.worldlink.protocol.readInboundFrame
import dev.or2.central.worldlink.protocol.clan.*
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.slf4j.LoggerFactory

/** Central authority for durable clan state. */
class ClanService(
    private val repository: CentralClanRepository,
    private val store: ClanStore,
    private val presence: OnlinePresenceIndex,
    private val connections: WorldConnectionRegistry,
    private val economyRepository: CentralClanEconomyRepository,
    private val adminAuthorizer: ClanAdminAuthorizer,
    private val liveChatPresence: LiveChatPresenceIndex,
) {
    private val log = LoggerFactory.getLogger(ClanService::class.java)

    private data class AdminContext(val characterId: Int, val clanId: Long)
    private val adminContext = ThreadLocal<AdminContext?>()

    sealed interface Reply {
        data class Ok(val frame: ByteArray) : Reply
        data class Fail(val frame: ByteArray) : Reply
    }

    fun handleSync(worldId: Int, payload: ClanSyncPayload): Reply {
        if (!authorized(worldId, payload.characterId)) return syncFail(WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
        val clanId = store.clanIdForCharacter(payload.characterId)
        if (clanId != null && payload.updateLastSeen) {
            runCatching {
                repository.touchMemberLastSeen(payload.characterId, currentRuneDay())
                refreshClan(clanId)
            }.onFailure { log.warn("Unable to update clan last-seen for character {}", payload.characterId, it) }
        }
        processOwnershipTimers()
        val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val invites = repository.pendingInvitesForCharacter(payload.characterId).take(ClanLimits.MAX_PENDING_INVITES).map { row ->
            ClanInviteSnapshot(row.id, row.clanId, row.clanName, row.inviterCharacterId, row.inviterDisplayName, row.expiresAtMillis)
        }
        val verdictRows = repository.pendingApplicationVerdicts(payload.characterId).take(ClanLimits.MAX_PENDING_VERDICTS)
        val verdicts = verdictRows.map { row ->
            ClanApplicationVerdict(row.id, row.clanId, row.clanName, row.status == 1, row.reviewedAtMillis)
        }
        val frame =
            ClanPackets.encodeSyncOk(
                ClanCharacterSync(
                    snapshot = store.clanForCharacter(payload.characterId)?.toSnapshot(),
                    applicationDailyCount = repository.applicationCountSince(payload.characterId, today),
                    creationDailyCount = repository.creationParticipationCountSince(payload.characterId, today),
                    pendingInvites = invites,
                    applicationVerdicts = verdicts,
                    guestSnapshot = guestSnapshotFor(payload.characterId),
                ),
            )
        return Reply.Ok(frame)
    }

    fun handleMemberStats(worldId: Int, payload: ClanMemberStatsPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_CLAN_MEMBER_STATS) {
            if (payload.characterId <= 0 || payload.combatLevel !in 0..127 || payload.skillTotal !in 0..4095) {
                failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            }
            val currentWorld = presence.worldFor(payload.characterId)
            if (currentWorld != null && currentWorld != worldId) {
                failNow(WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
            }
            val clanId = repository.updateMemberStats(payload.characterId, payload.combatLevel, payload.skillTotal) ?: return@mutation 0L
            refreshAndPush(clanId)
            clanId
        }

    fun handleGuestSelection(worldId: Int, payload: ClanGuestSelectionPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_CLAN_GUEST_SELECTION) {
            requireAuthorized(worldId, payload.characterId)
            when (payload.action) {
                ClanGuestSelectionAction.SELECT -> {
                    val state = store.get(payload.clanId) ?: refreshClan(payload.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
                    if (state.members.any { it.characterId == payload.characterId }) {
                        failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
                    }
                    val isJmod = adminAuthorizer.isJagexModerator(payload.characterId)
                    val character = repository.findCharacter(payload.characterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
                    // A clan that blocks guests can still be loaded as a guest clan; only its chat is refused.
                    if (!isJmod && isBanned(state, character.characterId, character.displayName)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
                    if (!isJmod && GuestClanKickRestrictions.isBlocked(state.clan.clanId, character.characterId)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
                    if (!repository.setGuestClanSelection(payload.characterId, state.clan.clanId)) failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
                    state.clan.clanId
                }
                ClanGuestSelectionAction.CLEAR -> {
                    repository.clearGuestClanSelection(payload.characterId)
                    0L
                }
                else -> failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            }
        }

    fun handleGuestKick(worldId: Int, payload: ClanGuestKickPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_CLAN_GUEST_KICK) {
            requireAuthorized(worldId, payload.characterId)
            val state = store.get(payload.clanId) ?: refreshClan(payload.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            val isJmod = adminAuthorizer.isJagexModerator(payload.characterId)
            if (!isJmod) {
                val actor = member(state, payload.characterId)
                if (payload.characterId != state.clan.ownerCharacterId && actor.rank < state.clan.kickRank) {
                    failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
                }
            }
            val target = repository.findCharacterByDisplayName(payload.targetName) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
            if (state.members.any { it.characterId == target.characterId }) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
            if (adminAuthorizer.isJagexModerator(target.characterId)) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
            // A guest kick must target a currently registered live guest of THIS clan.
            val targetWorld = presence.worldFor(target.characterId)
                ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
            val guestChannel = LiveChatPresenceIndex.ChannelKey(LiveChatPresenceIndex.Kind.GUEST_CLAN, state.clan.clanId)
            if (!liveChatPresence.isMember(guestChannel, target.characterId, targetWorld)) {
                failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
            }
            // Invalidate the durable selection first; ClanHandler will then reconcile
            // the live roster from Central, including the departing target's world.
            if (!repository.clearGuestClanSelection(target.characterId, state.clan.clanId)) {
                failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
            }
            GuestClanKickRestrictions.recordKick(state.clan.clanId, target.characterId)
            state.clan.clanId
        }

    fun handleApplicationVerdictAck(worldId: Int, payload: ClanApplicationVerdictAckPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_VERDICT_ACK) {
            requireAuthorized(worldId, payload.characterId)
            if (payload.applicationIds.isEmpty() || payload.applicationIds.size > ClanLimits.MAX_PENDING_VERDICTS) {
                failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            }
            repository.acknowledgeApplicationVerdicts(payload.characterId, payload.applicationIds)
            0L
        }

    fun handleBoardSync(worldId: Int, payload: ClanCharacterPayload): Reply {
        if (!authorized(worldId, payload.characterId)) return fail(WorldOpcodes.OP_WORLD_CLAN_BOARD_SYNC, WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
        val entries = store.all().asSequence().filter { it.clan.boardListed }.map { state ->
            ClanBoardEntrySnapshot(
                clanId = state.clan.clanId,
                name = state.clan.name,
                ownerDisplayName = state.clan.ownerDisplayName,
                memberCount = state.members.size,
                creationTimeMinutes = state.clan.creationTimeMinutes,
                interestsMask = state.clan.interestsMask,
                recruitRequirements = state.recruitRequirementsSnapshot(),
                homeWorld = state.clan.homeWorld,
                acceptingApplications = state.clan.acceptingApplications,
            )
        }.take(ClanLimits.MAX_BOARD_RESULTS).toList()
        return Reply.Ok(ClanPackets.encodeBoardOk(ClanBoardSnapshot(entries)))
    }

    fun handleDetail(worldId: Int, payload: ClanDetailPayload): Reply {
        if (!authorized(worldId, payload.characterId)) return fail(WorldOpcodes.OP_WORLD_CLAN_DETAIL, WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
        val state = store.get(payload.clanId)
        val isMember = state?.members?.any { it.characterId == payload.characterId } == true
        val isJmod = state != null && adminAuthorizer.isJagexModerator(payload.characterId)
        val snapshot =
            when {
                state == null -> null
                isMember || isJmod -> state.toSnapshot()
                state.clan.boardListed -> state.toPublicSnapshot(payload.characterId)
                else -> null
            }
        return Reply.Ok(ClanPackets.encodeDetailOk(snapshot))
    }

    /**
     * Executes an existing clan mutation against a foreign clan after Central has
     * independently verified the actor's Jagex admin rights. Ownership lifecycle
     * operations are deliberately not admitted here.
     */
    fun handleAdminMutation(worldId: Int, payload: ClanAdminMutationPayload): Reply {
        if (!authorized(worldId, payload.characterId)) {
            return fail(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION, WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
        }
        if (!adminAuthorizer.isJagexModerator(payload.characterId)) {
            return fail(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION, WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        }
        if (store.get(payload.clanId) == null && refreshClan(payload.clanId) == null) {
            return fail(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION, WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        }

        return try {
            readInboundFrame(payload.mutationFrame).use { inner ->
                if (inner.opcode !in ADMIN_MUTATION_OPCODES) {
                    return fail(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION, WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
                }
                withAdminContext(payload.characterId, payload.clanId) {
                    when (inner.opcode) {
                        WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER -> handleAddMember(worldId, ClanPackets.decodeMemberAction(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER -> handleRemoveMember(worldId, ClanPackets.decodeMemberAction(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_RANK -> handleSetRank(worldId, ClanPackets.decodeMemberRank(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_MUTED -> handleSetMuted(worldId, ClanPackets.decodeMemberMuted(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_BAN_ADD -> handleBanAdd(worldId, ClanPackets.decodeBanAdd(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE -> handleBanRemove(worldId, ClanPackets.decodeBanRemove(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_PROFILE -> handleProfile(worldId, ClanPackets.decodeProfile(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_MOTIF -> handleMotif(worldId, ClanPackets.decodeMotif(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS -> handleRecruitRequirements(worldId, ClanPackets.decodeRecruitRequirements(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_HALL_SETTINGS -> handleHallSettings(worldId, ClanPackets.decodeHallSettings(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_SETTING -> handleSetting(worldId, ClanPackets.decodeSetting(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_RANK_TITLES -> handleRankTitles(worldId, ClanPackets.decodeRankTitles(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_SET_BOARD_STATE -> handleBoardState(worldId, ClanPackets.decodeBoardState(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW -> handleApplicationReview(worldId, ClanPackets.decodeApplicationReview(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_EVENT_UPSERT -> handleEventUpsert(worldId, ClanPackets.decodeEventUpsert(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_EVENT_DELETE -> handleEventDelete(worldId, ClanPackets.decodeEventDelete(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_RENAME -> handleRename(worldId, ClanPackets.decodeRename(inner).requireActor(payload.characterId))
                        WorldOpcodes.OP_WORLD_CLAN_INVITE_CREATE -> handleInviteCreate(worldId, ClanPackets.decodeInviteCreate(inner).requireActor(payload.characterId))
                        else -> fail(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION, WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
                    }
                }
            }
        } catch (_: IllegalArgumentException) {
            fail(WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION, WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        }
    }

    fun handleCreate(worldId: Int, payload: ClanCreatePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_CREATE) {
        requireAuthorized(worldId, payload.characterId)
        if (store.clanForCharacter(payload.characterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
        val name = cleanClanName(payload.name) ?: failNow(WorldOpcodes.CLAN_FAIL_NAME_INVALID)
        if (store.byName(name) != null || repository.findClanIdByName(name) != null) failNow(WorldOpcodes.CLAN_FAIL_NAME_TAKEN)

        val founders = linkedSetOf(payload.characterId).apply { addAll(payload.foundingCharacterIds) }
        if (founders.size !in 1..ClanRules.MAX_FOUNDERS) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        for (characterId in founders) {
            if (!presence.isOnline(characterId, worldId)) failNow(WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
            if (repository.findCharacter(characterId) == null) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
            if (store.clanForCharacter(characterId) != null || repository.clanIdForCharacter(characterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
            // Participation history is recorded but not enforced yet; clan creation still allows fewer founders for testing.
        }

        val clanId = repository.createClan(
            name = name,
            ownerCharacterId = payload.characterId,
            foundingCharacterIds = founders.toList(),
            creationTimeMinutes = (System.currentTimeMillis() / 60_000L).toInt(),
            homeWorld = worldId,
            defaultRankTitles = ClanRules.DEFAULT_RANK_TITLES,
            administrativePermissionIds = ClanRules.ADMIN_PERMISSION_IDS.toList(),
            broadcastPermissionIds = ClanRules.BROADCAST_RANK_IDS.toList(),
            ownerRank = ClanRules.OWNER,
            nobodyRank = ClanRules.NOBODY,
            currentRuneDay = currentRuneDay(),
        )
        val state = refreshClan(clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
        pushSnapshot(state)
        clanId
    }

    fun handleAddMember(worldId: Int, payload: ClanMemberActionPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.RECRUITMENT)
        if (state.members.size >= ClanRules.MAX_MEMBERS) failNow(WorldOpcodes.CLAN_FAIL_CLAN_FULL)
        if (!presence.isOnline(payload.targetCharacterId, worldId)) failNow(WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
        if (store.clanForCharacter(payload.targetCharacterId) != null || repository.clanIdForCharacter(payload.targetCharacterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
        val target = repository.findCharacter(payload.targetCharacterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        if (isBanned(state, target.characterId, target.displayName)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
        if (!repository.addMember(state.clan.clanId, target.characterId, ClanRules.MEMBER, currentRuneDay(), ClanRules.MAX_MEMBERS)) {
            failNow(WorldOpcodes.CLAN_FAIL_CLAN_FULL)
        }
        repository.clearGuestClanSelection(target.characterId, state.clan.clanId)
        val updated = refreshClan(state.clan.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
        pushSnapshot(updated)
        updated.clan.clanId
    }

    fun handleLeave(worldId: Int, payload: ClanCharacterPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_LEAVE) {
        val state = actorClan(worldId, payload.characterId)
        if (state.clan.ownerCharacterId == payload.characterId) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        if (!repository.removeMember(state.clan.clanId, payload.characterId)) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        store.remove(state.clan.clanId)
        val updated = refreshClan(state.clan.clanId)
        pushMembershipCleared(payload.characterId, state.clan.clanId, ClanMembershipClearReason.LEFT)
        updated?.let(::pushSnapshot)
        state.clan.clanId
    }

    fun handleRemoveMember(worldId: Int, payload: ClanMemberActionPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.EXPEL_MEMBERS)
        val actor = member(state, payload.characterId)
        val target = member(state, payload.targetCharacterId)
        if (target.characterId == state.clan.ownerCharacterId || target.characterId == payload.characterId) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        if (payload.characterId != state.clan.ownerCharacterId && target.rank >= actor.rank) failNow(WorldOpcodes.CLAN_FAIL_HIERARCHY)
        if (!repository.removeMember(state.clan.clanId, target.characterId)) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        val updated = refreshClan(state.clan.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
        pushMembershipCleared(target.characterId, state.clan.clanId, ClanMembershipClearReason.EXPELLED)
        pushSnapshot(updated)
        state.clan.clanId
    }

    fun handleSetRank(worldId: Int, payload: ClanMemberRankPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_RANK) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.ASSIGN_MEMBER_RANKS)
        if (payload.rank !in ClanRules.ASSIGNABLE_RANKS) failNow(WorldOpcodes.CLAN_FAIL_BAD_RANK)
        val actor = member(state, payload.characterId)
        val target = member(state, payload.targetCharacterId)
        if (target.characterId == state.clan.ownerCharacterId || target.characterId == payload.characterId) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        if (payload.characterId != state.clan.ownerCharacterId && (target.rank >= actor.rank || payload.rank >= actor.rank)) failNow(WorldOpcodes.CLAN_FAIL_HIERARCHY)
        if (!repository.updateMemberRank(state.clan.clanId, target.characterId, payload.rank)) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleSetMuted(worldId: Int, payload: ClanMemberMutedPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_MUTED) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.BAN_FROM_CHAT)
        val actor = member(state, payload.characterId)
        val target = member(state, payload.targetCharacterId)
        if (target.characterId == state.clan.ownerCharacterId || target.characterId == payload.characterId) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        if (payload.characterId != state.clan.ownerCharacterId && target.rank >= actor.rank) failNow(WorldOpcodes.CLAN_FAIL_HIERARCHY)
        if (!repository.updateMemberMuted(state.clan.clanId, target.characterId, payload.muted)) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleBanAdd(worldId: Int, payload: ClanBanAddPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_BAN_ADD) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.BAN_FROM_CHAT)
        if (state.bans.size >= ClanRules.MAX_BANS) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        val target = payload.targetCharacterId.takeIf { it > 0 }?.let(repository::findCharacter)
        val name = target?.displayName?.takeIf(String::isNotBlank) ?: payload.targetDisplayName.trim()
        if (name.isBlank()) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        if (target != null && state.members.any { it.characterId == target.characterId }) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        if (isBanned(state, target?.characterId, name)) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        if (repository.addBan(state.clan.clanId, target?.characterId, name, payload.characterId, ClanRules.MAX_BANS) == null) {
            failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        }
        val selectedTarget = target ?: repository.findCharacterByDisplayName(name)
        if (selectedTarget != null && !adminAuthorizer.isJagexModerator(selectedTarget.characterId)) {
            repository.clearGuestClanSelection(selectedTarget.characterId, state.clan.clanId)
        }
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleBanRemove(worldId: Int, payload: ClanBanRemovePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.BAN_FROM_CHAT)
        if (!repository.removeBan(state.clan.clanId, payload.banId)) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleProfile(worldId: Int, payload: ClanProfilePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_PROFILE) {
        val state = actorClan(worldId, payload.characterId)
        when (payload.field) {
            ClanProfileField.HOME_WORLD -> {
                requirePermission(state, payload.characterId, ClanRules.EDIT_PUBLIC_PROFILE)
                if (payload.value !in 1..999) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
                if (!repository.updateProfileInt(state.clan.clanId, ClanIntField.HOME_WORLD, payload.value)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            }
            ClanProfileField.TALK_RANK -> {
                requireAnyPermission(state, payload.characterId, ClanRules.EDIT_PUBLIC_PROFILE, ClanRules.PERMISSION_ACCESS)
                if (payload.value !in ClanRules.GUEST..ClanRules.OWNER || payload.value == ClanRules.NOBODY) failNow(WorldOpcodes.CLAN_FAIL_BAD_RANK)
                if (!repository.updateProfileInt(state.clan.clanId, ClanIntField.TALK_RANK, payload.value)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            }
            ClanProfileField.KICK_RANK -> {
                requirePermission(state, payload.characterId, ClanRules.PERMISSION_ACCESS)
                if (payload.value !in ClanRules.MEMBER..ClanRules.OWNER) failNow(WorldOpcodes.CLAN_FAIL_BAD_RANK)
                if (!repository.updateProfileInt(state.clan.clanId, ClanIntField.KICK_RANK, payload.value)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            }
            ClanProfileField.ALLOW_UNAFFINED -> {
                requireAnyPermission(state, payload.characterId, ClanRules.EDIT_PUBLIC_PROFILE, ClanRules.PERMISSION_ACCESS)
                val allow = payload.value != 0
                if (!repository.updateProfileBoolean(state.clan.clanId, ClanBooleanField.ALLOW_UNAFFINED, allow)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
                if (!allow) {
                    val affined = state.members.asSequence().map { it.characterId }.toHashSet()
                    repository.guestCharactersForClan(state.clan.clanId).forEach { guestCharacterId ->
                        if (guestCharacterId !in affined && !adminAuthorizer.isJagexModerator(guestCharacterId)) {
                            repository.clearGuestClanSelection(guestCharacterId, state.clan.clanId)
                        }
                    }
                }
            }
            ClanProfileField.INTERESTS_MASK -> {
                requirePermission(state, payload.characterId, ClanRules.EDIT_PUBLIC_PROFILE)
                // Live clans can list any number of the 16 interests.
                val mask = payload.value and 0xFFFF
                if (!repository.updateProfileInt(state.clan.clanId, ClanIntField.INTERESTS_MASK, mask)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            }
            else -> failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        }
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleMotif(worldId: Int, payload: ClanMotifPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_MOTIF) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.ICONS)
        // Same limits as the clans table. The outfit editor sends icons 1..28; the top icon can
        // also still be the default 126, so colour/bottom-icon-only edits must keep it.
        val topIconValid = payload.topIcon in 0..28 || payload.topIcon == 126
        val bottomIconValid = payload.bottomIcon in 0..28
        if (!topIconValid || !bottomIconValid || payload.colour !in 0..8) {
            failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        }
        if (!repository.updateMotif(state.clan.clanId, payload.topIcon, payload.bottomIcon, payload.colour)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleRecruitRequirements(worldId: Int, payload: ClanRecruitRequirementsPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS) {
            val state = actorClan(worldId, payload.characterId)
            requirePermission(state, payload.characterId, ClanRules.RECRUITMENT)
            if (payload.minimumCombatLevel !in 0..127 || payload.minimumSkillTotal !in 0..4095 ||
                payload.minimumQuestPoints !in 0..2047 || payload.ironStatus !in 0..3
            ) {
                failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            }
            val updated =
                repository.updateRecruitRequirements(
                    state.clan.clanId,
                    payload.minimumCombatLevel,
                    payload.minimumSkillTotal,
                    payload.minimumQuestPoints,
                    payload.ironStatus,
                )
            if (!updated) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            refreshAndPush(state.clan.clanId)
            state.clan.clanId
        }

    fun handleHallSettings(worldId: Int, payload: ClanHallSettingsPayload): Reply =
        mutation(WorldOpcodes.OP_WORLD_CLAN_SET_HALL_SETTINGS) {
            val state = actorClan(worldId, payload.characterId)
            requirePermission(state, payload.characterId, ClanRules.EDIT_CLAN_HALL)
            if (payload.playerCount !in 0..2000) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            if (!repository.updateHallSettings(state.clan.clanId, payload.homeWorldOnly, payload.locked, payload.playerCount)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
            refreshAndPush(state.clan.clanId)
            state.clan.clanId
        }

    fun handleSetting(worldId: Int, payload: ClanSettingPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_SETTING) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.PERMISSION_ACCESS)
        when (payload.namespace) {
            ClanSettingNamespace.PERMISSION -> {
                if (payload.value !in ClanRules.GUEST..ClanRules.NOBODY || payload.value == ClanRules.JAGEX_MODERATOR) {
                    failNow(WorldOpcodes.CLAN_FAIL_BAD_RANK)
                }
                repository.setPermission(state.clan.clanId, payload.settingId, payload.value)
            }
            ClanSettingNamespace.BROADCAST -> repository.setBroadcastSetting(state.clan.clanId, payload.settingId, payload.value)
            ClanSettingNamespace.GENERIC -> repository.setSetting(state.clan.clanId, payload.settingId, payload.value)
            else -> failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        }
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleRankTitles(worldId: Int, payload: ClanRankTitlesPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_RANK_TITLES) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.EDIT_RANK_TITLES)
        if (payload.values.keys.any { it !in ClanRules.EDITABLE_TITLE_RANKS } || payload.values.values.any { it !in 1..265 }) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        repository.replaceRankTitles(state.clan.clanId, payload.values)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleBoardState(worldId: Int, payload: ClanBoardStatePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_SET_BOARD_STATE) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.RECRUITMENT)
        if (!repository.setBoardState(state.clan.clanId, payload.listed, payload.acceptingApplications)) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleApplicationSubmit(worldId: Int, payload: ClanApplicationSubmitPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_SUBMIT) {
        requireAuthorized(worldId, payload.characterId)
        if (store.clanForCharacter(payload.characterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
        val state = store.get(payload.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        if (!state.clan.acceptingApplications) failNow(WorldOpcodes.CLAN_FAIL_APPLICATIONS_CLOSED)
        val target = repository.findCharacter(payload.characterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        if (isBanned(state, target.characterId, target.displayName)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
        if (state.applications.any { it.applicantCharacterId == payload.characterId }) failNow(WorldOpcodes.CLAN_FAIL_APPLICATION_EXISTS)
        if (state.applications.size >= ClanLimits.MAX_APPLICATIONS) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        if (!meetsRecruitmentRequirements(payload.profile, state)) failNow(WorldOpcodes.CLAN_FAIL_REQUIREMENTS)
        repository.submitApplication(
            clanId = state.clan.clanId,
            clanName = state.clan.name,
            applicantCharacterId = payload.characterId,
            combatLevel = payload.profile.combatLevel,
            skillTotal = payload.profile.skillTotal,
            questPoints = payload.profile.questPoints,
            ironStatus = payload.profile.ironStatus,
            skillLevels = payload.profile.skillLevels.take(ClanLimits.APPLICATION_SKILL_COUNT),
        )
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleApplicationWithdraw(worldId: Int, payload: ClanApplicationWithdrawPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_WITHDRAW) {
        requireAuthorized(worldId, payload.characterId)
        val state = store.get(payload.clanId) ?: refreshClan(payload.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        val application = state.applications.firstOrNull { it.applicantCharacterId == payload.characterId }
            ?: failNow(WorldOpcodes.CLAN_FAIL_APPLICATION_NOT_FOUND)
        if (!repository.withdrawApplication(state.clan.clanId, application.id, payload.characterId)) {
            failNow(WorldOpcodes.CLAN_FAIL_APPLICATION_NOT_FOUND)
        }
        val updated = refreshClan(state.clan.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
        pushSnapshot(updated)
        pushApplicationWithdrawn(updated)
        state.clan.clanId
    }

    fun handleApplicationReview(worldId: Int, payload: ClanApplicationReviewPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.RECRUITMENT)
        val application = state.applications.firstOrNull { it.id == payload.applicationId } ?: failNow(WorldOpcodes.CLAN_FAIL_APPLICATION_NOT_FOUND)
        if (payload.accept) {
            if (state.members.size >= ClanRules.MAX_MEMBERS) failNow(WorldOpcodes.CLAN_FAIL_CLAN_FULL)
            if (store.clanForCharacter(application.applicantCharacterId) != null || repository.clanIdForCharacter(application.applicantCharacterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
            if (isBanned(state, application.applicantCharacterId, application.applicantDisplayName)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
            repository.acceptApplicationAsMember(
                state.clan.clanId,
                application.id,
                payload.characterId,
                ClanRules.MEMBER,
                currentRuneDay(),
                ClanRules.MAX_MEMBERS,
            ) ?: failNow(WorldOpcodes.CLAN_FAIL_APPLICATION_NOT_FOUND)
            repository.clearGuestClanSelection(application.applicantCharacterId, state.clan.clanId)
        } else {
            repository.reviewApplication(state.clan.clanId, application.id, payload.characterId, 2)
                ?: failNow(WorldOpcodes.CLAN_FAIL_APPLICATION_NOT_FOUND)
        }
        val updated = refreshClan(state.clan.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
        pushSnapshot(updated)
        pushApplicationVerdict(
            application.applicantCharacterId,
            ClanApplicationVerdict(
                applicationId = application.id,
                clanId = state.clan.clanId,
                clanName = state.clan.name,
                accepted = payload.accept,
                reviewedAtMillis = System.currentTimeMillis(),
            ),
        )
        state.clan.clanId
    }

    fun handleEventUpsert(worldId: Int, payload: ClanEventUpsertPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_EVENT_UPSERT) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.ORGANISE_EVENTS)
        if (payload.time !in 0..1439 || payload.durationDays !in 0..14 || payload.world !in 0..999 || payload.position < 0) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        val creator = repository.findCharacter(payload.characterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        repository.upsertEvent(
            state.clan.clanId,
            ClanEventRow(payload.eventId, state.clan.clanId, payload.position, payload.date, payload.time, payload.durationDays, payload.world, payload.type, payload.subtype, payload.activity, payload.rankPlusOne, payload.characterId, creator.displayName),
        )
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleEventDelete(worldId: Int, payload: ClanEventDeletePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_EVENT_DELETE) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.ORGANISE_EVENTS)
        if (!repository.deleteEvent(state.clan.clanId, payload.eventId)) failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleRename(worldId: Int, payload: ClanRenamePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_RENAME) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.EDIT_PUBLIC_PROFILE)
        val name = cleanClanName(payload.name) ?: failNow(WorldOpcodes.CLAN_FAIL_NAME_INVALID)
        val existing = store.byName(name)
        if (existing != null && existing.clan.clanId != state.clan.clanId) failNow(WorldOpcodes.CLAN_FAIL_NAME_TAKEN)
        if (!repository.updateClanName(state.clan.clanId, name, (System.currentTimeMillis() / 60_000L).toInt())) failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    fun handleInviteCreate(worldId: Int, payload: ClanInviteCreatePayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_INVITE_CREATE) {
        val state = actorClan(worldId, payload.characterId)
        requirePermission(state, payload.characterId, ClanRules.RECRUITMENT)
        if (store.clanForCharacter(payload.targetCharacterId) != null || repository.clanIdForCharacter(payload.targetCharacterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
        val target = repository.findCharacter(payload.targetCharacterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        if (isBanned(state, target.characterId, target.displayName)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
        val expires = payload.expiresAtMillis.takeIf { it > System.currentTimeMillis() }?.let(Instant::ofEpochMilli)
        val inviteId = repository.createInvite(state.clan.clanId, target.characterId, payload.characterId, expires)
        val inviter = member(state, payload.characterId)
        pushInvite(
            target.characterId,
            ClanInviteSnapshot(
                id = inviteId,
                clanId = state.clan.clanId,
                clanName = state.clan.name,
                inviterCharacterId = payload.characterId,
                inviterDisplayName = inviter.displayName,
                expiresAtMillis = expires?.toEpochMilli() ?: 0L,
            ),
        )
        state.clan.clanId
    }

    fun handleInviteAccept(worldId: Int, payload: ClanInviteAcceptPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_INVITE_ACCEPT) {
        requireAuthorized(worldId, payload.characterId)
        if (store.clanForCharacter(payload.characterId) != null || repository.clanIdForCharacter(payload.characterId) != null) failNow(WorldOpcodes.CLAN_FAIL_ALREADY_IN_CLAN)
        val invite = repository.pendingInvite(payload.inviteId, payload.characterId)
            ?: failNow(WorldOpcodes.CLAN_FAIL_INVITE_NOT_FOUND)
        val invitedClan = store.get(invite.clanId) ?: refreshClan(invite.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        if (invitedClan.members.size >= ClanRules.MAX_MEMBERS) failNow(WorldOpcodes.CLAN_FAIL_CLAN_FULL)
        val invitee = repository.findCharacter(payload.characterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
        if (isBanned(invitedClan, invitee.characterId, invitee.displayName)) failNow(WorldOpcodes.CLAN_FAIL_BANNED)
        val clanId = repository.acceptInviteAsMember(
            payload.inviteId,
            payload.characterId,
            ClanRules.MEMBER,
            currentRuneDay(),
            ClanRules.MAX_MEMBERS,
        ) ?: failNow(WorldOpcodes.CLAN_FAIL_INVITE_NOT_FOUND)
        repository.clearGuestClanSelection(payload.characterId, clanId)
        val state = refreshClan(clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
        pushSnapshot(state)
        clanId
    }

    fun handleAbdication(worldId: Int, payload: ClanAbdicationPayload): Reply = mutation(WorldOpcodes.OP_WORLD_CLAN_ABDICATION) {
        val state = actorClan(worldId, payload.characterId)
        if (state.clan.ownerCharacterId != payload.characterId) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
        when (payload.action) {
            ClanAbdicationAction.BEGIN -> {
                val now = System.currentTimeMillis()
                if (!repository.beginAbdication(state.clan.clanId, payload.characterId, now, now + MANUAL_ABDICATION_DELAY_MILLIS)) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            }
            ClanAbdicationAction.CANCEL -> if (!repository.cancelAbdication(state.clan.clanId)) failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
            else -> failNow(WorldOpcodes.CLAN_FAIL_CONFLICT)
        }
        refreshAndPush(state.clan.clanId)
        state.clan.clanId
    }

    /** Called from account display-name NOTIFY; identity remains character-id based. */
    fun updateDisplayName(characterId: Int, displayName: String) {
        val affectedClanIds = repository.updateCharacterDisplayName(characterId, displayName)
        for (clanId in affectedClanIds) {
            refreshClan(clanId)?.let(::pushSnapshot)
        }
    }

    /** Central ownership timer; safe to run at startup and periodically. */
    @Synchronized
    fun processOwnershipTimers() {
        val now = System.currentTimeMillis()
        for (state in store.all()) {
            val clan = state.clan
            val pendingDueAt = clan.pendingAbdicationDueAt
            if (pendingDueAt != null && pendingDueAt <= now) {
                val successor = abdicationSuccessor(state)
                when {
                    successor != null -> transferOwnership(state, successor.characterId)
                    state.members.none { it.characterId != clan.ownerCharacterId } -> disband(state)
                    else -> {
                        repository.cancelAbdication(clan.clanId)
                        refreshClan(clan.clanId)?.let(::pushSnapshot)
                    }
                }
                continue
            }
            val owner = state.members.firstOrNull { it.characterId == clan.ownerCharacterId } ?: continue
            if (owner.lastSeenRuneDay > 0 && currentRuneDay() - owner.lastSeenRuneDay >= AUTOMATIC_ABDICATION_INACTIVE_DAYS) {
                abdicationSuccessor(state)?.let { transferOwnership(state, it.characterId) }
            }
        }
    }

    private fun transferOwnership(state: ClanStateRow, successorId: Int) {
        if (!repository.transferOwnership(state.clan.clanId, state.clan.ownerCharacterId, successorId, ClanRules.DEPUTY_OWNER, ClanRules.OWNER)) return
        refreshClan(state.clan.clanId)?.let(::pushSnapshot)
    }

    private fun disband(state: ClanStateRow) {
        // Never destroy clan-owned value or an unresolved cross-database transfer. Completed
        // economy history deliberately survives clan deletion; only current value/open operations block.
        // The economy advisory lock is held across both this check and the clan delete, so a transfer
        // cannot become PREPARED in the gap between them.
        val worlds = state.members.mapNotNull { presence.worldFor(it.characterId) }.toSet()
        val memberIds = state.members.map { it.characterId }
        val disbanded =
            economyRepository.disbandIfClear(state.clan.clanId) {
                repository.disbandClan(state.clan.clanId)
            }
        if (!disbanded) {
            repository.cancelAbdication(state.clan.clanId)
            refreshClan(state.clan.clanId)?.let(::pushSnapshot)
            return
        }
        store.remove(state.clan.clanId)
        val frame = ClanPackets.encodeServerDisbanded(state.clan.clanId)
        worlds.forEach { connections.push(it, frame) }
        memberIds.forEach { pushMembershipCleared(it, state.clan.clanId, ClanMembershipClearReason.DISBANDED) }
    }

    private fun abdicationSuccessor(state: ClanStateRow): ClanMemberRow? {
        val today = currentRuneDay()
        return state.members.asSequence()
            .filter { it.characterId != state.clan.ownerCharacterId }
            .filter { it.rank >= ClanRules.ADMINISTRATOR }
            .filter { it.lastSeenRuneDay > 0 && today - it.lastSeenRuneDay < AUTOMATIC_ABDICATION_INACTIVE_DAYS }
            .sortedWith(compareByDescending<ClanMemberRow> { it.rank }.thenBy { it.joinRuneDay }.thenBy { it.characterId })
            .firstOrNull()
    }

    private fun actorClan(worldId: Int, characterId: Int): ClanStateRow {
        requireAuthorized(worldId, characterId)
        val admin = adminContext.get()
        if (admin != null && admin.characterId == characterId) {
            return store.get(admin.clanId) ?: refreshClan(admin.clanId) ?: failNow(WorldOpcodes.CLAN_FAIL_CLAN_NOT_FOUND)
        }
        return store.clanForCharacter(characterId) ?: failNow(WorldOpcodes.CLAN_FAIL_NO_CLAN)
    }

    private fun requireAuthorized(worldId: Int, characterId: Int) {
        if (!authorized(worldId, characterId)) failNow(WorldOpcodes.CLAN_FAIL_NOT_LOGGED_IN)
    }

    private fun authorized(worldId: Int, characterId: Int): Boolean = characterId > 0 && worldId > 0 && presence.isOnline(characterId, worldId)

    private fun member(state: ClanStateRow, characterId: Int): ClanMemberRow {
        state.members.firstOrNull { it.characterId == characterId }?.let { return it }
        val admin = adminContext.get()
        if (admin != null && admin.characterId == characterId && admin.clanId == state.clan.clanId) {
            val character = repository.findCharacter(characterId) ?: failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
            return ClanMemberRow(
                clanId = state.clan.clanId,
                characterId = characterId,
                displayName = character.displayName,
                rank = ClanRules.JAGEX_MODERATOR,
                joinRuneDay = 0,
                muted = false,
                extraInfo = 0,
                lastSeenRuneDay = currentRuneDay(),
                combatLevel = 0,
                skillTotal = 0,
            )
        }
        failNow(WorldOpcodes.CLAN_FAIL_MEMBER_NOT_FOUND)
    }

    private fun requirePermission(state: ClanStateRow, actorCharacterId: Int, settingId: Int) {
        if (!hasPermission(state, actorCharacterId, settingId)) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
    }

    private fun requireAnyPermission(state: ClanStateRow, actorCharacterId: Int, vararg settingIds: Int) {
        if (settingIds.none { hasPermission(state, actorCharacterId, it) }) failNow(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
    }

    private fun hasPermission(state: ClanStateRow, actorCharacterId: Int, settingId: Int): Boolean {
        val admin = adminContext.get()
        if (admin != null && admin.characterId == actorCharacterId && admin.clanId == state.clan.clanId) return true
        if (state.clan.ownerCharacterId == actorCharacterId) return true
        val actorRank = state.members.firstOrNull { it.characterId == actorCharacterId }?.rank ?: ClanRules.GUEST
        val required = state.permissions[settingId] ?: ClanRules.OWNER
        if (required == ClanRules.NOBODY) return false
        if (required == ClanRules.GUEST) return true
        return actorRank >= required
    }

    private inline fun <T> withAdminContext(characterId: Int, clanId: Long, block: () -> T): T {
        val previous = adminContext.get()
        adminContext.set(AdminContext(characterId, clanId))
        return try {
            block()
        } finally {
            if (previous == null) adminContext.remove() else adminContext.set(previous)
        }
    }

    private fun <T> T.requireActor(expected: Int): T {
        val actual = when (this) {
            is ClanMemberActionPayload -> characterId
            is ClanMemberRankPayload -> characterId
            is ClanMemberMutedPayload -> characterId
            is ClanBanAddPayload -> characterId
            is ClanBanRemovePayload -> characterId
            is ClanProfilePayload -> characterId
            is ClanMotifPayload -> characterId
            is ClanRecruitRequirementsPayload -> characterId
            is ClanHallSettingsPayload -> characterId
            is ClanSettingPayload -> characterId
            is ClanRankTitlesPayload -> characterId
            is ClanBoardStatePayload -> characterId
            is ClanApplicationReviewPayload -> characterId
            is ClanEventUpsertPayload -> characterId
            is ClanEventDeletePayload -> characterId
            is ClanRenamePayload -> characterId
            is ClanInviteCreatePayload -> characterId
            else -> -1
        }
        require(actual == expected) { "nested clan admin actor mismatch" }
        return this
    }

    private fun isBanned(state: ClanStateRow, characterId: Int?, displayName: String): Boolean =
        state.bans.any { ban ->
            (characterId != null && characterId > 0 && ban.characterId == characterId) || ban.displayName.equals(displayName, ignoreCase = true)
        }

    private fun guestSnapshotFor(characterId: Int): ClanSnapshot? {
        val clanId = repository.guestClanIdForCharacter(characterId) ?: return null
        val state = store.get(clanId) ?: refreshClan(clanId) ?: run {
            repository.clearGuestClanSelection(characterId)
            return null
        }

        if (adminAuthorizer.isJagexModerator(characterId)) {
            return state.toPublicSnapshot(characterId)
        }

        if (state.members.any { it.characterId == characterId }) {
            repository.clearGuestClanSelection(characterId, clanId)
            return null
        }

        val character = repository.findCharacter(characterId) ?: run {
            repository.clearGuestClanSelection(characterId, clanId)
            return null
        }
        if (!state.clan.allowUnaffined || isBanned(state, character.characterId, character.displayName)) {
            repository.clearGuestClanSelection(characterId, clanId)
            return null
        }
        return state.toPublicSnapshot(characterId)
    }

    private fun ClanStateRow.recruitRequirementsSnapshot(): ClanRecruitRequirementsSnapshot =
        ClanRecruitRequirementsSnapshot(
            minimumCombatLevel = clan.recruitMinCombat,
            minimumSkillTotal = clan.recruitMinSkillTotal,
            minimumQuestPoints = clan.recruitMinQuestPoints,
            ironStatus = clan.recruitIronStatus,
        )

    private fun meetsRecruitmentRequirements(profile: ClanApplicationProfile, state: ClanStateRow): Boolean {
        val req = state.recruitRequirementsSnapshot()
        if (profile.combatLevel < req.minimumCombatLevel) return false
        if (profile.skillTotal < req.minimumSkillTotal) return false
        if (profile.questPoints < req.minimumQuestPoints) return false
        return when (req.ironStatus) {
            0 -> true
            1 -> profile.ironStatus in 1..3
            2 -> profile.ironStatus == 2
            3 -> profile.ironStatus == 3
            else -> false
        }
    }

    private fun refreshClan(clanId: Long): ClanStateRow? {
        val state = repository.loadClan(clanId)
        if (state == null) store.remove(clanId) else store.put(state)
        return state
    }

    private fun refreshAndPush(clanId: Long) {
        refreshClan(clanId)?.let(::pushSnapshot)
    }

    private fun pushSnapshot(state: ClanStateRow) {
        val frame = ClanPackets.encodeServerSnapshot(state.toSnapshot())
        val memberWorlds = state.members.asSequence().mapNotNull { presence.worldFor(it.characterId) }.toSet()
        memberWorlds.forEach { worldId -> connections.push(worldId, frame) }
        // Other worlds get only a clan ID, never another clan's full private snapshot.
        val invalidation = outboundFrame(WorldOpcodes.OP_SERVER_CLAN_INVALIDATED) {
            writeLong(state.clan.clanId)
        }
        connections.registeredWorldIds().asSequence().filter { it !in memberWorlds }.forEach { worldId ->
            connections.push(worldId, invalidation)
        }
    }

    private fun pushInvite(characterId: Int, invite: ClanInviteSnapshot) {
        val worldId = presence.worldFor(characterId) ?: return
        connections.push(worldId, ClanPackets.encodeInvitePush(ClanInvitePush(characterId, invite)))
    }

    private fun pushApplicationVerdict(characterId: Int, verdict: ClanApplicationVerdict) {
        val worldId = presence.worldFor(characterId) ?: return
        // A successful socket write is not proof that the game thread applied the verdict.
        // Keep it pending until the world acknowledges delivery after game-thread application.
        connections.push(worldId, ClanPackets.encodeApplicationResultPush(ClanApplicationResultPush(characterId, verdict)))
    }

    private fun pushApplicationWithdrawn(state: ClanStateRow) {
        val frame = ClanPackets.encodeServerApplicationWithdrawn(state.clan.clanId)
        state.members.asSequence().mapNotNull { presence.worldFor(it.characterId) }.toSet()
            .forEach { worldId -> connections.push(worldId, frame) }
    }

    private fun pushMembershipCleared(characterId: Int, clanId: Long, reason: Int) {
        val worldId = presence.worldFor(characterId) ?: return
        connections.push(worldId, ClanPackets.encodeMembershipCleared(ClanMembershipClearedPush(characterId, clanId, reason)))
    }

    private fun ClanStateRow.toSnapshot(): ClanSnapshot = ClanSnapshot(
        clanId = clan.clanId,
        name = clan.name,
        ownerCharacterId = clan.ownerCharacterId,
        ownerDisplayName = clan.ownerDisplayName,
        creationTimeMinutes = clan.creationTimeMinutes,
        renamedTimeMinutes = clan.renamedTimeMinutes,
        allowUnaffined = clan.allowUnaffined,
        talkRank = clan.talkRank,
        kickRank = clan.kickRank,
        homeWorld = clan.homeWorld,
        interestsMask = clan.interestsMask,
        motif = ClanMotifSnapshot(clan.motifTopIcon, clan.motifBottomIcon, clan.motifColour),
        recruitRequirements = recruitRequirementsSnapshot(),
        hallSettings = ClanHallSettingsSnapshot(clan.hallHomeWorldOnly, clan.hallLocked, clan.hallPlayerCount),
        boardListed = clan.boardListed,
        acceptingApplications = clan.acceptingApplications,
        pendingAbdicationRequesterCharacterId = clan.pendingAbdicationRequesterCharacterId ?: 0,
        pendingAbdicationRequestedAt = clan.pendingAbdicationRequestedAt ?: 0L,
        pendingAbdicationDueAt = clan.pendingAbdicationDueAt ?: 0L,
        members =
            members.map { member ->
                ClanMemberSnapshot(
                    characterId = member.characterId,
                    displayName = member.displayName,
                    rank = member.rank,
                    joinRuneDay = member.joinRuneDay,
                    muted = member.muted,
                    extraInfo = member.extraInfo,
                    lastSeenRuneDay = member.lastSeenRuneDay,
                    combatLevel = member.combatLevel,
                    skillTotal = member.skillTotal,
                    worldId = presence.worldFor(member.characterId) ?: 0,
                )
            },
        bans = bans.map { ClanBanSnapshot(it.id, it.characterId ?: 0, it.displayName, it.createdAtMillis) },
        rankTitles = rankTitles,
        permissions = permissions,
        broadcastSettings = broadcastSettings,
        settings = settings,
        applications = applications.map {
            ClanApplicationSnapshot(
                id = it.id, characterId = it.applicantCharacterId, displayName = it.applicantDisplayName, appliedAtMillis = it.appliedAtMillis,
                profile = ClanApplicationProfile(it.combatLevel, it.skillTotal, it.questPoints, it.ironStatus, it.skillLevels),
            )
        },
        events = events.map { ClanEventSnapshot(it.id, it.position, it.date, it.time, it.durationDays, it.world, it.type, it.subtype, it.activity, it.rankPlusOne, it.creatorCharacterId ?: 0, it.creatorDisplayName) },
    )

    /**
     * Public board/guest inspection deliberately omits moderation and application state, except
     * the viewer's own pending application so they can see and withdraw it.
     */
    private fun ClanStateRow.toPublicSnapshot(viewerCharacterId: Int): ClanSnapshot {
        val full = toSnapshot()
        return full.copy(
            bans = emptyList(),
            applications = full.applications.filter { it.characterId == viewerCharacterId },
            pendingAbdicationRequesterCharacterId = 0,
            pendingAbdicationRequestedAt = 0L,
            pendingAbdicationDueAt = 0L,
        )
    }

    private inline fun mutation(action: Int, block: () -> Long): Reply = try {
        Reply.Ok(ClanPackets.encodeOk(ClanMutationReply(action, block())))
    } catch (failure: ClanFailure) {
        fail(action, failure.reason)
    } catch (sql: SQLException) {
        log.warn("Clan mutation {} failed in PostgreSQL", action, sql)
        val reason = if (sql.sqlState == "23505") WorldOpcodes.CLAN_FAIL_CONFLICT else WorldOpcodes.CLAN_FAIL_UNAVAILABLE
        fail(action, reason)
    } catch (t: Throwable) {
        log.warn("Clan mutation {} failed", action, t)
        fail(action, WorldOpcodes.CLAN_FAIL_UNAVAILABLE)
    }

    private fun fail(action: Int, reason: Int): Reply.Fail = Reply.Fail(ClanPackets.encodeFail(ClanMutationFail(action, reason)))
    private fun syncFail(reason: Int): Reply.Fail = Reply.Fail(ClanPackets.encodeSyncFail(reason))
    private fun failNow(reason: Int): Nothing = throw ClanFailure(reason)

    private fun cleanClanName(raw: String): String? {
        val filtered = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }.take(12).trim()
        if (filtered.isBlank()) return null
        return filtered.split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::uppercase) }
    }

    private fun currentRuneDay(): Int = (LocalDate.now(ZoneOffset.UTC).toEpochDay() - RUNESCAPE_EPOCH_DAY).toInt()

    private class ClanFailure(val reason: Int) : RuntimeException()

    private companion object {
        val RUNESCAPE_EPOCH_DAY: Long = LocalDate.of(2002, 2, 27).toEpochDay()
        const val MANUAL_ABDICATION_DELAY_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
        const val AUTOMATIC_ABDICATION_INACTIVE_DAYS: Int = 90

        val ADMIN_MUTATION_OPCODES: Set<Int> = setOf(
            WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER,
            WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER,
            WorldOpcodes.OP_WORLD_CLAN_SET_RANK,
            WorldOpcodes.OP_WORLD_CLAN_SET_MUTED,
            WorldOpcodes.OP_WORLD_CLAN_BAN_ADD,
            WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE,
            WorldOpcodes.OP_WORLD_CLAN_SET_PROFILE,
            WorldOpcodes.OP_WORLD_CLAN_SET_SETTING,
            WorldOpcodes.OP_WORLD_CLAN_SET_RANK_TITLES,
            WorldOpcodes.OP_WORLD_CLAN_SET_BOARD_STATE,
            WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW,
            WorldOpcodes.OP_WORLD_CLAN_EVENT_UPSERT,
            WorldOpcodes.OP_WORLD_CLAN_EVENT_DELETE,
            WorldOpcodes.OP_WORLD_CLAN_RENAME,
            WorldOpcodes.OP_WORLD_CLAN_INVITE_CREATE,
            WorldOpcodes.OP_WORLD_CLAN_SET_MOTIF,
            WorldOpcodes.OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS,
            WorldOpcodes.OP_WORLD_CLAN_SET_HALL_SETTINGS,
        )
    }
}
