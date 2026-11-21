package dev.or2.central.worldlink.handlers

import dev.or2.central.friendchat.FriendChatEffectiveSettings
import dev.or2.central.friendchat.FriendChatService
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.readInboundFrame
import dev.or2.central.worldlink.protocol.livechat.LiveChatKind
import dev.or2.central.worldlink.protocol.friendchat.FriendChatMutationFail
import dev.or2.central.worldlink.protocol.friendchat.FriendChatMutationReply
import dev.or2.central.worldlink.protocol.friendchat.FriendChatPackets
import dev.or2.central.worldlink.protocol.packets.incoming.impl.*
import org.slf4j.LoggerFactory

class FriendChatHandler(
    private val service: FriendChatService,
    private val liveChat: LiveChatHandler,
    private val effectiveSettings: FriendChatEffectiveSettings,
) {
    private val log = LoggerFactory.getLogger(FriendChatHandler::class.java)

    init {
        // Setup changes reach live channels when their 60-second batch takes effect.
        effectiveSettings.onDue(::applyDueBatch)
    }

    /**
     * Ends a due settings batch and publishes the settings now in force to every world hosting the
     * channel. Shares this handler's monitor with [handle], so no settings write can land between
     * the batch ending and its publication.
     */
    @Synchronized
    private fun applyDueBatch(batch: FriendChatEffectiveSettings.Batch) {
        if (!effectiveSettings.complete(batch)) return
        runCatching {
            liveChat.reconcileChannel(LiveChatKind.FRIENDS_CHAT, batch.channelId)
            liveChat.publishFriendChatSettings(batch.channelId)
        }.onFailure { log.warn("Publishing Friends Chat settings failed for channel {}", batch.channelId, it) }
    }

    // Keep the successful settings write and its roster publication in one order.
    // No client packets or game-thread state are mutated under this monitor.
    @Synchronized
    fun handle(connection: WorldConnection, opcode: Int, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) return notAllowed(opcode)
        val reply = try {
            when (opcode) {
                WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC -> service.handleSync(connection.worldId, WorldFriendChatSyncPacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_RESOLVE -> service.handleResolve(connection.worldId, WorldFriendChatResolvePacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_SETTINGS -> service.handleSetSettings(connection.worldId, WorldFriendChatSetSettingsPacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_FIELD -> service.handleSetField(connection.worldId, WorldFriendChatSetFieldPacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_RANK -> service.handleSetRank(connection.worldId, WorldFriendChatSetRankPacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_RESTORE -> service.handleRestore(connection.worldId, WorldFriendChatRestorePacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_CLEAR_RESTORE -> service.handleClearRestore(connection.worldId, WorldFriendChatClearRestorePacket.decode(input))
                WorldOpcodes.OP_WORLD_FRIENDCHAT_KICK -> {
                    val payload = WorldFriendChatKickPacket.decode(input)
                    val reason = liveChat.kickFriendChat(connection.worldId, payload)
                    if (reason == null) {
                        FriendChatService.Reply.Ok(
                            FriendChatPackets.encodeOk(
                                FriendChatMutationReply(opcode, payload.channelId)
                            )
                        )
                    } else {
                        FriendChatService.Reply.Fail(
                            FriendChatPackets.encodeFail(FriendChatMutationFail(opcode, reason))
                        )
                    }
                }
                else -> return notAllowed(opcode)
            }
        } catch (_: PacketDecodeException) {
            return notAllowed(opcode)
        } catch (_: IllegalArgumentException) {
            return notAllowed(opcode)
        }
        return when (reply) {
            is FriendChatService.Reply.Ok -> {
                // SET_FIELD / SET_SETTINGS: published when their settings batch takes effect (see init).
                // Rank changes still apply immediately.
                if (opcode == WorldOpcodes.OP_WORLD_FRIENDCHAT_SET_RANK) {
                    runCatching {
                        val id = readInboundFrame(reply.frame).use { FriendChatPackets.decodeMutationReply(it).channelId }
                        liveChat.reconcileChannel(LiveChatKind.FRIENDS_CHAT, id)
                    }
                }
                HandlerResult.Reply(reply.frame)
            }
            is FriendChatService.Reply.Fail -> HandlerResult.Reply(reply.frame)
        }
    }

    private fun notAllowed(opcode: Int): HandlerResult {
        val frame = if (opcode == WorldOpcodes.OP_WORLD_FRIENDCHAT_SYNC) {
            FriendChatPackets.encodeSyncFail(WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED)
        } else {
            FriendChatPackets.encodeFail(FriendChatMutationFail(opcode, WorldOpcodes.FRIEND_CHAT_FAIL_NOT_ALLOWED))
        }
        return HandlerResult.Reply(frame)
    }
}