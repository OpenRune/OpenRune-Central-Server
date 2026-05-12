package dev.or2.central.http.world

data class WorldAuthRow(
    val worldId: Int,
    val enabled: Boolean,
    val maxPlayers: Int?,
    val worldKeySha256: ByteArray?,
    val loginRestrictionsEnabled: Boolean,
    val loginMinTotalLevel: Int,
    val loginMinRightsToken: String?,
    val loginGateMinLevelEnabled: Boolean,
    val loginGateRightsEnabled: Boolean,
    val loginGateWhitelistEnabled: Boolean,
    /** Realm `dev_mode`: when true, LOGIN_OK always sends `modlevel.owner` (overrides account rights on wire). */
    val realmDevMode: Boolean,
)
