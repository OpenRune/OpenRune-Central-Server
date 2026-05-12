package dev.or2.central.worldserver.session

import dev.or2.central.account.AccountRepository
import dev.or2.central.account.PasswordHasher
import dev.or2.central.util.crypto.sha256
import dev.or2.central.worldserver.net.codec.FrameInput
import dev.or2.central.worldserver.net.codec.WorldServerInboundFrameSpecs
import dev.or2.central.worldserver.net.codec.readFramePayload
import dev.or2.central.worldserver.net.codec.writeHeartbeatAck
import dev.or2.central.worldserver.net.codec.writeHelloAck
import dev.or2.central.worldserver.net.codec.writeHelloReject
import dev.or2.central.worldserver.net.codec.writeLoginFail
import dev.or2.central.worldserver.net.codec.writeLoginOk
import dev.or2.central.worldserver.net.codec.writeLogoutAck
import dev.or2.central.worldserver.net.codec.writePushSubscribeAck
import dev.or2.central.worldserver.net.protocol.WorldServerOpcodes
import dev.or2.central.worldserver.telemetry.WorldServerTelemetry
import dev.or2.central.account.punishment.PunishmentLoginBlock
import dev.or2.central.account.punishment.PunishmentRepository
import dev.or2.central.http.world.WorldKeyVerifier
import dev.or2.central.http.world.WorldListCache
import dev.or2.central.http.world.WorldLoginGateRepository
import dev.or2.central.http.world.WorldRepository
import dev.or2.central.http.world.ops.WorldOpsRepository
import dev.or2.sql.OpenRuneSql
import java.security.SecureRandom
import java.sql.Connection
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource
import org.slf4j.LoggerFactory

