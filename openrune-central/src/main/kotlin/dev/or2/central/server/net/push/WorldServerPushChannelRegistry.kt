package dev.or2.central.server.net.push

import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.util.concurrent.ConcurrentHashMap

class WorldServerPushChannelRegistry {

    private val byWorldId = ConcurrentHashMap<Int, Channel>()

    fun register(worldId: Int, channel: Channel) {
        val previous = byWorldId.put(worldId, channel)

        if (previous != null && previous !== channel && previous.isActive) {
            previous.close()
        }

        channel.closeFuture().addListener(ChannelFutureListener {
            byWorldId.compute(worldId) { _, cur ->
                if (cur === channel) null else cur
            }
        })
    }

    fun unregister(worldId: Int, channel: Channel) {
        byWorldId.compute(worldId) { _, cur ->
            if (cur === channel) null else cur
        }
    }

    fun channel(worldId: Int): Channel? =
        byWorldId[worldId]?.takeIf { it.isActive }

    fun registeredWorldIds(): List<Int> =
        byWorldId.keys.toList()
}