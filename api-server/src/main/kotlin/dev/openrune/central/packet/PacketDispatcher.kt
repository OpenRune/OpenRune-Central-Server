package dev.openrune.central.packet

import dev.openrune.central.packet.routing.PacketRouter
import org.slf4j.LoggerFactory

/**
 * Background dispatcher that pulls packets off the Netty server queue and routes them.
 */
class PacketDispatcher(private val router: PacketRouter) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val logger = LoggerFactory.getLogger(PacketDispatcher::class.java)

    fun start() {
        if (running) return
        running = true
        thread =
            Thread {
                while (running) {
                    val pkt = CentralPacketServer.incoming.take()
                    try {
                        router.dispatch(pkt)
                    } catch (t: Throwable) {
                        logger.error("Packet dispatch loop crashed for world={} opcode={} seq={}", pkt.worldId, pkt.opcode, pkt.seq, t)
                    }
                }
            }.apply {
                name = "central-packet-dispatcher"
                isDaemon = true
                start()
            }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }
}