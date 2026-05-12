package dev.or2.central.analytics

import dev.or2.sql.OpenRuneSql
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

class OnlineSampleRepository(
    private val dataSource: DataSource,
) {

    fun insertSnapshot(
        sampledAtMillis: Long,
        countsByWorld: Map<Int, Int>,
    ) {
        if (countsByWorld.values.none { it > 0 }) return

        val sql = OpenRuneSql.text("central/online_samples/insert.sql")

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                countsByWorld.forEach { (worldId, count) ->
                    if (count <= 0) return@forEach

                    ps.setLong(1, sampledAtMillis)
                    ps.setInt(2, worldId)
                    ps.setInt(3, count)
                    ps.addBatch()
                }

                ps.executeBatch()
            }
        }
    }

    fun hourlyAggregatesUtc(
        fromEpochMillisInclusive: Long,
        toEpochMillisExclusive: Long,
        worldId: Int?,
    ): List<OnlineHourlyBucket> {

        val sql = OpenRuneSql.text("central/online_samples/hourly_aggregate.sql")

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, fromEpochMillisInclusive)
                ps.setLong(2, toEpochMillisExclusive)
                bindWorld(ps, 3, worldId)
                bindWorld(ps, 4, worldId)

                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                OnlineHourlyBucket(
                                    bucketStartUtcMillis = rs.getLong("bucket_utc_millis"),
                                    worldId = rs.getInt("world_id"),
                                    peakOnline = rs.getInt("peak_online"),
                                    avgOnline = rs.getDouble("avg_online"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun dailyAggregatesUtc(
        fromEpochMillisInclusive: Long,
        toEpochMillisExclusive: Long,
        worldId: Int?,
    ): List<OnlineDailyBucket> {

        val sql = OpenRuneSql.text("central/online_samples/daily_aggregate.sql")

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, fromEpochMillisInclusive)
                ps.setLong(2, toEpochMillisExclusive)
                bindWorld(ps, 3, worldId)
                bindWorld(ps, 4, worldId)

                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                OnlineDailyBucket(
                                    dayUtc = rs.getDate("day_utc").toLocalDate().toString(),
                                    worldId = rs.getInt("world_id"),
                                    peakOnline = rs.getInt("peak_online"),
                                    avgOnline = rs.getDouble("avg_online"),
                                    sampleCount = rs.getLong("sample_count"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bindWorld(ps: PreparedStatement, index: Int, worldId: Int?) {
        if (worldId == null) {
            ps.setNull(index, Types.INTEGER)
        } else {
            ps.setInt(index, worldId)
        }
    }
}