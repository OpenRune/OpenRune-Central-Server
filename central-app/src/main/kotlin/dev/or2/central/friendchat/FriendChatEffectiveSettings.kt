package dev.or2.central.friendchat

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * The Friends Chat settings actually in force, as opposed to the configured settings stored in the
 * database.
 *
 * OSRS applies Friends Chat Setup changes in batches ("Changes will take effect on your friends
 * channel in the next 60 seconds"): the first change starts a 60-second window, later changes join
 * it, and they all take effect together when it ends. The channel update arrives 101 game ticks
 * after the first change.
 *
 * Setup writes go to the database immediately, so Setup (sync) shows the configured values. While a
 * batch is pending, every live path (join, restore, talk and kick permission, roster pushes) reads
 * through [load] / [inForce], which keep the values from before the batch.
 *
 * When a window ends, the [onDue] listener is called on the scheduler thread. It must call
 * [complete] under the same lock that guards settings writes and then publish the new settings,
 * so a write can never slip in between the batch ending and its publication.
 *
 * In-memory only: if Central restarts, any pending batch takes effect at once, which only shortens
 * the delay.
 */
class FriendChatEffectiveSettings(
    private val repository: CentralFriendChatRepository,
    private val scheduler: ScheduledExecutorService,
    private val delayMillis: Long = SETTINGS_DELAY_MILLIS,
) {
    private val log = LoggerFactory.getLogger(FriendChatEffectiveSettings::class.java)

    /** One pending batch. Compared by identity, so an old timer can never end a newer batch. */
    class Batch(val channelId: Long, val before: FriendChatChannelRow)

    private val pending = ConcurrentHashMap<Long, Batch>()

    @Volatile
    private var listener: ((Batch) -> Unit)? = null

    fun onDue(listener: (Batch) -> Unit) {
        this.listener = listener
    }

    /** The channel row with the settings in force, or null if the channel does not exist. */
    fun load(channelId: Long): FriendChatChannelRow? = repository.loadById(channelId)?.let(::inForce)

    /** [row] with its Setup fields replaced by the pre-batch values while a batch is pending. */
    fun inForce(row: FriendChatChannelRow): FriendChatChannelRow {
        val before = pending[row.id]?.before ?: return row
        return row.copy(
            displayName = before.displayName,
            minJoin = before.minJoin,
            minTalk = before.minTalk,
            minKick = before.minKick,
            lootShareRank = before.lootShareRank,
            coinShareEnabled = before.coinShareEnabled,
        )
    }

    /**
     * Call with the channel row read *before* a settings write. Starts a batch unless one is
     * already pending for the channel, in which case the write joins that batch.
     */
    fun hold(before: FriendChatChannelRow) {
        val batch = Batch(before.id, before)
        if (pending.putIfAbsent(before.id, batch) != null) return
        try {
            scheduler.schedule({ due(batch) }, delayMillis, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            // No timer means the batch would never end; apply the change now instead.
            pending.remove(before.id, batch)
            throw t
        }
    }

    /** Ends [batch]. Returns false if it already ended, so the caller must not publish again. */
    fun complete(batch: Batch): Boolean = pending.remove(batch.channelId, batch)

    private fun due(batch: Batch) {
        val listener = listener
        if (listener == null) {
            complete(batch)
            return
        }
        try {
            listener(batch)
        } catch (t: Throwable) {
            log.warn("Publishing Friends Chat settings failed for channel {}", batch.channelId, t)
        } finally {
            // Never leave a channel stuck on old settings, whatever the listener did.
            complete(batch)
        }
    }

    companion object {
        const val SETTINGS_DELAY_MILLIS: Long = 60_000L
    }
}
