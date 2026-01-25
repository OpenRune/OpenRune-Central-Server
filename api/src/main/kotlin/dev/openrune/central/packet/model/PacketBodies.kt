package dev.openrune.central.packet.model

import dev.openrune.central.LogsResponse
import dev.openrune.central.PlayerLoadResponse
import dev.openrune.central.PlayerSaveLoadResponse
import dev.openrune.central.PlayerUID

/**
 * Marker interfaces for packet bodies.
 */
sealed interface PacketBody {
    val requestId: Long
}

interface IncomingPacketBody : PacketBody
interface OutgoingPacketBody : PacketBody

data class LogoutIncoming(
    val uid: PlayerUID,
    val account: String,
    val previousXteas : List<Int>,
    override val requestId: Long = 0L
) : IncomingPacketBody, PacketBody

data class LoginRequestIncoming(
    val username: String,
    val password: String,
    val xteas : List<Int>,
    override val requestId: Long = 0L
) : IncomingPacketBody, PacketBody

data class LoginResponseOutgoing(
    val result: PlayerLoadResponse,
    val login: LoginDetailsDto? = null,
    override val requestId: Long = 0L
) : OutgoingPacketBody, PacketBody

data class PlayerSaveLoadRequestIncoming(
    val uid: Long,
    val account: String,
    override val requestId: Long = 0L
) : IncomingPacketBody, PacketBody

data class PlayerSaveLoadResponseOutgoing(
    val result: PlayerSaveLoadResponse,
    val data: String? = null,
    override val requestId: Long = 0L
) : OutgoingPacketBody, PacketBody

data class PlayerSaveUpsertRequestIncoming(
    val uid: Long,
    val account: String,
    val data: String,
    override val requestId: Long = 0L
) : IncomingPacketBody, PacketBody

data class LogsRequestIncoming(
    val data: String,
    override val requestId: Long = 0L
) : IncomingPacketBody, PacketBody

data class LogsResponseOutgoing(
    val result: LogsResponse,
    val error: String? = null,
    override val requestId: Long = 0L
) : OutgoingPacketBody, PacketBody

data class PlayerSaveOutgoing(
    val result: LogsResponse,
    val error: String? = null,
    override val requestId: Long = 0L
) : OutgoingPacketBody, PacketBody

data class LogoutResponseOutgoing(
    val result: LogsResponse,
    val error: String? = null,
    override val requestId: Long = 0L
) : OutgoingPacketBody, PacketBody


data class LoginDetailsDto(
    val loginUsername: String,
    val createdAt: Long,
    val lastLogin: Long,
    val linkedAccounts: List<PlayerDetailsDto> = emptyList(),
)

data class PlayerDetailsDto(
    /**
     * Account username (lowercase).
     */
    val username: String,
    /**
     * Stable unique id for this account group (based on primary login username).
     */
    val uid: PlayerUID = PlayerUID(0L),
    /**
     * Last successful session xteas for this account (used for reconnection checks).
     */
    val previousXteas: List<Int> = emptyList(),
    val previousDisplayName: String = "",
    val dateChanged: Long = -1,
    val registryDate: Long
)

