package dev.openrune.central.api

import kotlinx.serialization.Serializable

/**
 * Shared account/login DTOs used by both the central server and world servers (api-client).
 *
 * Note: we intentionally avoid `@SerialName` where it doesn't change the wire format to keep this
 * compact. Property names are already the desired JSON keys.
 */

@Serializable
enum class PlayerLoadResponse { NEW_ACCOUNT, LOAD, ALREADY_ONLINE, INVALID_CREDENTIALS, INVALID_RECONNECTION, MALFORMED, OFFLINE_SERVER }

/**
 * Stable unique identifier for a login/account group.
 *
 * We derive this from the primary login username (lowercased) because it does not change.
 */
@Serializable
data class PlayerUID(val value: Long)

/**
 * Single request shape reused for:
 * - POST /api/private/login/request
 * - POST /api/private/details
 * - POST /api/private/accounts/link  (requires `newAccount`)
 */
@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    val xteas: List<Int> = emptyList(),
    val newAccount: String? = null,
)

@Serializable
data class LoginResponseDto(
    val result: PlayerLoadResponse,
    val login: LoginDetailsDto? = null
)

@Serializable
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

@Serializable
data class LoginDetailsDto(
    val loginUsername: String,
    val createdAt: Long,
    val lastLogin: Long,
    val linkedAccounts: List<PlayerDetailsDto> = emptyList(),
)

@Serializable
data class LinkAccountResponseDto(
    val ok: Boolean,
    val linkedAccounts: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Logout request from a world server.
 *
 * We use:
 * - uid: stable group uid (from login response)
 * - account: the specific account username (lowercase or raw; server will normalize)
 * - previousXteas: last session xteas to persist for reconnection checks
 */
@Serializable
data class LogoutRequestDto(
    val uid: Long,
    val account: String,
    val previousXteas: List<Int> = emptyList(),
)

@Serializable
data class LogoutResponseDto(
    val ok: Boolean,
    val error: String? = null
)

/**
 * World server -> central: persist account save JSON (opaque to central).
 */
@Serializable
data class PlayerSaveUpsertRequestDto(
    val uid: Long,
    val account: String,
    /**
     * Opaque JSON payload representing the account save.
     * Typically `Document.toJson()` from the world server.
     */
    val data: String,
)

@Serializable
data class PlayerSaveUpsertResponseDto(
    val ok: Boolean,
    val error: String? = null
)

@Serializable
data class PlayerSaveLoadRequestDto(
    val uid: Long,
    val account: String,
)

@Serializable
data class PlayerSaveLoadResponseDto(
    val ok: Boolean,
    val data: String? = null,
    val error: String? = null
)

