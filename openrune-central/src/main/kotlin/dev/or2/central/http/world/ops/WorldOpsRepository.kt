package dev.or2.central.http.world.ops

import dev.or2.sql.OpenRuneSql
import java.sql.Types
import javax.sql.DataSource

class WorldOpsRepository(
    private val dataSource: DataSource,
) {
    fun shouldBlockLoginForScheduledReboot(
        worldId: Int,
        nowEpochMs: Long,
    ): Boolean =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/worldops/select_active_reboots_for_login.sql")
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val rebootAt = rs.getTimestamp("reboot_at").time
                        val createdAt = rs.getTimestamp("created_at").time
                        val noticeSec = ((rebootAt - createdAt) / 1000L).coerceAtLeast(60L)
                        val leadMs = RebootLoginBlockPolicy.leadBlockSeconds(noticeSec) * 1000L
                        val remaining = rebootAt - nowEpochMs
                        if (remaining in 1..leadMs) {
                            return@use true
                        }
                    }
                    false
                }
            }
        }

    fun insertRebootSchedule(
        worldId: Int?,
        rebootAtIsoUtc: String,
        message: String,
        createdBy: String,
    ): Long =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/worldops/insert_reboot_schedule_returning_id.sql")
            conn.prepareStatement(sql).use { ps ->
                if (worldId == null) {
                    ps.setNull(1, Types.INTEGER)
                } else {
                    ps.setInt(1, worldId)
                }
                ps.setString(2, rebootAtIsoUtc)
                ps.setString(3, message.ifBlank { "Scheduled reboot." })
                ps.setString(4, createdBy.ifBlank { "admin-web" })
                ps.executeQuery().use { rs ->
                    require(rs.next())
                    rs.getLong(1)
                }
            }
        }

    fun cancelRebootSchedule(id: Long): Int =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/worldops/cancel_reboot_schedule.sql")
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                ps.executeUpdate()
            }
        }

    fun insertBroadcastLog(
        worldId: Int?,
        message: String,
        url: String,
        icon: String,
        createdBy: String,
    ): Long =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/worldops/insert_broadcast_log_returning_id.sql")
            conn.prepareStatement(sql).use { ps ->
                if (worldId == null) {
                    ps.setNull(1, Types.INTEGER)
                } else {
                    ps.setInt(1, worldId)
                }
                ps.setString(2, message)
                ps.setString(3, url)
                ps.setString(4, icon)
                ps.setString(5, createdBy.ifBlank { "admin-web" })
                ps.executeQuery().use { rs ->
                    require(rs.next())
                    rs.getLong(1)
                }
            }
        }
}
