package dev.openrune.central.client.packet

import dev.openrune.central.client.CentralConnection
import dev.openrune.central.packet.registry.OutgoingPackets

/**
 * Background dispatcher that consumes decoded packets and invokes registered handlers.
 */
class ClientPacketDispatcher(
    private val connection: CentralConnection,
    private val router: ClientPacketRouter
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread =
            Thread {
                val call = ClientPacketCall(connection)
                while (running && connection.isActive()) {
                    val pkt = connection.nextPacketBlocking() // blocks
                    val spec = OutgoingPackets.Companion.byId(pkt.opcode) ?: continue
                    val decoded =
                        try {
                            spec.decode(pkt.payload)
                        } catch (t: Throwable) {
                            continue
                        }
                    // If someone is awaiting this opcode+requestId as a one-shot response, fulfill that first.
                    if (connection.tryCompleteCorrelated(pkt.opcode, decoded)) {
                        continue
                    }
                    try {
                        router.dispatch(call, pkt.opcode, decoded)
                    } catch (t: Throwable) {
                        // keep dispatcher alive
                    }
                }
            }.apply {
                name = "central-packet-client-dispatcher"
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