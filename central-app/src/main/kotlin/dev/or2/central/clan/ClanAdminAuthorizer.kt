package dev.or2.central.clan

import dev.or2.central.account.Rights
import javax.sql.DataSource

/** Reads durable account rights directly from Central before a foreign-clan mutation. */
class ClanAdminAuthorizer(private val dataSource: DataSource) {
    fun isJagexModerator(characterId: Int): Boolean {
        if (characterId <= 0) return false
        val sql = """
            SELECT COALESCE(a.rights, '') AS rights
            FROM account_characters c
            JOIN accounts a ON a.id = c.account_id
            WHERE c.id = ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs ->
                    rs.next() && Rights.fromRightsColumn(rs.getString("rights")).isAtLeast(Rights.ADMINISTRATOR)
                }
            }
        }
    }
}