class WorldServerSessionService(
    private val dataSource: DataSource,
    private val worldRepository: WorldRepository,
    private val worldKeyVerifier: WorldKeyVerifier,
    private val accountRepository: AccountRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionRepository: SessionRepository,
    private val worldListCache: WorldListCache?,
    private val punishmentRepository: PunishmentRepository,
    private val worldOpsRepository: WorldOpsRepository,
    private val worldLoginGateRepository: WorldLoginGateRepository,
    private val telemetry: WorldServerTelemetry = WorldServerTelemetry.None,
) {
    private val log = LoggerFactory.getLogger(WorldServerSessionService::class.java)
    private val random = SecureRandom()

    fun handle(
        state: WorldServerConnectionState,
        payload: ByteArray,
    ): WorldServerHandleResult {
        if (payload.isEmpty()) {
            telemetry.recordInboundRejected(-1, "empty_frame")
            return WorldServerHandleResult.CloseSilent
        }
        val input = readFramePayload(payload)
        val bodyLen = input.remainingAfterOpcode
        val shapeErr = WorldServerInboundFrameSpecs.validateInboundBody(input.opcode, bodyLen)
        if (shapeErr != null) {
            telemetry.recordInboundRejected(input.opcode, shapeErr)
            return rejectBadShape(state)
        }
        telemetry.recordInboundValidated(input.opcode)
        return when (input.opcode) {
            WorldServerOpcodes.OP_WORLD_HELLO -> handleHello(state, input)
            WorldServerOpcodes.OP_LOGIN -> handleLogin(state, input)
            WorldServerOpcodes.OP_PUSH_SUBSCRIBE -> handlePushSubscribe(state, input)
            WorldServerOpcodes.OP_HEARTBEAT -> handleHeartbeat(state, input)
            WorldServerOpcodes.OP_LOGOUT -> handleLogout(state, input)
            else -> {
                telemetry.recordInboundRejected(input.opcode, "unhandled_opcode")
                if (!state.handshakeDone) {
                    WorldServerHandleResult.Reply(
                        writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                        closeAfterWrite = true,
                    )
                } else {
                    WorldServerHandleResult.Reply(
                        writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME),
                        closeAfterWrite = false,
                    )
                }
            }
        }
    }

    private fun rejectBadShape(state: WorldServerConnectionState): WorldServerHandleResult =
        if (!state.handshakeDone) {
            WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                closeAfterWrite = true,
            )
        } else {
            WorldServerHandleResult.Reply(
                writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME),
                closeAfterWrite = false,
            )
        }

    private fun handleHello(
        state: WorldServerConnectionState,
        input: FrameInput,
    ): WorldServerHandleResult {
        if (state.handshakeDone) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                closeAfterWrite = true,
            )
        }
        val magic = input.readMagic()
        if (magic != WorldServerOpcodes.MAGIC) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                closeAfterWrite = true,
            )
        }
        val version = input.readUnsignedShortCompat()
        if (version < WorldServerOpcodes.MIN_CLIENT_PROTOCOL_VERSION ||
            version > WorldServerOpcodes.MAX_CLIENT_PROTOCOL_VERSION
        ) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                closeAfterWrite = true,
            )
        }
        state.protocolVersion = version
        val worldId = input.readIntCompat()
        val keyLen = input.readUnsignedShortCompat()
        if (keyLen > WorldServerInboundFrameSpecs.WORLD_KEY_MAX_BYTES) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                closeAfterWrite = true,
            )
        }
        val keyBytes = input.readFully(keyLen)
        if (input.trailingUnreadBytes() != 0) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_PROTOCOL),
                closeAfterWrite = true,
            )
        }
        val row =
            worldRepository.findForAuth(worldId)
                ?: return WorldServerHandleResult.Reply(
                    writeHelloReject(WorldServerOpcodes.HELLO_REASON_UNKNOWN_WORLD),
                    closeAfterWrite = true,
                )
        if (!row.enabled) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_WORLD_DISABLED),
                closeAfterWrite = true,
            )
        }
        if (!worldKeyVerifier.verify(row, keyBytes)) {
            return WorldServerHandleResult.Reply(
                writeHelloReject(WorldServerOpcodes.HELLO_REASON_BAD_KEY),
                closeAfterWrite = true,
            )
        }
        state.worldId = worldId
        state.maxPlayers = row.maxPlayers
        state.loginRestrictionsEnabled = row.loginRestrictionsEnabled
        state.loginMinTotalLevel = row.loginMinTotalLevel.coerceAtLeast(0)
        state.loginMinRightsToken = row.loginMinRightsToken
        state.loginGateMinLevelEnabled = row.loginGateMinLevelEnabled
        state.loginGateRightsEnabled = row.loginGateRightsEnabled
        state.loginGateWhitelistEnabled = row.loginGateWhitelistEnabled
        state.handshakeDone = true
        return WorldServerHandleResult.Reply(writeHelloAck())
    }

    private fun handlePushSubscribe(
        state: WorldServerConnectionState,
        input: FrameInput,
    ): WorldServerHandleResult {
        if (!state.handshakeDone) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN))
        }
        if (input.trailingUnreadBytes() != 0) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))
        }
        state.subscribedForServerPush = true
        return WorldServerHandleResult.Reply(writePushSubscribeAck())
    }

    private fun handleLogin(
        state: WorldServerConnectionState,
        input: FrameInput,
    ): WorldServerHandleResult {
        if (!state.handshakeDone) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN))
        }
        val username = input.readUtf8LenPrefixed()
        val password = input.readUtf8LenPrefixed()
        val trailingAfterCreds = input.trailingUnreadBytes()
        val loginCharacterId: Int? =
            when {
                trailingAfterCreds == 0 -> null
                state.protocolVersion >= 4 && trailingAfterCreds == 4 -> input.readIntCompat()
                else ->
                    return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))
            }
        if (input.trailingUnreadBytes() != 0) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))
        }
        if (username.isBlank() || username.length > 64 || password.length > 256) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))
        }
        val loginKey = username.trim()
        var accounts = accountRepository.findByUsername(loginKey)
        val registerNow = System.currentTimeMillis()
        if (accounts == null) {
            val hash = passwordHasher.hash(password)
            accountRepository.insertIfAbsent(loginKey, hash, registerNow)
            accounts = accountRepository.findByUsername(loginKey)
        }
        if (accounts == null || !passwordHasher.verify(accounts.passwordHash, password)) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_INVALID))
        }
        val effectiveCharId = loginCharacterId?.takeIf { it > 0 }
        if (effectiveCharId != null &&
            !punishmentRepository.characterBelongsToAccount(effectiveCharId, accounts.id)
        ) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))
        }
        when (
            punishmentRepository.blockingLoginSanction(
                accounts.id,
                effectiveCharId,
            )
        ) {
            PunishmentLoginBlock.LOCKED ->
                return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_LOCKED))
            PunishmentLoginBlock.BANNED ->
                return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BANNED))
            null -> Unit
        }
        if (state.loginRestrictionsEnabled) {
            val deny =
                evaluateWorldLoginDeny(
                    state = state,
                    loginUsername = loginKey,
                    accountRights = accounts.rights,
                    accountId = accounts.id,
                    characterId = effectiveCharId,
                )
            if (deny != null) {
                return WorldServerHandleResult.Reply(
                    writeLoginFail(
                        deny.code,
                        state.protocolVersion,
                        Triple(deny.line1, deny.line2, deny.line3),
                    ),
                )
            }
        }
        if (worldOpsRepository.shouldBlockLoginForScheduledReboot(state.worldId, System.currentTimeMillis())) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_UPDATE_IN_PROGRESS))
        }
        val max = state.maxPlayers
        val token = ByteArray(WorldServerOpcodes.TOKEN_BYTES).also { random.nextBytes(it) }
        val tokenHash = sha256(token)
        val now = System.currentTimeMillis()
        val dayUtc =
            Instant.ofEpochMilli(now)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .toString()
        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    deleteSessionsForAccount(conn, accounts.id)
                    if (max != null && countSessionsForWorld(conn, state.worldId) >= max) {
                        conn.rollback()
                        return WorldServerHandleResult.Reply(
                            writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_WORLD_FULL),
                        )
                    }
                    insertSession(conn, accounts.id, state.worldId, tokenHash, now)
                    insertLoginEventOrIgnore(conn, accounts.id, state.worldId, dayUtc, now)
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            log.warn("Login transaction failed (worldId={})", state.worldId, e)
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))
        }
        worldListCache?.rebuild()
        telemetry.recordLoginSuccess()
        return WorldServerHandleResult.Reply(
            writeLoginOk(token, accounts.id, accounts.rights, state.protocolVersion),
        )
    }

    private fun handleHeartbeat(
        state: WorldServerConnectionState,
        input: FrameInput,
    ): WorldServerHandleResult {
        if (!state.handshakeDone) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN))
        }
        val token = readSessionTokenStrict(input) ?: return loginBadFrameReply()
        val row = sessionRepository.findByTokenHash(sha256(token)) ?: return failBadToken()
        if (row.worldId != state.worldId) {
            return failBadToken()
        }
        sessionRepository.touchById(row.id, System.currentTimeMillis())
        return WorldServerHandleResult.Reply(writeHeartbeatAck())
    }

    private fun handleLogout(
        state: WorldServerConnectionState,
        input: FrameInput,
    ): WorldServerHandleResult {
        if (!state.handshakeDone) {
            return WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN))
        }
        val token = readSessionTokenStrict(input) ?: return loginBadFrameReply()
        val row = sessionRepository.findByTokenHash(sha256(token)) ?: return failBadToken()
        if (row.worldId != state.worldId) {
            return failBadToken()
        }
        sessionRepository.deleteById(row.id)
        worldListCache?.rebuild()
        return WorldServerHandleResult.Reply(writeLogoutAck(), closeAfterWrite = true)
    }

    private fun failBadToken(): WorldServerHandleResult.Reply =
        WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_TOKEN))

    private fun loginBadFrameReply(): WorldServerHandleResult.Reply =
        WorldServerHandleResult.Reply(writeLoginFail(WorldServerOpcodes.LOGIN_FAIL_BAD_FRAME))

    private data class WorldGateDeny(
        val code: Int,
        val line1: String,
        val line2: String,
        val line3: String,
    )

    private fun evaluateWorldLoginDeny(
        state: WorldServerConnectionState,
        loginUsername: String,
        accountRights: String,
        accountId: Long,
        characterId: Int?,
    ): WorldGateDeny? {
        if (!state.loginRestrictionsEnabled) {
            return null
        }
        val minRequired = state.loginMinTotalLevel.coerceAtLeast(0)
        val anyLevel = state.loginGateMinLevelEnabled && minRequired > 0
        val anyRights = state.loginGateRightsEnabled && !state.loginMinRightsToken.isNullOrBlank()
        val hasWhitelistRows = worldLoginGateRepository.hasLoginWhitelistEntries(state.worldId)
        val anyWhitelist = state.loginGateWhitelistEnabled && hasWhitelistRows
        if (!anyLevel && !anyRights && !anyWhitelist) {
            return null
        }
        var ok = false
        val total: Int? =
            if (anyLevel && characterId != null) {
                worldLoginGateRepository.characterTotalBaseLevel(characterId, accountId)
            } else {
                null
            }
        if (anyLevel && characterId != null && (total ?: 0) >= minRequired) {
            ok = true
        }
        if (!ok && anyWhitelist && worldLoginGateRepository.isLoginWhitelisted(state.worldId, loginUsername)) {
            ok = true
        }
        if (!ok && anyRights) {
            val t = state.loginMinRightsToken!!
            if (rightsHasToken(accountRights, t)) {
                ok = true
            }
        }
        if (ok) {
            return null
        }
        val tok = (state.loginMinRightsToken ?: "").trim()
        if (anyLevel && (characterId == null || (total ?: 0) < minRequired)) {
            val line1 = "You need a total level of at least $minRequired."
            val line2 =
                if (characterId == null) {
                    "This world could not verify your character's skills on login."
                } else {
                    "Your total level is ${total ?: 0}."
                }
            val line3 =
                if (characterId == null) {
                    "Select your character before logging in, or update your game server."
                } else {
                    "Train your skills, then try again."
                }
            return WorldGateDeny(WorldServerOpcodes.LOGIN_FAIL_WORLD_MIN_LEVEL, line1, line2, line3)
        }
        if (anyWhitelist) {
            return WorldGateDeny(
                WorldServerOpcodes.LOGIN_FAIL_WORLD_WHITELIST,
                "This world is restricted.",
                "Your account is not on the login whitelist.",
                "Contact staff if you believe this is a mistake.",
            )
        }
        if (anyRights) {
            return WorldGateDeny(
                WorldServerOpcodes.LOGIN_FAIL_WORLD_RIGHTS,
                "This world requires special account access.",
                """You need "${tok.substringAfter(".")}" access to log in.""",
                "",
            )
        }
        return WorldGateDeny(
            WorldServerOpcodes.LOGIN_FAIL_WORLD_ACCESS,
            "You cannot log in to this world right now.",
            "World login rules blocked this account.",
            "",
        )
    }

    private fun rightsHasToken(
        rights: String,
        token: String,
    ): Boolean {
        val t = token.trim().lowercase()
        if (t.isEmpty()) {
            return false
        }
        return rights.split(',').any { it.trim().lowercase() == t }
    }

    private fun readSessionTokenStrict(input: FrameInput): ByteArray? {
        val tokenLen = input.readUnsignedShortCompat()
        if (tokenLen != WorldServerOpcodes.TOKEN_BYTES) return null
        val token = input.readFully(tokenLen)
        if (input.trailingUnreadBytes() != 0) return null
        return token
    }

    private fun deleteSessionsForAccount(
        conn: Connection,
        accountId: Long,
    ) {
        val sql = OpenRuneSql.text("central/session/delete_by_account_id.sql")
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, accountId)
            ps.executeUpdate()
        }
    }

    private fun countSessionsForWorld(
        conn: Connection,
        worldId: Int,
    ): Int {
        val sql = OpenRuneSql.text("central/session/count_for_world.sql")
        return conn.prepareStatement(sql).use { ps ->
            ps.setInt(1, worldId)
            ps.executeQuery().use { rs ->
                require(rs.next())
                rs.getInt(1)
            }
        }
    }

    private fun insertSession(
        conn: Connection,
        accountId: Long,
        worldId: Int,
        tokenHash: ByteArray,
        now: Long,
    ) {
        val sql = OpenRuneSql.text("central/session/insert.sql")
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, accountId)
            ps.setInt(2, worldId)
            ps.setBytes(3, tokenHash)
            ps.setLong(4, now)
            ps.setLong(5, now)
            ps.executeUpdate()
        }
    }

    private fun insertLoginEventOrIgnore(
        conn: Connection,
        accountId: Long,
        worldId: Int,
        dayUtc: String,
        now: Long,
    ) {
        val sql = OpenRuneSql.text("central/analytics/login_event_insert.sql")
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, accountId)
            ps.setInt(2, worldId)
            ps.setString(3, dayUtc)
            ps.setLong(4, now)
            ps.executeUpdate()
        }
    }
}
