package dev.or2.central.clan

import dev.or2.sql.OpenRuneSql
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class ClanCharacterRow(
    val characterId: Int,
    val accountId: Long,
    val displayName: String,
)

data class ClanRow(
    val clanId: Long,
    val name: String,
    val ownerCharacterId: Int,
    val ownerDisplayName: String,
    val creationTimeMinutes: Int,
    val renamedTimeMinutes: Int,
    val allowUnaffined: Boolean,
    val talkRank: Int,
    val kickRank: Int,
    val homeWorld: Int,
    val interestsMask: Int,
    val motifTopIcon: Int,
    val motifBottomIcon: Int,
    val motifColour: Int,
    val recruitMinCombat: Int,
    val recruitMinSkillTotal: Int,
    val recruitMinQuestPoints: Int,
    val recruitIronStatus: Int,
    val hallHomeWorldOnly: Boolean,
    val hallLocked: Boolean,
    val hallPlayerCount: Int,
    val boardListed: Boolean,
    val acceptingApplications: Boolean,
    val pendingAbdicationRequesterCharacterId: Int?,
    val pendingAbdicationRequestedAt: Long?,
    val pendingAbdicationDueAt: Long?,
)

data class ClanMemberRow(
    val clanId: Long,
    val characterId: Int,
    val displayName: String,
    val rank: Int,
    val joinRuneDay: Int,
    val muted: Boolean,
    val extraInfo: Int,
    val lastSeenRuneDay: Int,
    val combatLevel: Int,
    val skillTotal: Int,
)

data class ClanBanRow(
    val id: Long,
    val clanId: Long,
    val characterId: Int?,
    val displayName: String,
    val bannedByCharacterId: Int?,
    val createdAtMillis: Long,
)

data class ClanApplicationRow(
    val id: Long,
    val clanId: Long,
    val applicantCharacterId: Int,
    val applicantDisplayName: String,
    val appliedAtMillis: Long,
    val combatLevel: Int,
    val skillTotal: Int,
    val questPoints: Int,
    val ironStatus: Int,
    val skillLevels: List<Int>,
)

data class ClanInviteRow(
    val id: Long,
    val clanId: Long,
    val clanName: String,
    val inviterCharacterId: Int,
    val inviterDisplayName: String,
    val expiresAtMillis: Long,
)

data class ClanApplicationVerdictRow(
    val id: Long,
    val clanId: Long,
    val clanName: String,
    val status: Int,
    val reviewedAtMillis: Long,
)

data class ClanEventRow(
    val id: Long,
    val clanId: Long,
    val position: Int,
    val date: Int,
    val time: Int,
    val durationDays: Int,
    val world: Int,
    val type: Int,
    val subtype: Int,
    val activity: Int,
    val rankPlusOne: Int,
    val creatorCharacterId: Int?,
    val creatorDisplayName: String,
)

data class ClanStateRow(
    val clan: ClanRow,
    val members: List<ClanMemberRow> = emptyList(),
    val bans: List<ClanBanRow> = emptyList(),
    val rankTitles: Map<Int, Int> = emptyMap(),
    val permissions: Map<Int, Int> = emptyMap(),
    val broadcastSettings: Map<Int, Int> = emptyMap(),
    val settings: Map<Int, Int> = emptyMap(),
    val applications: List<ClanApplicationRow> = emptyList(),
    val events: List<ClanEventRow> = emptyList(),
)

/**
 * PostgreSQL boundary for permanent clan state.
 *
 * The repository deliberately contains no world/interface logic. Mutating methods commit first;
 * callers can safely refresh [ClanStateRow] afterwards without exposing uncommitted state.
 */
