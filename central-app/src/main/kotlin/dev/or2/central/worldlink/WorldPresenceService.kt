package dev.or2.central.worldlink

import dev.or2.central.db.repositories.SessionRepository
import dev.or2.central.http.WorldListCache
import dev.or2.central.social.SocialService
import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

/** Clears Central sessions and game character online markers when a world-link connection drops. */
class WorldPresenceService(
    private val dataSource: DataSource,
    private val sessionRepository: SessionRepository,
    private val worldListCache: WorldListCache?,
    private val socialService: SocialService,
) {
    fun onPushChannelAttached(worldId: Int) {
        socialService.pruneStalePresenceForWorld(worldId)
    }

    fun onWorldDisconnected(worldId: Int) {
        if (worldId <= 0) {
            return
        }
        for (session in sessionRepository.listByWorldId(worldId)) {
            val characterId = session.characterId ?: continue
            socialService.onCharacterOffline(worldId, characterId)
        }
        val sessionsRemoved = sessionRepository.deleteByWorldId(worldId)
        val charactersCleared = clearCharacterOnlineMarkers(worldId)
        if (sessionsRemoved > 0 || charactersCleared > 0) {
            worldListCache?.rebuild()
        }
    }

    private fun clearCharacterOnlineMarkers(worldId: Int): Int {
        val sql = OpenRuneSql.text("game/character/characters_clear_online_presence_on_world.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeUpdate()
            }
        }
    }
}
