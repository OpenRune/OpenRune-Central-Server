package dev.or2.central.http.world

import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

class WorldRepository(
    private val dataSource: DataSource,
) {
    fun findAllForList(): List<WorldRow> {
        val sql = OpenRuneSql.text("central/world/find_all_for_list.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                WorldRow(
                                    worldId = rs.getInt("world_id"),
                                    flags = rs.getString("flags") ?: "",
                                    host = rs.getString("host"),
                                    activity = rs.getString("activity"),
                                    location = rs.getInt("location"),
                                    population = rs.getInt("population"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun findForAuth(worldId: Int): WorldAuthRow? {
        val sql = OpenRuneSql.text("central/world/find_for_auth.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    val maxPlayers = rs.getObject("max_players") as Int?
                    val keyBytes = rs.getBytes("world_key_sha256")
                    WorldAuthRow(
                        worldId = rs.getInt("world_id"),
                        enabled = rs.getInt("enabled") != 0,
                        maxPlayers = maxPlayers,
                        worldKeySha256 = keyBytes,
                        loginRestrictionsEnabled = rs.getInt("login_restrictions_enabled") != 0,
                        loginMinTotalLevel = rs.getInt("login_min_total_level"),
                        loginMinRightsToken = rs.getString("login_min_rights_token")?.trim()?.takeIf { it.isNotEmpty() },
                        loginGateMinLevelEnabled = rs.getInt("login_gate_min_level_enabled") != 0,
                        loginGateRightsEnabled = rs.getInt("login_gate_rights_enabled") != 0,
                        loginGateWhitelistEnabled = rs.getInt("login_gate_whitelist_enabled") != 0,
                        realmDevMode = rs.getBoolean("realm_dev_mode"),
                    )
                }
            }
        }
    }

    fun updateWorldKeySha256(
        worldId: Int,
        sha256Digest: ByteArray,
    ): Int {
        val sql = OpenRuneSql.text("central/world/update_world_key_sha256.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setBytes(1, sha256Digest)
                ps.setInt(2, worldId)
                ps.executeUpdate()
            }
        }
    }

    fun exists(worldId: Int): Boolean {
        val sql = OpenRuneSql.text("central/world/exists.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeQuery().use { it.next() }
            }
        }
    }
}