class CentralClanRepository(
    private val dataSource: DataSource,
) {
    fun loadAll(): List<ClanStateRow> {
        val clans = loadAllClans()
        if (clans.isEmpty()) return emptyList()
        val members = loadMembers().groupBy(ClanMemberRow::clanId)
        val bans = loadBans().groupBy(ClanBanRow::clanId)
        val titles = loadIntPairs("central/clan/load_all_rank_titles.sql", "rank", "title_id")
        val permissions = loadIntPairs("central/clan/load_all_permissions.sql", "setting_id", "minimum_rank")
        val broadcasts = loadIntPairs("central/clan/load_all_broadcast_settings.sql", "setting_id", "value")
        val settings = loadIntPairs("central/clan/load_all_settings.sql", "setting_id", "value")
        val applications = loadPendingApplications().groupBy(ClanApplicationRow::clanId)
        val events = loadEvents().groupBy(ClanEventRow::clanId)
        return clans.map { clan ->
            ClanStateRow(
                clan = clan,
                members = members[clan.clanId].orEmpty(),
                bans = bans[clan.clanId].orEmpty(),
                rankTitles = titles[clan.clanId].orEmpty(),
                permissions = permissions[clan.clanId].orEmpty(),
                broadcastSettings = broadcasts[clan.clanId].orEmpty(),
                settings = settings[clan.clanId].orEmpty(),
                applications = applications[clan.clanId].orEmpty(),
                events = events[clan.clanId].orEmpty(),
            )
        }
    }

    fun loadClan(clanId: Long): ClanStateRow? {
        val clan = queryByClan(OpenRuneSql.text("central/clan/load_clan.sql"), clanId) { it.toClan() }.firstOrNull() ?: return null
        return ClanStateRow(
            clan = clan,
            members = queryByClan(OpenRuneSql.text("central/clan/load_members_for_clan.sql"), clanId) { it.toMember() },
            bans = queryByClan(OpenRuneSql.text("central/clan/load_bans_for_clan.sql"), clanId) { it.toBan() },
            rankTitles = loadIntPairsForClan("central/clan/load_rank_titles_for_clan.sql", clanId, "rank", "title_id"),
            permissions = loadIntPairsForClan("central/clan/load_permissions_for_clan.sql", clanId, "setting_id", "minimum_rank"),
            broadcastSettings = loadIntPairsForClan("central/clan/load_broadcast_settings_for_clan.sql", clanId, "setting_id", "value"),
            settings = loadIntPairsForClan("central/clan/load_settings_for_clan.sql", clanId, "setting_id", "value"),
            applications = queryByClan(OpenRuneSql.text("central/clan/load_pending_applications_for_clan.sql"), clanId) { it.toApplication() },
            events = queryByClan(OpenRuneSql.text("central/clan/load_events_for_clan.sql"), clanId) { it.toEvent() },
        )
    }

    fun clanIdForCharacter(characterId: Int): Long? {
        if (characterId <= 0) return null
        val sql = "SELECT clan_id FROM clan_members WHERE character_id = ? LIMIT 1"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
            }
        }
    }

    fun guestClanIdForCharacter(characterId: Int): Long? {
        if (characterId <= 0) return null
        return dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT clan_id FROM clan_guest_selections WHERE character_id = ? LIMIT 1").use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
            }
        }
    }

    fun setGuestClanSelection(characterId: Int, clanId: Long): Boolean =
        update(
            """
            INSERT INTO clan_guest_selections (character_id, clan_id)
            VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE
            SET clan_id = EXCLUDED.clan_id, updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
        ) { ps ->
            ps.setInt(1, characterId)
            ps.setLong(2, clanId)
        } > 0

    fun clearGuestClanSelection(characterId: Int, clanId: Long? = null): Boolean {
        val sql =
            if (clanId == null) {
                "DELETE FROM clan_guest_selections WHERE character_id = ?"
            } else {
                "DELETE FROM clan_guest_selections WHERE character_id = ? AND clan_id = ?"
            }
        return update(sql) { ps ->
            ps.setInt(1, characterId)
            if (clanId != null) ps.setLong(2, clanId)
        } > 0
    }

    fun guestCharactersForClan(clanId: Long): List<Int> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT character_id FROM clan_guest_selections WHERE clan_id = ?").use { ps ->
                ps.setLong(1, clanId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getInt(1)) } }
            }
        }

    fun findCharacter(characterId: Int): ClanCharacterRow? {
        if (characterId <= 0) return null
        val sql = "SELECT id, account_id, COALESCE(display_name, '') AS display_name FROM account_characters WHERE id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toCharacter() else null }
            }
        }
    }

    fun findCharacterByDisplayName(name: String): ClanCharacterRow? {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return null
        val sql =
            """
            SELECT id, account_id, COALESCE(display_name, '') AS display_name
            FROM account_characters
            WHERE lower(trim(display_name)) = lower(trim(?))
            LIMIT 1
            """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, cleaned)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toCharacter() else null }
            }
        }
    }

    fun findClanIdByName(name: String): Long? {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return null
        return dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT id FROM clans WHERE lower(trim(name)) = lower(trim(?)) LIMIT 1").use { ps ->
                ps.setString(1, cleaned)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
            }
        }
    }

    fun createClan(
        name: String,
        ownerCharacterId: Int,
        foundingCharacterIds: List<Int>,
        creationTimeMinutes: Int,
        homeWorld: Int,
        defaultRankTitles: Map<Int, Int>,
        administrativePermissionIds: Collection<Int>,
        broadcastPermissionIds: Collection<Int>,
        ownerRank: Int,
        nobodyRank: Int,
        currentRuneDay: Int,
    ): Long = transaction { conn ->
        val clanId =
            conn.prepareStatement(
                """
        INSERT INTO clans (
            name,
            owner_character_id,
            creation_time_minutes,
            home_world,
            motif_top_icon,
            motif_bottom_icon,
            motif_colour
        )
        VALUES (?, ?, ?, ?, 16, 16, 3)
        RETURNING id
        """.trimIndent(),
            ).use { ps ->
                ps.setString(1, name)
                ps.setInt(2, ownerCharacterId)
                ps.setInt(3, creationTimeMinutes)
                ps.setInt(4, homeWorld)

                ps.executeQuery().use { rs ->
                    check(rs.next()) { "Clan insert did not return an id" }
                    rs.getLong(1)
                }
            }

        val uniqueFounders = linkedSetOf(ownerCharacterId).apply { addAll(foundingCharacterIds.filter { it > 0 }) }
        conn.prepareStatement(
            """
            INSERT INTO clan_members (clan_id, character_id, rank, join_rune_day, last_seen_rune_day)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            for (characterId in uniqueFounders) {
                ps.setLong(1, clanId)
                ps.setInt(2, characterId)
                ps.setInt(3, if (characterId == ownerCharacterId) ownerRank else 0)
                ps.setInt(4, currentRuneDay)
                ps.setInt(5, currentRuneDay)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        conn.prepareStatement(
            "INSERT INTO clan_creation_participants (clan_id, character_id, founder) VALUES (?, ?, ?)",
        ).use { ps ->
            for (characterId in uniqueFounders) {
                ps.setLong(1, clanId)
                ps.setInt(2, characterId)
                ps.setBoolean(3, characterId == ownerCharacterId)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        replaceIntMap(conn, "clan_rank_titles", clanId, "rank", "title_id", defaultRankTitles)
        administrativePermissionIds.forEach { upsertIntPair(conn, "clan_permissions", clanId, "setting_id", "minimum_rank", it, ownerRank) }
        broadcastPermissionIds.forEach { upsertIntPair(conn, "clan_permissions", clanId, "setting_id", "minimum_rank", it, nobodyRank) }
        clanId
    }

    fun addMember(
        clanId: Long,
        characterId: Int,
        rank: Int,
        runeDay: Int,
        maxMembers: Int,
    ): Boolean = transaction { conn ->
        if (!lockClan(conn, clanId)) return@transaction false
        if (memberCount(conn, clanId) >= maxMembers) return@transaction false
        conn.prepareStatement(
            """
            INSERT INTO clan_members (clan_id, character_id, rank, join_rune_day, last_seen_rune_day)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.setInt(2, characterId)
            ps.setInt(3, rank)
            ps.setInt(4, runeDay)
            ps.setInt(5, runeDay)
            ps.executeUpdate() > 0
        }
    }

    fun removeMember(clanId: Long, characterId: Int): Boolean =
        update("DELETE FROM clan_members WHERE clan_id = ? AND character_id = ?") { ps ->
            ps.setLong(1, clanId); ps.setInt(2, characterId)
        } > 0

    fun updateMemberRank(clanId: Long, characterId: Int, rank: Int): Boolean =
        update("UPDATE clan_members SET rank = ? WHERE clan_id = ? AND character_id = ?") { ps ->
            ps.setInt(1, rank); ps.setLong(2, clanId); ps.setInt(3, characterId)
        } > 0

    fun updateMemberMuted(clanId: Long, characterId: Int, muted: Boolean): Boolean =
        update("UPDATE clan_members SET muted = ? WHERE clan_id = ? AND character_id = ?") { ps ->
            ps.setBoolean(1, muted); ps.setLong(2, clanId); ps.setInt(3, characterId)
        } > 0

    fun touchMemberLastSeen(characterId: Int, runeDay: Int): Long? = transaction { conn ->
        val clanId =
            conn.prepareStatement("SELECT clan_id FROM clan_members WHERE character_id = ? FOR UPDATE").use { ps ->
                ps.setInt(1, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else return@transaction null }
            }
        conn.prepareStatement("UPDATE clan_members SET last_seen_rune_day = ? WHERE character_id = ?").use { ps ->
            ps.setInt(1, runeDay); ps.setInt(2, characterId); ps.executeUpdate()
        }
        clanId
    }

    fun updateMemberStats(
        characterId: Int,
        combatLevel: Int,
        skillTotal: Int,
    ): Long? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE clan_members
                SET combat_level = ?, skill_total = ?
                WHERE character_id = ?
                RETURNING clan_id
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, combatLevel)
                ps.setInt(2, skillTotal)
                ps.setInt(3, characterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
            }
        }

    fun addBan(
        clanId: Long,
        targetCharacterId: Int?,
        displayName: String,
        actorCharacterId: Int,
        maxBans: Int,
    ): Long? = transaction { conn ->
        if (!lockClan(conn, clanId)) return@transaction null
        val count = conn.prepareStatement("SELECT count(*) FROM clan_bans WHERE clan_id = ?").use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        if (count >= maxBans) return@transaction null
        conn.prepareStatement(
            """
            INSERT INTO clan_bans (clan_id, character_id, display_name, banned_by_character_id)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId)
            if (targetCharacterId == null) ps.setNull(2, java.sql.Types.INTEGER) else ps.setInt(2, targetCharacterId)
            ps.setString(3, displayName)
            ps.setInt(4, actorCharacterId)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
    }

    fun removeBan(clanId: Long, banId: Long): Boolean =
        update("DELETE FROM clan_bans WHERE clan_id = ? AND id = ?") { ps ->
            ps.setLong(1, clanId); ps.setLong(2, banId)
        } > 0

    fun updateClanName(clanId: Long, name: String, renamedTimeMinutes: Int): Boolean =
        update("UPDATE clans SET name = ?, renamed_time_minutes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?") { ps ->
            ps.setString(1, name); ps.setInt(2, renamedTimeMinutes); ps.setLong(3, clanId)
        } > 0

    fun updateProfileInt(clanId: Long, column: ClanIntField, value: Int): Boolean =
        update("UPDATE clans SET ${column.column} = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?") { ps ->
            ps.setInt(1, value); ps.setLong(2, clanId)
        } > 0

    fun updateProfileBoolean(clanId: Long, column: ClanBooleanField, value: Boolean): Boolean =
        update("UPDATE clans SET ${column.column} = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?") { ps ->
            ps.setBoolean(1, value); ps.setLong(2, clanId)
        } > 0

    fun updateMotif(clanId: Long, topIcon: Int, bottomIcon: Int, colour: Int): Boolean =
        update(
            "UPDATE clans SET motif_top_icon=?, motif_bottom_icon=?, motif_colour=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
        ) { ps ->
            ps.setInt(1, topIcon); ps.setInt(2, bottomIcon); ps.setInt(3, colour); ps.setLong(4, clanId)
        } > 0

    fun updateRecruitRequirements(
        clanId: Long,
        minimumCombatLevel: Int,
        minimumSkillTotal: Int,
        minimumQuestPoints: Int,
        ironStatus: Int,
    ): Boolean =
        update(
            "UPDATE clans SET recruit_min_combat=?, recruit_min_skill_total=?, recruit_min_quest_points=?, recruit_iron_status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
        ) { ps ->
            ps.setInt(1, minimumCombatLevel); ps.setInt(2, minimumSkillTotal); ps.setInt(3, minimumQuestPoints); ps.setInt(4, ironStatus); ps.setLong(5, clanId)
        } > 0

    fun updateHallSettings(clanId: Long, homeWorldOnly: Boolean, locked: Boolean, playerCount: Int): Boolean =
        update(
            "UPDATE clans SET hall_home_world_only=?, hall_locked=?, hall_player_count=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
        ) { ps ->
            ps.setBoolean(1, homeWorldOnly); ps.setBoolean(2, locked); ps.setInt(3, playerCount); ps.setLong(4, clanId)
        } > 0

    fun setPermission(clanId: Long, settingId: Int, minimumRank: Int) =
        upsertPair("clan_permissions", clanId, "setting_id", "minimum_rank", settingId, minimumRank)

    fun setBroadcastSetting(clanId: Long, settingId: Int, value: Int) =
        upsertPair("clan_broadcast_settings", clanId, "setting_id", "value", settingId, value)

    fun setSetting(clanId: Long, settingId: Int, value: Int) =
        upsertPair("clan_settings", clanId, "setting_id", "value", settingId, value)

    fun replaceRankTitles(clanId: Long, values: Map<Int, Int>) = transaction<Unit> { conn ->
        replaceIntMap(conn, "clan_rank_titles", clanId, "rank", "title_id", values)
    }

    fun setBoardState(clanId: Long, listed: Boolean, acceptingApplications: Boolean): Boolean =
        update(
            "UPDATE clans SET board_listed = ?, accepting_applications = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        ) { ps ->
            ps.setBoolean(1, listed); ps.setBoolean(2, acceptingApplications); ps.setLong(3, clanId)
        } > 0

    fun submitApplication(
        clanId: Long,
        clanName: String,
        applicantCharacterId: Int,
        combatLevel: Int,
        skillTotal: Int,
        questPoints: Int,
        ironStatus: Int,
        skillLevels: List<Int>,
    ): Long = transaction { conn ->
        conn.prepareStatement(
            """
            INSERT INTO clan_applications (
                clan_id, applicant_character_id, clan_name, applicant_combat_level, applicant_skill_total,
                applicant_quest_points, applicant_iron_status, applicant_skill_levels
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId); ps.setInt(2, applicantCharacterId); ps.setString(3, clanName)
            ps.setInt(4, combatLevel); ps.setInt(5, skillTotal); ps.setInt(6, questPoints); ps.setInt(7, ironStatus)
            ps.setArray(8, conn.createArrayOf("SMALLINT", skillLevels.map(Int::toShort).toTypedArray()))
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
    }

    fun reviewApplication(
        clanId: Long,
        applicationId: Long,
        reviewerCharacterId: Int,
        status: Int,
    ): Int? = transaction { conn ->
        conn.prepareStatement(
            """
            UPDATE clan_applications
            SET status = ?, reviewer_character_id = ?, reviewed_at = CURRENT_TIMESTAMP, notified_at = NULL
            WHERE clan_id = ? AND id = ? AND status = 0
            RETURNING applicant_character_id
            """.trimIndent(),
        ).use { ps ->
            ps.setInt(1, status); ps.setInt(2, reviewerCharacterId); ps.setLong(3, clanId); ps.setLong(4, applicationId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }
    }

    /** Status 3 marks a withdrawn application; it is never delivered as a verdict. */
    fun withdrawApplication(clanId: Long, applicationId: Long, applicantCharacterId: Int): Boolean =
        update(
            """
            UPDATE clan_applications
            SET status = 3, reviewed_at = CURRENT_TIMESTAMP, notified_at = CURRENT_TIMESTAMP
            WHERE clan_id = ? AND id = ? AND applicant_character_id = ? AND status = 0
            """.trimIndent(),
        ) { ps ->
            ps.setLong(1, clanId); ps.setLong(2, applicationId); ps.setInt(3, applicantCharacterId)
        } > 0

    fun applicationCountSince(applicantCharacterId: Int, since: Instant): Int {
        val sql = "SELECT count(*) FROM clan_applications WHERE applicant_character_id = ? AND applied_at >= ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, applicantCharacterId); ps.setTimestamp(2, Timestamp.from(since))
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }
    }

    fun creationParticipationCountSince(characterId: Int, since: Instant): Int {
        val sql = "SELECT count(*) FROM clan_creation_participants WHERE character_id = ? AND created_at >= ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId); ps.setTimestamp(2, Timestamp.from(since))
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }
    }


    fun acceptApplicationAsMember(
        clanId: Long,
        applicationId: Long,
        reviewerCharacterId: Int,
        rank: Int,
        runeDay: Int,
        maxMembers: Int,
    ): Int? = transaction { conn ->
        if (!lockClan(conn, clanId)) return@transaction null
        if (memberCount(conn, clanId) >= maxMembers) return@transaction null
        val applicantId =
            conn.prepareStatement(
                "SELECT applicant_character_id FROM clan_applications WHERE clan_id=? AND id=? AND status=0 FOR UPDATE",
            ).use { ps ->
                ps.setLong(1, clanId); ps.setLong(2, applicationId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else return@transaction null }
            }
        conn.prepareStatement(
            "INSERT INTO clan_members (clan_id, character_id, rank, join_rune_day, last_seen_rune_day) VALUES (?, ?, ?, ?, ?)",
        ).use { ps ->
            ps.setLong(1, clanId); ps.setInt(2, applicantId); ps.setInt(3, rank); ps.setInt(4, runeDay); ps.setInt(5, runeDay)
            ps.executeUpdate()
        }
        conn.prepareStatement(
            "UPDATE clan_applications SET status=1, reviewer_character_id=?, reviewed_at=CURRENT_TIMESTAMP, notified_at=NULL WHERE id=?",
        ).use { ps -> ps.setInt(1, reviewerCharacterId); ps.setLong(2, applicationId); ps.executeUpdate() }
        applicantId
    }

    fun acceptInviteAsMember(
        inviteId: Long,
        inviteeCharacterId: Int,
        rank: Int,
        runeDay: Int,
        maxMembers: Int,
    ): Long? = transaction { conn ->
        val clanId =
            conn.prepareStatement(
                """
                SELECT clan_id FROM clan_invites
                WHERE id=? AND invitee_character_id=? AND consumed_at IS NULL
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                FOR UPDATE
                """.trimIndent(),
            ).use { ps ->
                ps.setLong(1, inviteId); ps.setInt(2, inviteeCharacterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else return@transaction null }
            }
        if (!lockClan(conn, clanId)) return@transaction null
        if (memberCount(conn, clanId) >= maxMembers) return@transaction null
        conn.prepareStatement(
            "INSERT INTO clan_members (clan_id, character_id, rank, join_rune_day, last_seen_rune_day) VALUES (?, ?, ?, ?, ?)",
        ).use { ps ->
            ps.setLong(1, clanId); ps.setInt(2, inviteeCharacterId); ps.setInt(3, rank); ps.setInt(4, runeDay); ps.setInt(5, runeDay)
            ps.executeUpdate()
        }
        conn.prepareStatement("UPDATE clan_invites SET consumed_at=CURRENT_TIMESTAMP WHERE id=?").use { ps ->
            ps.setLong(1, inviteId); ps.executeUpdate()
        }
        clanId
    }

    fun pendingInvite(inviteId: Long, inviteeCharacterId: Int): ClanInviteRow? {
        val sql =
            """
            SELECT i.id, i.clan_id, c.name AS clan_name, i.inviter_character_id,
                   COALESCE(inviter.display_name, '') AS inviter_display_name,
                   COALESCE((EXTRACT(EPOCH FROM i.expires_at) * 1000)::BIGINT, 0) AS expires_at_millis
            FROM clan_invites i
            JOIN clans c ON c.id = i.clan_id
            JOIN account_characters inviter ON inviter.id = i.inviter_character_id
            WHERE i.id = ? AND i.invitee_character_id = ? AND i.consumed_at IS NULL
              AND (i.expires_at IS NULL OR i.expires_at > CURRENT_TIMESTAMP)
            LIMIT 1
            """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, inviteId)
                ps.setInt(2, inviteeCharacterId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        ClanInviteRow(
                            id = rs.getLong("id"),
                            clanId = rs.getLong("clan_id"),
                            clanName = rs.getString("clan_name"),
                            inviterCharacterId = rs.getInt("inviter_character_id"),
                            inviterDisplayName = rs.getString("inviter_display_name"),
                            expiresAtMillis = rs.getLong("expires_at_millis"),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun pendingInvitesForCharacter(characterId: Int): List<ClanInviteRow> =
        queryByCharacter(OpenRuneSql.text("central/clan/load_pending_invites_for_character.sql"), characterId) { rs ->
            ClanInviteRow(
                id = rs.getLong("id"),
                clanId = rs.getLong("clan_id"),
                clanName = rs.getString("clan_name"),
                inviterCharacterId = rs.getInt("inviter_character_id"),
                inviterDisplayName = rs.getString("inviter_display_name"),
                expiresAtMillis = rs.getLong("expires_at_millis"),
            )
        }

    fun pendingApplicationVerdicts(characterId: Int): List<ClanApplicationVerdictRow> =
        queryByCharacter(OpenRuneSql.text("central/clan/load_pending_application_verdicts.sql"), characterId) { rs ->
            ClanApplicationVerdictRow(
                id = rs.getLong("id"),
                clanId = rs.getLong("clan_id"),
                clanName = rs.getString("clan_name"),
                status = rs.getInt("status"),
                reviewedAtMillis = rs.getLong("reviewed_at_millis"),
            )
        }

    /**
     * Idempotently acknowledges verdicts only for the intended applicant.
     * A repeated ACK is safe and leaves already-notified rows unchanged.
     */
    fun acknowledgeApplicationVerdicts(applicantCharacterId: Int, ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { conn ->
            var updated = 0
            conn.prepareStatement(
                """
                UPDATE clan_applications
                SET notified_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND applicant_character_id = ?
                  AND status IN (1, 2, 4)
                  AND notified_at IS NULL
                """.trimIndent(),
            ).use { ps ->
                ids.distinct().forEach { id ->
                    ps.setLong(1, id)
                    ps.setInt(2, applicantCharacterId)
                    updated += ps.executeUpdate()
                }
            }
            updated
        }
    }

    fun createInvite(
        clanId: Long,
        inviteeCharacterId: Int,
        inviterCharacterId: Int,
        expiresAt: Instant?,
    ): Long = transaction { conn ->
        conn.prepareStatement(
            """
            INSERT INTO clan_invites (clan_id, invitee_character_id, inviter_character_id, expires_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (clan_id, invitee_character_id) WHERE consumed_at IS NULL
            DO UPDATE SET inviter_character_id = EXCLUDED.inviter_character_id,
                          created_at = CURRENT_TIMESTAMP,
                          expires_at = EXCLUDED.expires_at
            RETURNING id
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId); ps.setInt(2, inviteeCharacterId); ps.setInt(3, inviterCharacterId)
            if (expiresAt == null) ps.setNull(4, java.sql.Types.TIMESTAMP_WITH_TIMEZONE) else ps.setTimestamp(4, Timestamp.from(expiresAt))
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
    }

    fun consumeInvite(inviteId: Long, inviteeCharacterId: Int): Long? = transaction { conn ->
        conn.prepareStatement(
            """
            UPDATE clan_invites
            SET consumed_at = CURRENT_TIMESTAMP
            WHERE id = ? AND invitee_character_id = ? AND consumed_at IS NULL
              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            RETURNING clan_id
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, inviteId); ps.setInt(2, inviteeCharacterId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }
    }

    fun upsertEvent(clanId: Long, event: ClanEventRow): Long = transaction { conn ->
        if (event.id > 0) {
            conn.prepareStatement(
                """
                UPDATE clan_events SET position=?, event_date=?, event_time=?, duration_days=?, world=?, type=?, subtype=?,
                    activity=?, rank_plus_one=?, creator_character_id=?, creator_display_name=?, updated_at=CURRENT_TIMESTAMP
                WHERE clan_id=? AND id=? RETURNING id
                """.trimIndent(),
            ).use { ps ->
                bindEvent(ps, event, includeClanAndId = true, clanId = clanId)
                ps.executeQuery().use { rs -> if (rs.next()) return@transaction rs.getLong(1) }
            }
        }
        conn.prepareStatement(
            """
            INSERT INTO clan_events (clan_id, position, event_date, event_time, duration_days, world, type, subtype,
                activity, rank_plus_one, creator_character_id, creator_display_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """.trimIndent(),
        ).use { ps ->
            ps.setLong(1, clanId)
            ps.setInt(2, event.position); ps.setInt(3, event.date); ps.setInt(4, event.time); ps.setInt(5, event.durationDays)
            ps.setInt(6, event.world); ps.setInt(7, event.type); ps.setInt(8, event.subtype); ps.setInt(9, event.activity)
            ps.setInt(10, event.rankPlusOne)
            if (event.creatorCharacterId == null) ps.setNull(11, java.sql.Types.INTEGER) else ps.setInt(11, event.creatorCharacterId)
            ps.setString(12, event.creatorDisplayName)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
        }
    }

    fun deleteEvent(clanId: Long, eventId: Long): Boolean =
        update("DELETE FROM clan_events WHERE clan_id = ? AND id = ?") { ps -> ps.setLong(1, clanId); ps.setLong(2, eventId) } > 0

    fun beginAbdication(clanId: Long, requesterCharacterId: Int, requestedAt: Long, dueAt: Long): Boolean =
        update(
            """
            UPDATE clans SET pending_abdication_requester_character_id=?, pending_abdication_requested_at=?,
                pending_abdication_due_at=?, updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND pending_abdication_due_at IS NULL
            """.trimIndent(),
        ) { ps ->
            ps.setInt(1, requesterCharacterId); ps.setLong(2, requestedAt); ps.setLong(3, dueAt); ps.setLong(4, clanId)
        } > 0

    fun cancelAbdication(clanId: Long): Boolean =
        update(
            """
            UPDATE clans SET pending_abdication_requester_character_id=NULL, pending_abdication_requested_at=NULL,
                pending_abdication_due_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=?
            """.trimIndent(),
        ) { ps -> ps.setLong(1, clanId) } > 0

    fun transferOwnership(clanId: Long, oldOwnerCharacterId: Int, newOwnerCharacterId: Int, deputyRank: Int, ownerRank: Int): Boolean =
        transaction { conn ->
            val changed = conn.prepareStatement("UPDATE clans SET owner_character_id=?, pending_abdication_requester_character_id=NULL, pending_abdication_requested_at=NULL, pending_abdication_due_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_character_id=?").use { ps ->
                ps.setInt(1, newOwnerCharacterId); ps.setLong(2, clanId); ps.setInt(3, oldOwnerCharacterId); ps.executeUpdate()
            }
            if (changed == 0) return@transaction false
            conn.prepareStatement("UPDATE clan_members SET rank=? WHERE clan_id=? AND character_id=?").use { ps ->
                ps.setInt(1, deputyRank); ps.setLong(2, clanId); ps.setInt(3, oldOwnerCharacterId); ps.executeUpdate()
            }
            conn.prepareStatement("UPDATE clan_members SET rank=? WHERE clan_id=? AND character_id=?").use { ps ->
                ps.setInt(1, ownerRank); ps.setLong(2, clanId); ps.setInt(3, newOwnerCharacterId); ps.executeUpdate()
            }
            true
        }

    fun disbandClan(clanId: Long): Boolean = transaction { conn ->
        // Never cascade-delete live economic value or an ambiguous transfer. Historical committed/
        // aborted economy rows may cascade after these current-state guards are clear.
        val hasEconomyValue =
            conn.prepareStatement(
                """
                SELECT
                    EXISTS (SELECT 1 FROM clan_coffers WHERE clan_id = ? AND coin_balance > 0) OR
                    EXISTS (SELECT 1 FROM clan_party_chest_items WHERE clan_id = ?) OR
                    EXISTS (SELECT 1 FROM clan_party_drop_batches WHERE clan_id = ? AND state IN (0, 1)) OR
                    EXISTS (SELECT 1 FROM clan_economy_operations WHERE clan_id = ? AND state = 0)
                """.trimIndent(),
            ).use { ps ->
                repeat(4) { ps.setLong(it + 1, clanId) }
                ps.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
            }
        if (hasEconomyValue) return@transaction false

        conn.prepareStatement(
            "UPDATE clan_applications SET status=4, reviewed_at=CURRENT_TIMESTAMP WHERE clan_id=? AND status=0",
        ).use { ps -> ps.setLong(1, clanId); ps.executeUpdate() }
        conn.prepareStatement("DELETE FROM clans WHERE id = ?").use { ps ->
            ps.setLong(1, clanId)
            ps.executeUpdate() > 0
        }
    }

    fun updateCharacterDisplayName(characterId: Int, newDisplayName: String): Set<Long> = transaction { conn ->
        // Character names live in account_characters. Capture every clan whose snapshot contains a
        // denormalized or joined label for this character before updating those labels.
        val affected = linkedSetOf<Long>()
        conn.prepareStatement(
            """
            SELECT clan_id FROM clan_members WHERE character_id = ?
            UNION SELECT clan_id FROM clan_bans WHERE character_id = ?
            UNION SELECT clan_id FROM clan_events WHERE creator_character_id = ?
            UNION SELECT id AS clan_id FROM clans WHERE owner_character_id = ?
            """.trimIndent(),
        ).use { ps ->
            repeat(4) { index -> ps.setInt(index + 1, characterId) }
            ps.executeQuery().use { rs -> while (rs.next()) affected += rs.getLong(1) }
        }
        conn.prepareStatement("UPDATE clan_bans SET display_name=? WHERE character_id=?").use { ps ->
            ps.setString(1, newDisplayName); ps.setInt(2, characterId); ps.executeUpdate()
        }
        conn.prepareStatement("UPDATE clan_events SET creator_display_name=? WHERE creator_character_id=?").use { ps ->
            ps.setString(1, newDisplayName); ps.setInt(2, characterId); ps.executeUpdate()
        }
        affected
    }

    private fun lockClan(conn: Connection, clanId: Long): Boolean =
        conn.prepareStatement("SELECT id FROM clans WHERE id = ? FOR UPDATE").use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { it.next() }
        }

    private fun memberCount(conn: Connection, clanId: Long): Int =
        conn.prepareStatement("SELECT count(*) FROM clan_members WHERE clan_id = ?").use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    private fun loadAllClans(): List<ClanRow> = query(OpenRuneSql.text("central/clan/load_all_clans.sql")) { it.toClan() }
    private fun loadMembers(): List<ClanMemberRow> = query(OpenRuneSql.text("central/clan/load_all_members.sql")) { it.toMember() }
    private fun loadBans(): List<ClanBanRow> = query(OpenRuneSql.text("central/clan/load_all_bans.sql")) { it.toBan() }
    private fun loadPendingApplications(): List<ClanApplicationRow> = query(OpenRuneSql.text("central/clan/load_pending_applications.sql")) { it.toApplication() }
    private fun loadEvents(): List<ClanEventRow> = query(OpenRuneSql.text("central/clan/load_events.sql")) { it.toEvent() }

    private fun loadIntPairs(resource: String, keyColumn: String, valueColumn: String): Map<Long, Map<Int, Int>> {
        val rows = mutableMapOf<Long, LinkedHashMap<Int, Int>>()
        query(OpenRuneSql.text(resource)) { rs ->
            rows.getOrPut(rs.getLong("clan_id")) { linkedMapOf() }[rs.getInt(keyColumn)] = rs.getInt(valueColumn)
            Unit
        }
        return rows
    }

    private fun <T> queryByCharacter(sql: String, characterId: Int, mapper: (ResultSet) -> T): List<T> = dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapper(rs)) } }
        }
    }

    private fun <T> queryByClan(sql: String, clanId: Long, mapper: (ResultSet) -> T): List<T> = dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, clanId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapper(rs)) } }
        }
    }

    private fun loadIntPairsForClan(resource: String, clanId: Long, keyColumn: String, valueColumn: String): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        queryByClan(OpenRuneSql.text(resource), clanId) { rs ->
            result[rs.getInt(keyColumn)] = rs.getInt(valueColumn)
            Unit
        }
        return result
    }

    private fun <T> query(sql: String, mapper: (ResultSet) -> T): List<T> = dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapper(rs)) } }
        }
    }

    private fun update(sql: String, bind: (java.sql.PreparedStatement) -> Unit): Int = dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { ps -> bind(ps); ps.executeUpdate() }
    }

    private fun upsertPair(table: String, clanId: Long, keyColumn: String, valueColumn: String, key: Int, value: Int) {
        dataSource.connection.use { conn -> upsertIntPair(conn, table, clanId, keyColumn, valueColumn, key, value) }
    }

    private fun upsertIntPair(conn: Connection, table: String, clanId: Long, keyColumn: String, valueColumn: String, key: Int, value: Int) {
        val sql = "INSERT INTO $table (clan_id, $keyColumn, $valueColumn) VALUES (?, ?, ?) ON CONFLICT (clan_id, $keyColumn) DO UPDATE SET $valueColumn = EXCLUDED.$valueColumn"
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, clanId); ps.setInt(2, key); ps.setInt(3, value); ps.executeUpdate()
        }
    }

    private fun replaceIntMap(conn: Connection, table: String, clanId: Long, keyColumn: String, valueColumn: String, values: Map<Int, Int>) {
        conn.prepareStatement("DELETE FROM $table WHERE clan_id = ?").use { ps -> ps.setLong(1, clanId); ps.executeUpdate() }
        if (values.isEmpty()) return
        conn.prepareStatement("INSERT INTO $table (clan_id, $keyColumn, $valueColumn) VALUES (?, ?, ?)").use { ps ->
            for ((key, value) in values) {
                ps.setLong(1, clanId); ps.setInt(2, key); ps.setInt(3, value); ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { conn ->
        val oldAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            val value = block(conn)
            conn.commit()
            value
        } catch (t: Throwable) {
            runCatching { conn.rollback() }
            throw t
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    private fun ResultSet.nullableInt(column: String): Int? = getInt(column).let { if (wasNull()) null else it }
    private fun ResultSet.nullableLong(column: String): Long? = getLong(column).let { if (wasNull()) null else it }

    private fun ResultSet.toCharacter() = ClanCharacterRow(getInt("id"), getLong("account_id"), getString("display_name").orEmpty().trim())

    private fun ResultSet.toClan() = ClanRow(
        clanId = getLong("id"), name = getString("name"), ownerCharacterId = getInt("owner_character_id"),
        ownerDisplayName = getString("owner_display_name").orEmpty(), creationTimeMinutes = getInt("creation_time_minutes"),
        renamedTimeMinutes = getInt("renamed_time_minutes"), allowUnaffined = getBoolean("allow_unaffined"), talkRank = getInt("talk_rank"),
        kickRank = getInt("kick_rank"), homeWorld = getInt("home_world"), interestsMask = getInt("interests_mask"),
        motifTopIcon = getInt("motif_top_icon"), motifBottomIcon = getInt("motif_bottom_icon"), motifColour = getInt("motif_colour"),
        recruitMinCombat = getInt("recruit_min_combat"), recruitMinSkillTotal = getInt("recruit_min_skill_total"),
        recruitMinQuestPoints = getInt("recruit_min_quest_points"), recruitIronStatus = getInt("recruit_iron_status"),
        hallHomeWorldOnly = getBoolean("hall_home_world_only"), hallLocked = getBoolean("hall_locked"),
        hallPlayerCount = getInt("hall_player_count"), boardListed = getBoolean("board_listed"), acceptingApplications = getBoolean("accepting_applications"),
        pendingAbdicationRequesterCharacterId = nullableInt("pending_abdication_requester_character_id"),
        pendingAbdicationRequestedAt = nullableLong("pending_abdication_requested_at"), pendingAbdicationDueAt = nullableLong("pending_abdication_due_at"),
    )

    private fun ResultSet.toMember() =
        ClanMemberRow(
            clanId = getLong("clan_id"),
            characterId = getInt("character_id"),
            displayName = getString("display_name").orEmpty(),
            rank = getInt("rank"),
            joinRuneDay = getInt("join_rune_day"),
            muted = getBoolean("muted"),
            extraInfo = getInt("extra_info"),
            lastSeenRuneDay = getInt("last_seen_rune_day"),
            combatLevel = getInt("combat_level"),
            skillTotal = getInt("skill_total"),
        )
    private fun ResultSet.toBan() = ClanBanRow(getLong("id"), getLong("clan_id"), nullableInt("character_id"), getString("display_name"), nullableInt("banned_by_character_id"), getLong("created_at_millis"))
    private fun ResultSet.toApplication() = ClanApplicationRow(
        id = getLong("id"), clanId = getLong("clan_id"), applicantCharacterId = getInt("applicant_character_id"),
        applicantDisplayName = getString("applicant_display_name"), appliedAtMillis = getLong("applied_at_millis"),
        combatLevel = getInt("applicant_combat_level"), skillTotal = getInt("applicant_skill_total"),
        questPoints = getInt("applicant_quest_points"), ironStatus = getInt("applicant_iron_status"),
        skillLevels = (getArray("applicant_skill_levels")?.array as? Array<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
    )
    private fun ResultSet.toEvent() = ClanEventRow(getLong("id"), getLong("clan_id"), getInt("position"), getInt("event_date"), getInt("event_time"), getInt("duration_days"), getInt("world"), getInt("type"), getInt("subtype"), getInt("activity"), getInt("rank_plus_one"), nullableInt("creator_character_id"), getString("creator_display_name"))

    private fun bindEvent(ps: java.sql.PreparedStatement, event: ClanEventRow, includeClanAndId: Boolean, clanId: Long) {
        ps.setInt(1, event.position); ps.setInt(2, event.date); ps.setInt(3, event.time); ps.setInt(4, event.durationDays)
        ps.setInt(5, event.world); ps.setInt(6, event.type); ps.setInt(7, event.subtype); ps.setInt(8, event.activity); ps.setInt(9, event.rankPlusOne)
        if (event.creatorCharacterId == null) ps.setNull(10, java.sql.Types.INTEGER) else ps.setInt(10, event.creatorCharacterId)
        ps.setString(11, event.creatorDisplayName)
        if (includeClanAndId) { ps.setLong(12, clanId); ps.setLong(13, event.id) }
    }
}

enum class ClanIntField(val column: String) {
    CREATION_TIME_MINUTES("creation_time_minutes"),
    RENAMED_TIME_MINUTES("renamed_time_minutes"),
    TALK_RANK("talk_rank"),
    KICK_RANK("kick_rank"),
    HOME_WORLD("home_world"),
    INTERESTS_MASK("interests_mask"),
    HALL_PLAYER_COUNT("hall_player_count"),
}

enum class ClanBooleanField(val column: String) {
    ALLOW_UNAFFINED("allow_unaffined"),
    HALL_HOME_WORLD_ONLY("hall_home_world_only"),
    HALL_LOCKED("hall_locked"),
}