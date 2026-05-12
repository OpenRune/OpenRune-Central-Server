package dev.or2.central.worldserver.logging

import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

class LoginEventRepository(
    private val dataSource: DataSource,
) {
    fun recordFirstLoginOfDay(accountId: Long, worldId: Int, dayUtc: String, nowMillis: Long) {
        val sql = OpenRuneSql.text("central/analytics/login_event_insert.sql")
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, accountId)
                ps.setInt(2, worldId)
                ps.setString(3, dayUtc)
                ps.setLong(4, nowMillis)
                ps.executeUpdate()
            }
        }
    }

    fun countDistinctAccountsForDay(dayUtc: String): Int {
        val sql = OpenRuneSql.text("central/analytics/login_event_count_day.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, dayUtc)
                ps.executeQuery().use { rs ->
                    require(rs.next())
                    rs.getInt(1)
                }
            }
        }
    }
}