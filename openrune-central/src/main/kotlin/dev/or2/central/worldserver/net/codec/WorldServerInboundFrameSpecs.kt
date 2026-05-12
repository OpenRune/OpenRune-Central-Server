package dev.or2.central.worldserver.net.codec

import dev.or2.central.worldserver.net.protocol.WorldServerOpcodes

object WorldServerInboundFrameSpecs {
    const val MAX_INBOUND_FRAMED_BODY: Int = 16 * 1024

    const val WORLD_KEY_MAX_BYTES: Int = 4096

    const val LOGIN_USERNAME_MAX_UTF8: Int = 64 * 4
    const val LOGIN_PASSWORD_MAX_UTF8: Int = 256 * 4

    const val TOKEN_BODY_BYTES: Int = 2 + WorldServerOpcodes.TOKEN_BYTES

    data class InboundSpec(
        val opcode: Int,
        val name: String,
        val minBody: Int,
        val maxBody: Int,
    )

    val inbound: List<InboundSpec> =
        listOf(
            InboundSpec(
                opcode = WorldServerOpcodes.OP_WORLD_HELLO,
                name = "WORLD_HELLO",
                minBody = 4 + 2 + 4 + 2 + 0,
                maxBody = 4 + 2 + 4 + 2 + WORLD_KEY_MAX_BYTES,
            ),
            InboundSpec(
                opcode = WorldServerOpcodes.OP_LOGIN,
                name = "LOGIN",
                minBody = 2 + 0 + 2 + 0,
                maxBody = 2 + LOGIN_USERNAME_MAX_UTF8 + 2 + LOGIN_PASSWORD_MAX_UTF8 + 4,
            ),
            InboundSpec(
                opcode = WorldServerOpcodes.OP_PUSH_SUBSCRIBE,
                name = "PUSH_SUBSCRIBE",
                minBody = 0,
                maxBody = 0,
            ),
            InboundSpec(
                opcode = WorldServerOpcodes.OP_HEARTBEAT,
                name = "HEARTBEAT",
                minBody = TOKEN_BODY_BYTES,
                maxBody = TOKEN_BODY_BYTES,
            ),
            InboundSpec(
                opcode = WorldServerOpcodes.OP_LOGOUT,
                name = "LOGOUT",
                minBody = TOKEN_BODY_BYTES,
                maxBody = TOKEN_BODY_BYTES,
            ),
        )

    private val inboundByOpcode: Map<Int, InboundSpec> = inbound.associateBy { it.opcode }

    fun inboundSpec(opcode: Int): InboundSpec? = inboundByOpcode[opcode]

    fun validateInboundBody(
        opcode: Int,
        bodyBytes: Int,
    ): String? {
        val spec = inboundByOpcode[opcode] ?: return "unknown_opcode"
        if (bodyBytes < spec.minBody) {
            return "too_short"
        }
        if (bodyBytes > spec.maxBody) {
            return "too_long"
        }
        return null
    }

    data class OutboundSpec(
        val opcode: Int,
        val name: String,
        val minBody: Int,
        val maxBody: Int,
    )

    private val loginOkMaxV3 = 2 + WorldServerOpcodes.TOKEN_BYTES + 8 + 2 + WorldServerOpcodes.LOGIN_OK_RIGHTS_MAX_BYTES

    private val loginFailMaxBodyV5: Int =
        4 + 3 * (2 + WorldServerOpcodes.LOGIN_FAIL_SCRIPT_LINE_MAX_UTF8_BYTES)

    val outbound: List<OutboundSpec> =
        listOf(
            OutboundSpec(WorldServerOpcodes.OP_HELLO_ACK, "HELLO_ACK", 0, 0),
            OutboundSpec(WorldServerOpcodes.OP_HELLO_REJECT, "HELLO_REJECT", 1, 1),
            OutboundSpec(WorldServerOpcodes.OP_LOGIN_FAIL, "LOGIN_FAIL", 4, loginFailMaxBodyV5),
            OutboundSpec(
                WorldServerOpcodes.OP_LOGIN_OK,
                "LOGIN_OK",
                minBody = 2 + WorldServerOpcodes.TOKEN_BYTES + 8,
                maxBody = loginOkMaxV3,
            ),
            OutboundSpec(WorldServerOpcodes.OP_HEARTBEAT_ACK, "HEARTBEAT_ACK", 0, 0),
            OutboundSpec(WorldServerOpcodes.OP_PUSH_SUBSCRIBE_ACK, "PUSH_SUBSCRIBE_ACK", 0, 0),
            OutboundSpec(WorldServerOpcodes.OP_LOGOUT_ACK, "LOGOUT_ACK", 0, 0),
            OutboundSpec(WorldServerOpcodes.OP_SERVER_REVOKE_LOGIN, "SERVER_REVOKE_LOGIN", 8 + 4, 8 + 4),
            OutboundSpec(WorldServerOpcodes.OP_SERVER_MUTE_UPDATE, "SERVER_MUTE_UPDATE", 8 + 4 + 8, 8 + 4 + 8),
            OutboundSpec(WorldServerOpcodes.OP_SERVER_KICK, "SERVER_KICK", 8 + 4, 8 + 4),
            OutboundSpec(
                WorldServerOpcodes.OP_SERVER_REBOOT,
                "SERVER_REBOOT",
                minBody = 1 + 4 + 8 + 2 + 0,
                maxBody = 1 + 4 + 8 + 2 + 2048,
            ),
            OutboundSpec(
                WorldServerOpcodes.OP_SERVER_BROADCAST,
                "SERVER_BROADCAST",
                minBody = 4 + 2 + 0 + 2 + 0 + 2 + 0,
                maxBody = 4 + 2 + 2048 + 2 + 2048 + 2 + 2048,
            ),
        )

    fun loginOkMaxBody(clientProtocolVersion: Int): Int {
        val base = 2 + WorldServerOpcodes.TOKEN_BYTES + 8
        return if (clientProtocolVersion >= 3) {
            base + 2 + WorldServerOpcodes.LOGIN_OK_RIGHTS_MAX_BYTES
        } else {
            base
        }
    }

    fun loginFailMaxBody(): Int = loginFailMaxBodyV5
}
