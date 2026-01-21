package dev.openrune.central.api

import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateAuthPayloadTest {
    @Test
    fun `payload format is stable`() {
        val ts = "123"
        val worldId = "1"
        val method = "POST"
        val path = "/api/private/test"
        val body = """{"a":1}"""

        val bytes = buildPrivateAuthPayload(ts, worldId, method, path, body)
        val s = bytes.toString(Charsets.UTF_8)
        assertEquals("123\n1\nPOST\n/api/private/test\n{\"a\":1}", s)
    }
}

