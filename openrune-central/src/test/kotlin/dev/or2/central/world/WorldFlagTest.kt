package dev.or2.central.world

import dev.or2.central.http.world.WorldFlag
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class WorldFlagTest {
    @Test
    fun maskFromCsvCombinesFlags() {
        assertEquals(0x1 or 0x4 or 0x100, WorldFlag.maskFromCsv("MEMBERS, PVPWORLD, SPEEDRUN"))
        assertEquals(0, WorldFlag.maskFromCsv(""))
        assertEquals(0, WorldFlag.maskFromCsv("   "))
    }

    @Test
    fun maskFromCsvIsCaseInsensitive() {
        assertEquals(1, WorldFlag.maskFromCsv("members"))
    }

    @Test
    fun unknownFlagThrows() {
        assertFailsWith<IllegalArgumentException> {
            WorldFlag.maskFromCsv("MEMBERS,NOT_A_FLAG")
        }
    }
}
