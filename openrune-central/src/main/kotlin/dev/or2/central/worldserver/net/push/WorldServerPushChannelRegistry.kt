package dev.or2.central.worldserver.net.push

import io.netty.channel.Channel
import java.util.concurrent.ConcurrentHashMap

class WorldServerPushChannelRegistry {
    private val byWorldId = ConcurrentHashMap<Int, Channel>()

    fun register(worldId: Int, channel: Channel) {
        byWorldId[worldId] = channel
    }

    fun unregister(worldId: Int, channel: Channel) {
        byWorldId.compute(worldId) { _, cur -> if (cur === channel) null else cur }
    }

    fun channel(worldId: Int): Channel? = byWorldId[worldId]

    fun registeredWorldIds(): List<Int> = byWorldId.keys.toList()
}
