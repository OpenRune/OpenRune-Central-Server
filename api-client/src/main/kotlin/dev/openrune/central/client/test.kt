package dev.openrune.central.client

import dev.openrune.central.PlayerUID
import dev.openrune.central.packet.model.LoginRequestIncoming
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.packet.model.LogoutIncoming
import dev.openrune.central.packet.model.PlayerSaveLoadRequestIncoming

fun main() {
    val res = CentralServer.openConnection(
        worldId = 1,
        worldPrivateKey = "MFECAQEwBQYDK2VwBCIEIE91XIVFQfAG6LHgwEVqmCK9CMiVQfu6SXQVaeji2ESNgSEAOKR_EZATJoqZBcDbBjh31gMVRoSj5Jq1r6hEcCzjsZg"
    )

    if (res is ConnectionResult.Connected) {
        val conn = res.connection

        conn.packetSender.sendLoginRequest(LoginRequestIncoming(username = "mark", password = "123", xteas = listOf(0, 0, 0, 0))) { loginResponse ->
            val uid = loginResponse.login?.linkedAccounts?.firstOrNull()?.uid?.value ?: 0L
            val username = loginResponse.login?.linkedAccounts?.firstOrNull()?.username ?: ""
            conn.packetSender.sendPlayerSaveLoadRequest(PlayerSaveLoadRequestIncoming(uid,username)) { loginResponse ->
                println(loginResponse.data)
            }

        }
    }
}