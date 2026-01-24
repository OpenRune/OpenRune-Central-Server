package dev.openrune.central.metrics

import dev.openrune.central.AppState
import dev.openrune.central.storage.JsonBucket
import dev.openrune.central.world.WorldManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Periodically samples players-online counts and persists hourly/daily aggregates (per world and global).
 *
 * Storage model:
 * - bucket: PLAYERS_ONLINE_HISTORY (record-style upserts)
 * - ids:
 *   - hour/{yyyyMMddHH}/world/{worldId}
 *   - hour/{yyyyMMddHH}/global
 *   - day/{yyyyMMdd}/world/{worldId}
 *   - day/{yyyyMMdd}/global
 *
 * This is designed to work across all JsonStorage implementations (mongo/postgres/flat).
 */
object PlayersOnlineHistoryRecorder {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private const val SAMPLE_INTERVAL_MS: Long = 3 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job =
            scope.launch {
                var currentHourKey: String? = null
                var currentDayKey: String? = null

                var hourAggByWorld: MutableMap<Int, Agg> = HashMap()
                var dayAggByWorld: MutableMap<Int, Agg> = HashMap()

                var hourAggGlobal = Agg()
                var dayAggGlobal = Agg()

                while (isActive) {
                    val nowMs = System.currentTimeMillis()
                    val hourKey = hourKeyUtc(nowMs)
                    val dayKey = dayKeyUtc(nowMs)

                    // Roll over hour/day if needed (persist completed buckets).
                    if (currentHourKey != null && currentHourKey != hourKey) {
                        persistHour(currentHourKey, nowMs, hourAggByWorld, hourAggGlobal)
                        hourAggByWorld = HashMap()
                        hourAggGlobal = Agg()
                    }
                    if (currentDayKey != null && currentDayKey != dayKey) {
                        persistDay(currentDayKey, nowMs, dayAggByWorld, dayAggGlobal)
                        dayAggByWorld = HashMap()
                        dayAggGlobal = Agg()
                    }

                    currentHourKey = hourKey
                    currentDayKey = dayKey

                    // Sample current counts.
                    var global = 0
                    for (w in AppState.config.worlds) {
                        val count = WorldManager.onlineCount(w.id)
                        global += count

                        hourAggByWorld.getOrPut(w.id) { Agg() }.add(count)
                        dayAggByWorld.getOrPut(w.id) { Agg() }.add(count)
                    }
                    hourAggGlobal.add(global)
                    dayAggGlobal.add(global)

                    // Also upsert the "in-progress" aggregates so data survives crashes/restarts.
                    persistHour(currentHourKey, nowMs, hourAggByWorld, hourAggGlobal)
                    persistDay(currentDayKey, nowMs, dayAggByWorld, dayAggGlobal)

                    delay(SAMPLE_INTERVAL_MS)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun persistHour(
        hourKey: String,
        nowMs: Long,
        byWorld: Map<Int, Agg>,
        global: Agg
    ) {
        for ((worldId, agg) in byWorld) {
            val id = "hour/$hourKey/world/$worldId"
            AppState.storage.upsert(JsonBucket.PLAYERS_ONLINE_HISTORY, id, json.encodeToString(agg.toHourRecord(worldId, hourKey, nowMs)))
        }
        AppState.storage.upsert(
            JsonBucket.PLAYERS_ONLINE_HISTORY,
            "hour/$hourKey/global",
            json.encodeToString(global.toHourRecord(null, hourKey, nowMs))
        )
    }

    private suspend fun persistDay(
        dayKey: String,
        nowMs: Long,
        byWorld: Map<Int, Agg>,
        global: Agg
    ) {
        for ((worldId, agg) in byWorld) {
            val id = "day/$dayKey/world/$worldId"
            AppState.storage.upsert(JsonBucket.PLAYERS_ONLINE_HISTORY, id, json.encodeToString(agg.toDayRecord(worldId, dayKey, nowMs)))
        }
        AppState.storage.upsert(
            JsonBucket.PLAYERS_ONLINE_HISTORY,
            "day/$dayKey/global",
            json.encodeToString(global.toDayRecord(null, dayKey, nowMs))
        )
    }

    private fun hourKeyUtc(nowMs: Long): String {
        val t = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC)
        val y = t.year
        val m = t.monthValue
        val d = t.dayOfMonth
        val h = t.hour
        return "%04d%02d%02d%02d".format(y, m, d, h)
    }

    private fun dayKeyUtc(nowMs: Long): String {
        val d: LocalDate = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        return "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }

    private class Agg {
        var samples: Int = 0
        var sum: Long = 0
        var min: Int = Int.MAX_VALUE
        var max: Int = Int.MIN_VALUE

        fun add(v: Int) {
            samples += 1
            sum += v.toLong()
            if (v < min) min = v
            if (v > max) max = v
        }

        fun avg(): Double = if (samples == 0) 0.0 else sum.toDouble() / samples.toDouble()

        fun toHourRecord(worldId: Int?, hourKey: String, nowMs: Long): PlayersOnlineHourRecord =
            PlayersOnlineHourRecord(
                id = if (worldId == null) "hour/$hourKey/global" else "hour/$hourKey/world/$worldId",
                scope = if (worldId == null) "GLOBAL" else "WORLD",
                worldId = worldId,
                hourKey = hourKey,
                updatedAtMs = nowMs,
                samples = samples,
                min = if (samples == 0) 0 else min,
                max = if (samples == 0) 0 else max,
                avg = avg()
            )

        fun toDayRecord(worldId: Int?, dayKey: String, nowMs: Long): PlayersOnlineDayRecord =
            PlayersOnlineDayRecord(
                id = if (worldId == null) "day/$dayKey/global" else "day/$dayKey/world/$worldId",
                scope = if (worldId == null) "GLOBAL" else "WORLD",
                worldId = worldId,
                dayKey = dayKey,
                updatedAtMs = nowMs,
                samples = samples,
                min = if (samples == 0) 0 else min,
                max = if (samples == 0) 0 else max,
                avg = avg()
            )
    }
}

@Serializable
data class PlayersOnlineHourRecord(
    @SerialName("id")
    val id: String,
    @SerialName("scope")
    val scope: String,
    @SerialName("worldId")
    val worldId: Int? = null,
    @SerialName("hourKey")
    val hourKey: String,
    @SerialName("updatedAtMs")
    val updatedAtMs: Long,
    @SerialName("samples")
    val samples: Int,
    @SerialName("min")
    val min: Int,
    @SerialName("max")
    val max: Int,
    @SerialName("avg")
    val avg: Double
)

@Serializable
data class PlayersOnlineDayRecord(
    @SerialName("id")
    val id: String,
    @SerialName("scope")
    val scope: String,
    @SerialName("worldId")
    val worldId: Int? = null,
    @SerialName("dayKey")
    val dayKey: String,
    @SerialName("updatedAtMs")
    val updatedAtMs: Long,
    @SerialName("samples")
    val samples: Int,
    @SerialName("min")
    val min: Int,
    @SerialName("max")
    val max: Int,
    @SerialName("avg")
    val avg: Double
)

