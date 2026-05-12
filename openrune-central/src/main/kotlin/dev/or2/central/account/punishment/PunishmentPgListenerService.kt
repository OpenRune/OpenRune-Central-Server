package dev.or2.central.account.punishment

import dev.or2.central.worldserver.session.SessionRepository
import dev.or2.central.http.world.WorldListCache
import dev.or2.central.worldserver.net.codec.writeServerBroadcast
import dev.or2.central.worldserver.net.codec.writeServerKick
import dev.or2.central.worldserver.net.codec.writeServerMuteUpdate
import dev.or2.central.worldserver.net.codec.writeServerRebootSchedule
import dev.or2.central.worldserver.net.codec.writeServerRevokeLogin
import dev.or2.central.worldserver.net.push.WorldServerPushChannelRegistry
import io.netty.buffer.Unpooled
import java.sql.Connection
import javax.sql.DataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull as KJsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory

class PunishmentPgListenerService(
    private val dataSource: DataSource,
    private val worldServerPushChannelRegistry: WorldServerPushChannelRegistry,
    private val sessionRepository: SessionRepository,
    private val worldListCache: WorldListCache?,
) {
    private val log = LoggerFactory.getLogger(PunishmentPgListenerService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var stopped: Boolean = true

    @Volatile
    private var listenConnection: Connection? = null

    private var thread: Thread? = null

    fun start() {
        if (!stopped) {
            return
        }
        stopped = false
        val t =
            Thread(
                {
                    try {
                        runListenLoop()
                    } catch (e: Exception) {
                        if (!stopped) {
                            log.error("Punishment LISTEN thread failed", e)
                        }
                    }
                },
                "openrune-punishment-pg-listen",
            ).apply { isDaemon = true }
        thread = t
        t.start()
    }

    fun stop() {
        stopped = true
        thread?.interrupt()
        try {
            listenConnection?.close()
        } catch (_: Exception) {
        }
        listenConnection = null
        thread = null
    }

    private fun runListenLoop() {
        val conn = dataSource.connection
        listenConnection = conn
        conn.autoCommit = true
        val pg =
            try {
                conn.unwrap(PGConnection::class.java)
            } catch (e: Exception) {
                log.warn("PostgreSQL LISTEN disabled (unwrap PGConnection failed): {}", e.message)
                try {
                    conn.close()
                } catch (_: Exception) {
                }
                listenConnection = null
                return
            }
        conn.createStatement().use { st ->
            st.execute("LISTEN punishment_events")
            st.execute("LISTEN punishment_kick_events")
            st.execute("LISTEN character_mute_events")
            st.execute("LISTEN world_reboot_events")
            st.execute("LISTEN world_broadcast_events")
        }
        log.info(
            "Listening for PostgreSQL NOTIFY on punishment_events, punishment_kick_events, " +
                "character_mute_events, world_reboot_events, world_broadcast_events",
        )
        conn.createStatement().use { ping ->
            while (!stopped && !Thread.currentThread().isInterrupted) {
                try {
                    ping.execute("SELECT 1")
                } catch (e: Exception) {
                    if (stopped) {
                        break
                    }
                    log.warn("Punishment LISTEN poll query failed: {}", e.message)
                    Thread.sleep(500)
                    continue
                }
                val batch = pg.notifications
                if (batch != null) {
                    for (n in batch) {
                        when (n.name) {
                            "punishment_events" -> handlePunishmentPayload(n.parameter)
                            "punishment_kick_events" -> handleKickPayload(n.parameter)
                            "character_mute_events" -> handleMutePayload(n.parameter)
                            "world_reboot_events" -> handleWorldRebootPayload(n.parameter)
                            "world_broadcast_events" -> handleWorldBroadcastPayload(n.parameter)
                        }
                    }
                }
                Thread.sleep(200)
            }
        }
    }

    private fun flushFrameToWorlds(
        worldIds: List<Int>,
        frame: ByteArray,
        broadcastAllIfEmpty: Boolean,
    ) {
        val targets =
            when {
                worldIds.isNotEmpty() -> worldIds
                broadcastAllIfEmpty -> worldServerPushChannelRegistry.registeredWorldIds()
                else -> emptyList()
            }
        for (worldId in targets) {
            val ch = worldServerPushChannelRegistry.channel(worldId) ?: continue
            ch.eventLoop().execute {
                if (ch.isActive) {
                    ch.writeAndFlush(Unpooled.wrappedBuffer(frame))
                }
            }
        }
    }

    private fun handlePunishmentPayload(parameter: String?) {
        if (parameter.isNullOrBlank()) {
            return
        }
        val accountId: Long
        val characterId: Int
        try {
            val root = json.parseToJsonElement(parameter).jsonObject
            accountId =
                root["account_id"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return
            val cidEl = root["character_id"]
            characterId =
                when {
                    cidEl == null || cidEl is KJsonNull -> 0
                    else -> cidEl.jsonPrimitive.content.toIntOrNull() ?: 0
                }
        } catch (e: Exception) {
            log.warn("Bad punishment_events payload: {}", parameter, e)
            return
        }
        try {
            val worldIds = sessionRepository.findDistinctWorldIdsByAccount(accountId)
            val removed = sessionRepository.deleteAllForAccount(accountId)
            if (removed > 0) {
                log.info("Revoked {} Central session(s) for account {} after ban notify", removed, accountId)
            }
            worldListCache?.rebuild()
            val frame = writeServerRevokeLogin(accountId, characterId)
            if (worldIds.isEmpty()) {
                log.info(
                    "punishment_events: no Central sessions for account {} — broadcasting revoke to all linked worlds",
                    accountId,
                )
            }
            flushFrameToWorlds(worldIds, frame, broadcastAllIfEmpty = true)
        } catch (e: Exception) {
            log.warn("Failed to apply punishment revocation for account {}", accountId, e)
        }
    }

    private fun handleKickPayload(parameter: String?) {
        if (parameter.isNullOrBlank()) {
            return
        }
        val accountId: Long
        val characterId: Int
        try {
            val root = json.parseToJsonElement(parameter).jsonObject
            accountId =
                root["account_id"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return
            characterId =
                root["character_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            log.warn("Bad punishment_kick_events payload: {}", parameter, e)
            return
        }
        try {
            val worldIds = sessionRepository.findDistinctWorldIdsByAccount(accountId)
            val frame = writeServerKick(accountId, characterId)
            flushFrameToWorlds(worldIds, frame, broadcastAllIfEmpty = true)
        } catch (e: Exception) {
            log.warn("Failed to push kick frame for account {}", accountId, e)
        }
    }

    private fun handleMutePayload(parameter: String?) {
        if (parameter.isNullOrBlank()) {
            return
        }
        val accountId: Long
        val characterId: Int
        val epochMillis: Long
        try {
            val root = json.parseToJsonElement(parameter).jsonObject
            accountId = root["account_id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
            characterId = root["character_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
            epochMillis =
                root["muted_until_epoch_millis"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
        } catch (e: Exception) {
            log.warn("Bad character_mute_events payload: {}", parameter, e)
            return
        }
        try {
            val worldIds = sessionRepository.findDistinctWorldIdsByAccount(accountId)
            val frame = writeServerMuteUpdate(accountId, characterId, epochMillis)
            flushFrameToWorlds(worldIds, frame, broadcastAllIfEmpty = true)
        } catch (e: Exception) {
            log.warn("Failed to push mute update for account {}", accountId, e)
        }
    }

    private fun handleWorldRebootPayload(parameter: String?) {
        if (parameter.isNullOrBlank()) {
            return
        }
        try {
            val root = json.parseToJsonElement(parameter).jsonObject
            val op = root["op"]?.jsonPrimitive?.content ?: return
            val widEl = root["world_id"]
            val worldId: Int? =
                when {
                    widEl == null || widEl is KJsonNull -> null
                    else -> widEl.jsonPrimitive.content.toIntOrNull()
                }
            val worldScope = worldId ?: 0
            if (op == "clear") {
                val frame = writeServerRebootSchedule(clear = true, worldScope = worldScope, rebootAtMs = 0L, message = "")
                if (worldId == null) {
                    flushFrameToWorlds(emptyList(), frame, broadcastAllIfEmpty = true)
                } else {
                    flushFrameToWorlds(listOf(worldId), frame, broadcastAllIfEmpty = false)
                }
                return
            }
            if (op != "set") {
                return
            }
            val rebootAtMs =
                root["reboot_at_ms"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return
            val message = root["message"]?.jsonPrimitive?.content ?: ""
            val frame =
                writeServerRebootSchedule(
                    clear = false,
                    worldScope = worldScope,
                    rebootAtMs = rebootAtMs,
                    message = message,
                )
            if (worldId == null) {
                flushFrameToWorlds(emptyList(), frame, broadcastAllIfEmpty = true)
            } else {
                flushFrameToWorlds(listOf(worldId), frame, broadcastAllIfEmpty = false)
            }
        } catch (e: Exception) {
            log.warn("Bad world_reboot_events payload: {}", parameter, e)
        }
    }

    private fun handleWorldBroadcastPayload(parameter: String?) {
        if (parameter.isNullOrBlank()) {
            return
        }
        try {
            val root = json.parseToJsonElement(parameter).jsonObject
            val widEl = root["world_id"]
            val worldId: Int? =
                when {
                    widEl == null || widEl is KJsonNull -> null
                    else -> widEl.jsonPrimitive.content.toIntOrNull()
                }
            val worldScope = worldId ?: 0
            val message = root["message"]?.jsonPrimitive?.content ?: return
            val url = root["url"]?.jsonPrimitive?.content ?: ""
            val icon = root["icon"]?.jsonPrimitive?.content ?: ""
            val frame = writeServerBroadcast(worldScope = worldScope, message = message, url = url, icon = icon)
            if (worldId == null) {
                flushFrameToWorlds(emptyList(), frame, broadcastAllIfEmpty = true)
            } else {
                flushFrameToWorlds(listOf(worldId), frame, broadcastAllIfEmpty = false)
            }
        } catch (e: Exception) {
            log.warn("Bad world_broadcast_events payload: {}", parameter, e)
        }
    }
}
