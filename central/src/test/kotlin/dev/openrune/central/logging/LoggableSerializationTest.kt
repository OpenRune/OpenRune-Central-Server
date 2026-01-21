package dev.openrune.central.logging

import kotlin.test.Test
import kotlin.test.assertTrue

class LoggableSerializationTest {
    @Test
    fun `polymorphic Loggable json includes discriminator`() {
        val log: Loggable = PlayerCommandLog(player = "Alice", command = "tele", arguments = listOf("home"))
        val json = LoggingJson.json.encodeToString(Loggable.serializer(), log)
        assertTrue(json.contains("\"logType\":\"PlayerCommandLog\""), json)
    }
}

