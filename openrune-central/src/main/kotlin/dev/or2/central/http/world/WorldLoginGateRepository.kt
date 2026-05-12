package dev.or2.central.http.world

import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

class WorldLoginGateRepository(
    private val dataSource: DataSource,
) {
    fun characterTotalBaseLevel(
        characterId: Int,
        accountId: Long,
    ): Int =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/world/character_total_base_level.sql")
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setLong(2, accountId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        0
                    } else {
                        rs.getInt("total_level")
                    }
                }
            }
        }

    fun isLoginWhitelisted(
        worldId: Int,
        loginUsername: String,
    ): Boolean =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/world/login_whitelist_exists.sql")
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.setString(2, loginUsername.trim())
                ps.executeQuery().use { it.next() }
            }
        }

    fun hasLoginWhitelistEntries(worldId: Int): Boolean =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/world/login_whitelist_nonempty.sql")
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        false
                    } else {
                        rs.getBoolean("has_rows")
                    }
                }
            }
        }
}
