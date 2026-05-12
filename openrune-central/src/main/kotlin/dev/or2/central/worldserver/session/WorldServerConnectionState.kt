package dev.or2.central.worldserver.session

class WorldServerConnectionState {
    var worldId: Int = -1
    var handshakeDone: Boolean = false
    var maxPlayers: Int? = null
    var protocolVersion: Int = 0
    var remoteHost: String? = null
    var subscribedForServerPush: Boolean = false

    var loginRestrictionsEnabled: Boolean = false
    var loginMinTotalLevel: Int = 0
    var loginMinRightsToken: String? = null
    var loginGateMinLevelEnabled: Boolean = false
    var loginGateRightsEnabled: Boolean = false
    var loginGateWhitelistEnabled: Boolean = false
}
