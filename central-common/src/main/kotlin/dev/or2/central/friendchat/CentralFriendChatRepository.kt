package dev.or2.central.friendchat

import dev.or2.central.account.Rights
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

data class FriendChatCharacterRow(val characterId: Int, val displayName: String, val rights: Rights)

data class FriendChatChannelRow(
    val id: Long,
    val ownerCharacterId: Int?,
    val ownerNameKey: String,
    val displayName: String,
    val channelType: Int,
    val groupingActivityId: Int?,
    val suggestedWorld: Int?,
    val minJoin: Int,
    val minTalk: Int,
    val minKick: Int,
    val lootShareRank: Int,
    val coinShareEnabled: Boolean,
    val maxMembers: Int,
)

class CentralFriendChatRepository(private val dataSource: DataSource) {
    fun findCharacter(characterId: Int): FriendChatCharacterRow? {
        if (characterId <= 0) return null
        val sql = """
            SELECT c.id, COALESCE(c.display_name, '') AS display_name, COALESCE(a.rights, '') AS rights
            FROM account_characters c
            JOIN accounts a ON a.id = c.account_id
            WHERE c.id = ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toCharacter() else null }
            }
        }
    }

    fun findCharacterByDisplayName(name: String): FriendChatCharacterRow? {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return null
        val sql = """
            SELECT c.id, COALESCE(c.display_name, '') AS display_name, COALESCE(a.rights, '') AS rights
            FROM account_characters c
            JOIN accounts a ON a.id = c.account_id
            WHERE lower(trim(c.display_name)) = lower(trim(?))
            LIMIT 1
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, cleaned)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toCharacter() else null }
            }
        }
    }

    fun loadById(channelId: Long): FriendChatChannelRow? =
        queryOne("SELECT * FROM friend_chat_channels WHERE id = ? LIMIT 1") { ps -> ps.setLong(1, channelId) }

    fun loadByOwnerCharacter(characterId: Int): FriendChatChannelRow? =
        queryOne("SELECT * FROM friend_chat_channels WHERE owner_character_id = ? LIMIT 1") { ps -> ps.setInt(1, characterId) }

    fun loadByOwnerKey(ownerNameKey: String): FriendChatChannelRow? =
        queryOne("SELECT * FROM friend_chat_channels WHERE lower(trim(owner_name_key)) = lower(trim(?)) LIMIT 1") { ps -> ps.setString(1, ownerNameKey) }

    fun loadRestoreTarget(characterId: Int): FriendChatChannelRow? {
        if (characterId <= 0) return null
        val sql =
            """
            SELECT c.*
            FROM friend_chat_restore_targets r
            JOIN friend_chat_channels c ON c.id = r.channel_id
            WHERE r.character_id = ?
            LIMIT 1
            """.trimIndent()
        return queryOne(sql) { ps -> ps.setInt(1, characterId) }
    }

    fun setRestoreTarget(characterId: Int, channelId: Long): Boolean {
        if (characterId <= 0 || channelId <= 0L) return false
        val sql =
            """
            INSERT INTO friend_chat_restore_targets(character_id, channel_id, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (character_id)
            DO UPDATE SET
                channel_id = EXCLUDED.channel_id,
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setLong(2, channelId)
                ps.executeUpdate() == 1
            }
        }
    }

    fun clearRestoreTarget(characterId: Int): Boolean {
        if (characterId <= 0) return false
        return dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM friend_chat_restore_targets WHERE character_id = ?").use { ps ->
                ps.setInt(1, characterId)
                ps.executeUpdate()
                true
            }
        }
    }

    fun ensurePlayerChannel(ownerCharacterId: Int): FriendChatChannelRow? {
        val character = findCharacter(ownerCharacterId) ?: return null
        val key = normaliseOwner(character.displayName)
        loadByOwnerCharacter(ownerCharacterId)?.let { existing ->
            if (existing.ownerNameKey != key && key.isNotBlank()) {
                runCatching { updateOwnerNameKey(ownerCharacterId, character.displayName) }
                return loadByOwnerCharacter(ownerCharacterId) ?: existing
            }
            return existing
        }
        if (key.isBlank()) return null
        val display = cleanChannelName(character.displayName) ?: return null
        return transaction { conn ->
            conn.prepareStatement(
                """
                INSERT INTO friend_chat_channels
                    (owner_character_id, owner_name_key, display_name, channel_type,
                     min_join, min_talk, min_kick, loot_share_rank, coin_share_enabled, max_members)
                VALUES (?, ?, ?, 0, -1, -1, 7, 0, FALSE, 100)
                RETURNING *
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setString(2, key)
                ps.setString(3, display)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toChannel() else null }
            }
        }
    }

    fun resolveByOwnerName(rawOwnerName: String): FriendChatChannelRow? {
        val key = normaliseOwner(rawOwnerName)
        if (key.isBlank()) return null
        loadByOwnerKey(key)?.let { return it }
        val owner = findCharacterByDisplayName(rawOwnerName) ?: return null
        return ensurePlayerChannel(owner.characterId)
    }

    fun isFriend(ownerCharacterId: Int, friendCharacterId: Int): Boolean {
        val sql = "SELECT 1 FROM character_friends WHERE owner_character_id = ? AND friend_character_id = ? LIMIT 1"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setInt(2, friendCharacterId)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    /** Owner's existing social Ignore List is authoritative for player-owned chat channels. */
    fun isIgnored(ownerCharacterId: Int, joiningCharacterId: Int): Boolean {
        if (ownerCharacterId <= 0 || joiningCharacterId <= 0) return false
        val sql = "SELECT 1 FROM character_ignores WHERE owner_character_id = ? AND ignored_character_id = ? LIMIT 1"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setInt(2, joiningCharacterId)
                ps.executeQuery().use { it.next() }
            }
        }
    }

    fun explicitRank(channelId: Long, characterId: Int): Int? {
        val sql = "SELECT rank FROM friend_chat_ranks WHERE channel_id = ? AND character_id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, channelId)
                ps.setInt(2, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
            }
        }
    }

    /** Atomic field-only update; never writes stale values of the other five settings. */
    fun updateSettingField(channelId: Long, field: Int, value: String): Boolean {
        val column = when (field) {
            1 -> "display_name"
            2 -> "min_join"
            3 -> "min_talk"
            4 -> "min_kick"
            5 -> "loot_share_rank"
            6 -> "coin_share_enabled"
            else -> throw IllegalArgumentException("Unknown Friends Chat field: $field")
        }
        val typed: Any = when (field) {
            1 -> value
            6 -> when (value) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Invalid boolean Friends Chat setting")
            }
            else -> value.toInt()
        }
        val sql = "UPDATE friend_chat_channels SET $column = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND channel_type = 0 AND $column IS DISTINCT FROM ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, typed)
                ps.setLong(2, channelId)
                ps.setObject(3, typed)
                ps.executeUpdate() == 1
            }
        }
    }

    fun updateSettings(
        channelId: Long,
        displayName: String,
        minJoin: Int,
        minTalk: Int,
        minKick: Int,
        lootShareRank: Int,
        coinShareEnabled: Boolean,
    ): Boolean {
        val sql = """
            UPDATE friend_chat_channels
            SET display_name=?, min_join=?, min_talk=?, min_kick=?, loot_share_rank=?,
                coin_share_enabled=?, updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND channel_type=0
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, displayName)
                ps.setInt(2, minJoin)
                ps.setInt(3, minTalk)
                ps.setInt(4, minKick)
                ps.setInt(5, lootShareRank)
                ps.setBoolean(6, coinShareEnabled)
                ps.setLong(7, channelId)
                ps.executeUpdate() == 1
            }
        }
    }

    fun setRank(channelId: Long, targetCharacterId: Int, rank: Int, actorCharacterId: Int): Boolean {
        return transaction { conn ->
            if (rank < 0) {
                conn.prepareStatement("DELETE FROM friend_chat_ranks WHERE channel_id=? AND character_id=?").use { ps ->
                    ps.setLong(1, channelId)
                    ps.setInt(2, targetCharacterId)
                    ps.executeUpdate()
                }
                true
            } else {
                conn.prepareStatement(
                    """
                    INSERT INTO friend_chat_ranks(channel_id, character_id, rank, assigned_by_character_id, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (channel_id, character_id)
                    DO UPDATE SET rank=EXCLUDED.rank, assigned_by_character_id=EXCLUDED.assigned_by_character_id,
                                  updated_at=CURRENT_TIMESTAMP
                    """.trimIndent(),
                ).use { ps ->
                    ps.setLong(1, channelId)
                    ps.setInt(2, targetCharacterId)
                    ps.setInt(3, rank)
                    ps.setInt(4, actorCharacterId)
                    ps.executeUpdate()
                }
                true
            }
        }
    }

    fun updateOwnerNameKey(characterId: Int, displayName: String): Boolean {
        val key = normaliseOwner(displayName)
        if (key.isBlank()) return false
        val sql = "UPDATE friend_chat_channels SET owner_name_key=?, updated_at=CURRENT_TIMESTAMP WHERE owner_character_id=?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, key)
                ps.setInt(2, characterId)
                ps.executeUpdate() > 0
            }
        }
    }

    private fun queryOne(sql: String, bind: (java.sql.PreparedStatement) -> Unit): FriendChatChannelRow? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toChannel() else null }
            }
        }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { conn ->
        val old = conn.autoCommit
        conn.autoCommit = false
        try {
            val result = block(conn)
            conn.commit()
            result
        } catch (t: Throwable) {
            conn.rollback()
            throw t
        } finally {
            conn.autoCommit = old
        }
    }

    private fun ResultSet.toCharacter() = FriendChatCharacterRow(
        characterId = getInt("id"),
        displayName = getString("display_name").orEmpty().trim(),
        rights = Rights.fromRightsColumn(getString("rights")),
    )

    private fun ResultSet.toChannel() = FriendChatChannelRow(
        id = getLong("id"),
        ownerCharacterId = getInt("owner_character_id").let { if (wasNull()) null else it },
        ownerNameKey = getString("owner_name_key").orEmpty(),
        displayName = getString("display_name").orEmpty(),
        channelType = getInt("channel_type"),
        groupingActivityId = getInt("grouping_activity_id").let { if (wasNull()) null else it },
        suggestedWorld = getInt("suggested_world").let { if (wasNull()) null else it },
        minJoin = getInt("min_join"),
        minTalk = getInt("min_talk"),
        minKick = getInt("min_kick"),
        lootShareRank = getInt("loot_share_rank"),
        coinShareEnabled = getBoolean("coin_share_enabled"),
        maxMembers = getInt("max_members"),
    )

    companion object {
        fun normaliseOwner(raw: String): String = raw.trim().lowercase().replace('\u00A0', ' ')
        fun cleanChannelName(raw: String): String? {
            val filtered = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }.take(12).trim()
            return filtered.ifBlank { null }
        }
    }
}