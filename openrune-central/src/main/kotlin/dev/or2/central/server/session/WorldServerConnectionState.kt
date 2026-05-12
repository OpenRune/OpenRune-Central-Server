package dev.or2.central.server.session

class WorldServerConnectionState {

    var worldId: Int = -1
    var remoteHost: String? = null

    var handshakeDone: Boolean = false
    var protocolVersion: Int = 0
    var subscribedForServerPush: Boolean = false

    var maxPlayers: Int? = null
    var realmDevMode: Boolean = false

    var loginRestrictionsEnabled: Boolean = false
    var loginMinTotalLevel: Int = 0
    var loginMinRightsToken: String? = null

    var loginGateMinLevelEnabled: Boolean = false
    var loginGateRightsEnabled: Boolean = false
    var loginGateWhitelistEnabled: Boolean = false
}