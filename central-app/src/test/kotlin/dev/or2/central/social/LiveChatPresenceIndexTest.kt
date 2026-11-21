package dev.or2.central.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveChatPresenceIndexTest {
    private val kind = LiveChatPresenceIndex.Kind.FRIEND_CHAT
    private val first = LiveChatPresenceIndex.ChannelKey(kind, 10)
    private val second = LiveChatPresenceIndex.ChannelKey(kind, 20)
    private val alpha = LiveChatPresenceIndex.Member(1, "Alpha", 301, 7)
    private val beta = LiveChatPresenceIndex.Member(2, "Beta", 302, 0)

    @Test
    fun joinRespectsCapacity() {
        val index = LiveChatPresenceIndex()
        assertIs<LiveChatPresenceIndex.JoinResult.Joined>(index.join(first, alpha, 301, 2))
        assertIs<LiveChatPresenceIndex.JoinResult.Joined>(index.join(first, beta, 302, 2))
        assertEquals(setOf(301, 302), index.recipientWorlds(first))
        val gamma = LiveChatPresenceIndex.Member(3, "Gamma", 301, 0)
        assertIs<LiveChatPresenceIndex.JoinResult.Full>(index.join(first, gamma, 301, 2))
    }

    @Test
    fun joinFromStaleWorldIsRejected() {
        val index = LiveChatPresenceIndex()
        index.join(first, alpha, 301, 2)
        assertEquals(LiveChatPresenceIndex.JoinResult.StaleWorld, index.join(first, alpha.copy(worldId = 302), 301, 2))
    }

    @Test
    fun delayedLeaveFromOldWorldIsIgnored() {
        val index = LiveChatPresenceIndex()
        index.join(first, alpha, 301, 2)
        index.join(first, beta, 302, 2)
        assertIs<LiveChatPresenceIndex.JoinResult.Joined>(index.join(first, alpha.copy(worldId = 302), 302, 2))
        assertNull(index.leave(kind, 1, 301))
        assertEquals(2, index.snapshot(first).members.size)
    }

    @Test
    fun joiningAnotherChannelOfSameKindLeavesTheFirst() {
        val index = LiveChatPresenceIndex()
        index.join(first, alpha, 301, 2)
        index.join(first, beta, 302, 2)
        assertIs<LiveChatPresenceIndex.JoinResult.Joined>(index.join(second, alpha.copy(worldId = 302), 302, 2))
        assertEquals(listOf(2), index.snapshot(first).members.map { it.characterId })
    }

    @Test
    fun removeWorldClearsOnlyThatWorld() {
        val index = LiveChatPresenceIndex()
        index.join(first, beta, 302, 2)
        index.join(second, alpha.copy(worldId = 302), 302, 2)
        assertTrue(index.removeWorld(301).isEmpty())
        assertEquals(2, index.removeWorld(302).size)
        assertTrue(index.snapshot(first).members.isEmpty())
        assertTrue(index.snapshot(second).members.isEmpty())
    }
}
