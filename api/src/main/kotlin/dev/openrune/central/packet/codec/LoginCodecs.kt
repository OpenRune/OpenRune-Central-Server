package dev.openrune.central.packet.codec

import dev.openrune.central.PlayerLoadResponse
import dev.openrune.central.PlayerUID
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LoginDetailsDto
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.packet.model.PlayerDetailsDto


object LoginResponseCodec : PacketCodec<LoginResponseOutgoing> {

    override fun decodeBody(r: PacketReader, requestId: Long): LoginResponseOutgoing {
        val result = PlayerLoadResponse.entries[r.readInt()]
        if (r.remaining() == 0) return LoginResponseOutgoing(result = result, login = null, requestId = requestId)

        val loginUsername = r.readString()
        val createdAt = r.readLong()
        val lastLogin = r.readLong()

        val accountCount = r.readInt()
        val linkedAccounts = List(accountCount) {
            readAccount(r)
        }

        return LoginResponseOutgoing(
            result = result,
            login = LoginDetailsDto(
                loginUsername = loginUsername,
                createdAt = createdAt,
                lastLogin = lastLogin,
                linkedAccounts = linkedAccounts
            ),
            requestId = requestId
        )
    }

    private fun readAccount(r: PacketReader): PlayerDetailsDto {
        val username = r.readString()
        val uid = PlayerUID(r.readLong())

        val xteaCount = r.readInt()
        val previousXteas = List(xteaCount) {
            r.readInt()
        }

        val previousDisplayName = r.readString()
        val dateChanged = r.readLong()
        val registryDate = r.readLong()

        return PlayerDetailsDto(
            username = username,
            uid = uid,
            previousXteas = previousXteas,
            previousDisplayName = previousDisplayName,
            dateChanged = dateChanged,
            registryDate = registryDate
        )
    }


    override fun encodeBody(w: PacketWriter, body: LoginResponseOutgoing) {
        w.writeInt(body.result.ordinal)

        val login = body.login ?: return

        w.writeString(login.loginUsername)
        w.writeLong(login.createdAt)
        w.writeLong(login.lastLogin)

        w.writeInt(login.linkedAccounts.size)
        login.linkedAccounts.forEach { account ->
            w.writeString(account.username)
            w.writeLong(account.uid.value)

            w.writeInt(account.previousXteas.size)
            account.previousXteas.forEach { xtea ->
                w.writeInt(xtea)
            }

            w.writeString(account.previousDisplayName)
            w.writeLong(account.dateChanged)
            w.writeLong(account.registryDate)
        }

    }


}

