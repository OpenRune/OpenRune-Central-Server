package dev.or2.central.analytics

import dev.or2.central.server.session.WorldSessionRepository


/**
 * Periodically snapshots SessionRepository counts into the online_samples table.
 */
class OnlineSampler(
    private val sessionRepository: WorldSessionRepository,
    private val onlineSampleRepository: OnlineSampleRepository,
) {

    fun sampleNow() {
        val timestamp = System.currentTimeMillis()
        val worldCounts = sessionRepository.countsByWorld()

        onlineSampleRepository.insertSnapshot(timestamp, worldCounts)
    }
}