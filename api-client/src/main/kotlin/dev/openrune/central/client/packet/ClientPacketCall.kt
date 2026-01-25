package dev.openrune.central.client.packet

import dev.openrune.central.client.CentralConnection

/**
 * Client-side packet handler context (world side).
 *
 * Use [connection] to send follow-up packets back to central.
 */
class ClientPacketCall(val connection: CentralConnection) {
    val worldId: Int get() = connection.worldIdUnsafe()
}