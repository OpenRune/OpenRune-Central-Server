package dev.or2.central.worldserver.net.codec

import dev.or2.central.worldserver.net.protocol.WorldServerOpcodes

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class WorldServerInboundFrameSpecsTest {
    @Test
    fun inboundHelloBounds() {
        assertNull(WorldServerInboundFrameSpecs.validateInboundBody(WorldServerOpcodes.OP_WORLD_HELLO, 12))
        assertEquals(
            "too_short",
            WorldServerInboundFrameSpecs.validateInboundBody(WorldServerOpcodes.OP_WORLD_HELLO, 11),
        )
        assertEquals(
            "too_long",
            WorldServerInboundFrameSpecs.validateInboundBody(
                WorldServerOpcodes.OP_WORLD_HELLO,
                4 + 2 + 4 + 2 + WorldServerInboundFrameSpecs.WORLD_KEY_MAX_BYTES + 1,
            ),
        )
    }

    @Test
    fun inboundHeartbeatExactLength() {
        assertNull(
            WorldServerInboundFrameSpecs.validateInboundBody(
                WorldServerOpcodes.OP_HEARTBEAT,
                WorldServerInboundFrameSpecs.TOKEN_BODY_BYTES,
            ),
        )
        assertEquals(
            "too_short",
            WorldServerInboundFrameSpecs.validateInboundBody(WorldServerOpcodes.OP_HEARTBEAT, 33),
        )
        assertEquals(
            "too_long",
            WorldServerInboundFrameSpecs.validateInboundBody(WorldServerOpcodes.OP_HEARTBEAT, 35),
        )
    }

    @Test
    fun unknownOpcode() {
        assertEquals("unknown_opcode", WorldServerInboundFrameSpecs.validateInboundBody(0x99, 0))
    }

    @Test
    fun inboundLoginAllowsOptionalCharacterIdTrailer() {
        val max =
            2 + WorldServerInboundFrameSpecs.LOGIN_USERNAME_MAX_UTF8 +
                2 + WorldServerInboundFrameSpecs.LOGIN_PASSWORD_MAX_UTF8 + 4
        assertNull(WorldServerInboundFrameSpecs.validateInboundBody(WorldServerOpcodes.OP_LOGIN, max))
        assertEquals(
            "too_long",
            WorldServerInboundFrameSpecs.validateInboundBody(WorldServerOpcodes.OP_LOGIN, max + 1),
        )
    }
}
