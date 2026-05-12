package dev.or2.central.world

import dev.or2.central.http.world.WorldFlag
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WorldFlagRuneliteTest {
    @Test
    fun runeliteTypeNamesFromMaskMembers() {
        assertEquals(listOf("MEMBERS"), WorldFlag.runeliteTypeNamesFromMask(1))
    }

    @Test
    fun runeliteTypeNamesFromMaskOrderMatchesRsprox() {
        val mask = 1 or (1 shl 2) or (1 shl 8)
        assertEquals(
            listOf("MEMBERS", "PVP", "QUEST_SPEEDRUNNING"),
            WorldFlag.runeliteTypeNamesFromMask(mask),
        )
    }

    @Test
    fun maskFromCsvAcceptsRuneliteAliases() {
        assertEquals(1 or (1 shl 2), WorldFlag.maskFromCsv("MEMBERS, PVP"))
        assertEquals(1 or (1 shl 2), WorldFlag.maskFromCsv("MEMBERS,PVPWORLD"))
    }
}
