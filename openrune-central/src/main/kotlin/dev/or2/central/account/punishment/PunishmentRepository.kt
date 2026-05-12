package dev.or2.central.account.punishment

import dev.or2.punishment.PunishmentKind
import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

enum class PunishmentLoginBlock {
    LOCKED,
    BANNED,
}

class PunishmentRepository(
    private val dataSource: DataSource,
) {
    fun characterBelongsToAccount(
        characterId: Int,
        accountId: Long,
    ): Boolean {
        val sql = OpenRuneSql.text("game/punishment/verify_character_for_account.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setLong(2, accountId)
                ps.executeQuery().use { it.next() }
            }
        }
    }

    fun blockingLoginSanction(
        accountId: Long,
        loginCharacterId: Int?,
    ): PunishmentLoginBlock? {
        val kinds =
            dataSource.connection.use { conn ->
                val sql =
                    if (loginCharacterId != null && loginCharacterId > 0) {
                        OpenRuneSql.text("game/punishment/select_active_affecting_character.sql")
                    } else {
                        OpenRuneSql.text("game/punishment/select_active_for_account.sql")
                    }
                conn.prepareStatement(sql).use { ps ->
                    if (loginCharacterId != null && loginCharacterId > 0) {
                        ps.setInt(1, loginCharacterId)
                        ps.setInt(2, loginCharacterId)
                    } else {
                        ps.setLong(1, accountId)
                    }
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.getString("kind"))
                            }
                        }
                    }
                }
            }
        return resolveLoginBlock(kinds)
    }

    private fun resolveLoginBlock(kinds: List<String>): PunishmentLoginBlock? {
        if (kinds.isEmpty()) {
            return null
        }
        if (kinds.any { it == PunishmentKind.LOCKED }) {
            return PunishmentLoginBlock.LOCKED
        }
        if (kinds.any { it == PunishmentKind.BAN || it == PunishmentKind.TEMP_BAN }) {
            return PunishmentLoginBlock.BANNED
        }
        return null
    }
}
