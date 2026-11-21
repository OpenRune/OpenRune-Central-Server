package dev.or2.central.worldlink.handlers

import dev.or2.central.clan.ClanService
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.readInboundFrame
import dev.or2.central.worldlink.protocol.livechat.LiveChatKind
import dev.or2.central.worldlink.protocol.clan.ClanMutationFail
import dev.or2.central.worldlink.protocol.clan.ClanPackets
import dev.or2.central.worldlink.protocol.packets.incoming.impl.*

class ClanHandler(
    private val clanService: ClanService,
    private val liveChat: LiveChatHandler,
) {
    fun handle(
        connection: WorldConnection,
        opcode: Int,
        input: FrameReader,
    ): HandlerResult {
        if (!connection.handshakeDone) return notAllowed(opcode)
        var guestSelectionCharacterId: Int? = null
        var adminMutationClanId: Long? = null
        val reply =
            try {
                when (opcode) {
                    WorldOpcodes.OP_WORLD_CLAN_SYNC -> clanService.handleSync(connection.worldId, WorldClanSyncPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_CREATE -> clanService.handleCreate(connection.worldId, WorldClanCreatePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER -> clanService.handleAddMember(connection.worldId, WorldClanAddMemberPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_LEAVE -> clanService.handleLeave(connection.worldId, WorldClanLeavePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER -> clanService.handleRemoveMember(connection.worldId, WorldClanRemoveMemberPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_RANK -> clanService.handleSetRank(connection.worldId, WorldClanSetRankPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_MUTED -> clanService.handleSetMuted(connection.worldId, WorldClanSetMutedPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_BAN_ADD -> clanService.handleBanAdd(connection.worldId, WorldClanBanAddPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE -> clanService.handleBanRemove(connection.worldId, WorldClanBanRemovePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_PROFILE -> clanService.handleProfile(connection.worldId, WorldClanSetProfilePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_MOTIF -> clanService.handleMotif(connection.worldId, WorldClanSetMotifPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS -> clanService.handleRecruitRequirements(connection.worldId, WorldClanSetRecruitRequirementsPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_HALL_SETTINGS -> clanService.handleHallSettings(connection.worldId, WorldClanSetHallSettingsPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_SETTING -> clanService.handleSetting(connection.worldId, WorldClanSetSettingPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_RANK_TITLES -> clanService.handleRankTitles(connection.worldId, WorldClanSetRankTitlesPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_SET_BOARD_STATE -> clanService.handleBoardState(connection.worldId, WorldClanSetBoardStatePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_APPLICATION_SUBMIT -> clanService.handleApplicationSubmit(connection.worldId, WorldClanApplicationSubmitPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW -> clanService.handleApplicationReview(connection.worldId, WorldClanApplicationReviewPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_EVENT_UPSERT -> clanService.handleEventUpsert(connection.worldId, WorldClanEventUpsertPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_EVENT_DELETE -> clanService.handleEventDelete(connection.worldId, WorldClanEventDeletePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_ABDICATION -> clanService.handleAbdication(connection.worldId, WorldClanAbdicationPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_BOARD_SYNC -> clanService.handleBoardSync(connection.worldId, WorldClanBoardSyncPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_DETAIL -> clanService.handleDetail(connection.worldId, WorldClanDetailPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_RENAME -> clanService.handleRename(connection.worldId, WorldClanRenamePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_INVITE_CREATE -> clanService.handleInviteCreate(connection.worldId, WorldClanInviteCreatePacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_INVITE_ACCEPT -> clanService.handleInviteAccept(connection.worldId, WorldClanInviteAcceptPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_APPLICATION_VERDICT_ACK -> clanService.handleApplicationVerdictAck(connection.worldId, WorldClanApplicationVerdictAckPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_ADMIN_MUTATION -> {
                        val payload = WorldClanAdminMutationPacket.decode(input)
                        adminMutationClanId = payload.clanId
                        clanService.handleAdminMutation(connection.worldId, payload)
                    }
                    WorldOpcodes.OP_WORLD_CLAN_MEMBER_STATS -> clanService.handleMemberStats(connection.worldId, WorldClanMemberStatsPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_GUEST_SELECTION -> {
                        val payload = WorldClanGuestSelectionPacket.decode(input)
                        guestSelectionCharacterId = payload.characterId
                        clanService.handleGuestSelection(connection.worldId, payload)
                    }
                    WorldOpcodes.OP_WORLD_CLAN_GUEST_KICK -> clanService.handleGuestKick(connection.worldId, WorldClanGuestKickPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CLAN_APPLICATION_WITHDRAW -> clanService.handleApplicationWithdraw(connection.worldId, WorldClanApplicationWithdrawPacket.decode(input))
                    else -> return notAllowed(opcode)
                }
            } catch (_: PacketDecodeException) {
                return notAllowed(opcode)
            } catch (_: IllegalArgumentException) {
                return notAllowed(opcode)
            }

        return when (reply) {
            is ClanService.Reply.Ok -> {
                // Persisted mutations succeeded; rebuild only the affected live projection.
                if (opcode in LIVE_CLAN_RECONCILE_ACTIONS) {
                    runCatching {
                        val changedId = readInboundFrame(reply.frame).use { ClanPackets.decodeMutationReply(it).clanId }
                        if (changedId > 0L) {
                            liveChat.reconcileChannel(LiveChatKind.MAIN_CLAN, changedId)
                            liveChat.reconcileChannel(LiveChatKind.GUEST_CLAN, changedId)
                        }
                    }
                }
                // CLEAR replies with clanId=0. Revalidate the actor's former live guest
                // channel rather than treating 0 as a channel id and leaving a ghost.
                guestSelectionCharacterId?.let { actorId ->
                    runCatching { liveChat.reconcileGuestSelection(actorId) }
                }
                // The admin envelope can perform a nested persistent mutation against
                // a foreign clan. Reconcile the envelope's authoritative clan id too.
                adminMutationClanId?.let { affectedClanId ->
                    if (affectedClanId > 0L) {
                        runCatching {
                            liveChat.reconcileChannel(LiveChatKind.MAIN_CLAN, affectedClanId)
                            liveChat.reconcileChannel(LiveChatKind.GUEST_CLAN, affectedClanId)
                        }
                    }
                }
                HandlerResult.Reply(reply.frame)
            }
            is ClanService.Reply.Fail -> HandlerResult.Reply(reply.frame)
        }
    }

    private companion object {
        val LIVE_CLAN_RECONCILE_ACTIONS = setOf(
            WorldOpcodes.OP_WORLD_CLAN_ADD_MEMBER,
            WorldOpcodes.OP_WORLD_CLAN_INVITE_ACCEPT,
            WorldOpcodes.OP_WORLD_CLAN_APPLICATION_REVIEW,
            WorldOpcodes.OP_WORLD_CLAN_LEAVE,
            WorldOpcodes.OP_WORLD_CLAN_REMOVE_MEMBER,
            WorldOpcodes.OP_WORLD_CLAN_SET_RANK,
            WorldOpcodes.OP_WORLD_CLAN_SET_MUTED,
            WorldOpcodes.OP_WORLD_CLAN_BAN_ADD,
            WorldOpcodes.OP_WORLD_CLAN_BAN_REMOVE,
            WorldOpcodes.OP_WORLD_CLAN_SET_SETTING,
            WorldOpcodes.OP_WORLD_CLAN_GUEST_SELECTION,
            WorldOpcodes.OP_WORLD_CLAN_GUEST_KICK,
        )
    }

    private fun notAllowed(opcode: Int): HandlerResult {
        val frame =
            if (opcode == WorldOpcodes.OP_WORLD_CLAN_SYNC) {
                ClanPackets.encodeSyncFail(WorldOpcodes.CLAN_FAIL_NOT_ALLOWED)
            } else {
                ClanPackets.encodeFail(ClanMutationFail(opcode, WorldOpcodes.CLAN_FAIL_NOT_ALLOWED))
            }
        return HandlerResult.Reply(frame)
    }
}