package dev.or2.central.analytics

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.or2.central.CentralSchemaBootstrap
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OnlineSampleRepositoryTest {
    companion object {
        private lateinit var embedded: EmbeddedPostgres

        @JvmStatic
        @BeforeAll
        fun postgresUp() {
            embedded = EmbeddedPostgres.start()
        }

        @JvmStatic
        @AfterAll
        fun postgresDown() {
            if (::embedded.isInitialized) {
                embedded.close()
            }
        }
    }

    private lateinit var dataSource: DataSource
    private lateinit var repository: OnlineSampleRepository

    @BeforeEach
    fun setup() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    dataSource = embedded.getPostgresDatabase()
                    maximumPoolSize = 4
                    connectionInitSql = "SET application_name = 'openrune_online_sample_test'"
                },
            )
        CentralSchemaBootstrap.apply(dataSource)
        repository = OnlineSampleRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        (dataSource as HikariDataSource).close()
    }

    @Test
    fun hourlyAndDailyAggregates() {
        val t10 = Instant.parse("2026-03-01T10:15:00Z").toEpochMilli()
        val t11 = Instant.parse("2026-03-01T11:20:00Z").toEpochMilli()
        repository.insertSnapshot(t10, mapOf(1 to 3))
        repository.insertSnapshot(t10 + 60_000L, mapOf(1 to 5))
        repository.insertSnapshot(t11, mapOf(1 to 7))
        val dayStart = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()
        val dayEnd = Instant.parse("2026-03-02T00:00:00Z").toEpochMilli()
        val hourly = repository.hourlyAggregatesUtc(dayStart, dayEnd, worldId = 1)
        assertEquals(2, hourly.size)
        val h10 = hourly.find { it.bucketStartUtcMillis == Instant.parse("2026-03-01T10:00:00Z").toEpochMilli() }!!
        assertEquals(5, h10.peakOnline)
        assertTrue(h10.avgOnline >= 3.9 && h10.avgOnline <= 4.1)
        val h11 = hourly.find { it.bucketStartUtcMillis == Instant.parse("2026-03-01T11:00:00Z").toEpochMilli() }!!
        assertEquals(7, h11.peakOnline)

        val daily = repository.dailyAggregatesUtc(dayStart, dayEnd, worldId = 1)
        assertEquals(1, daily.size)
        assertEquals("2026-03-01", daily[0].dayUtc)
        assertEquals(7, daily[0].peakOnline)
        assertEquals(3L, daily[0].sampleCount)
    }

    @Test
    fun hourlyAllWorldsWhenWorldFilterNull() {
        val t = Instant.parse("2026-04-10T12:00:00Z").toEpochMilli()
        repository.insertSnapshot(t, mapOf(1 to 4))
        val from = Instant.parse("2026-04-10T00:00:00Z").toEpochMilli()
        val to = Instant.parse("2026-04-11T00:00:00Z").toEpochMilli()
        val allWorlds = repository.hourlyAggregatesUtc(from, to, worldId = null)
        assertEquals(1, allWorlds.size)
        assertEquals(1, allWorlds[0].worldId)
        assertEquals(4, allWorlds[0].peakOnline)
    }
}