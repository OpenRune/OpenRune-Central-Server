package dev.openrune.central.storage.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.openrune.central.config.PostgresStorageConfig
import dev.openrune.central.storage.JsonBucket
import dev.openrune.central.storage.JsonStorage
import dev.openrune.central.storage.StorageNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.sql.Connection

class PostgresJsonStorage(private val cfg: PostgresStorageConfig) : JsonStorage {
    private val ds: HikariDataSource
    private val ensuredLogTables: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        val hc = HikariConfig().apply {
            jdbcUrl = cfg.jdbcUrl
            username = cfg.username
            password = cfg.password
            maximumPoolSize = 10
            isAutoCommit = true
        }
        ds = HikariDataSource(hc)
        migrate()
    }

    override suspend fun upsert(bucket: JsonBucket, id: String, json: String) {
        withContext(Dispatchers.IO) {
            ds.connection.use { c ->
                c.prepareStatement(
                    """
                    insert into ${tableName(bucket)} (id, data, updated_at)
                    values (?, ?::jsonb, now())
                    on conflict (id) do update set data = excluded.data, updated_at = now()
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, id)
                    ps.setString(2, json)
                    ps.executeUpdate()
                }
            }
        }
    }

    override suspend fun get(bucket: JsonBucket, id: String): String? {
        return withContext(Dispatchers.IO) {
            ds.connection.use { c ->
                c.prepareStatement("select data::text from ${tableName(bucket)} where id = ?").use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) null else rs.getString(1)
                    }
                }
            }
        }
    }

    override suspend fun append(bucket: JsonBucket, json: String) {
        withContext(Dispatchers.IO) {
            ds.connection.use { c ->
                c.prepareStatement(
                    "insert into ${logTableName(bucket)} (data, created_at) values (?::jsonb, now())"
                ).use { ps ->
                    ps.setString(1, json)
                    ps.executeUpdate()
                }
            }
        }
    }

    override suspend fun append(bucket: JsonBucket, partition: String, json: String) {
        val table = partitionedLogTableName(bucket, partition)
        ensurePartitionedLogTable(table)
        withContext(Dispatchers.IO) {
            ds.connection.use { c ->
                c.prepareStatement(
                    "insert into $table (data, created_at) values (?::jsonb, now())"
                ).use { ps ->
                    ps.setString(1, json)
                    ps.executeUpdate()
                }
            }
        }
    }

    override fun close() {
        ds.close()
    }

    private fun migrate() {
        ds.connection.use { c ->
            c.autoCommit = true
            c.createStatement().use { st ->
                st.execute("create schema if not exists ${cfg.schema}")
            }
            for (b in JsonBucket.entries) {
                c.createStatement().use { st ->
                    st.execute(
                        """
                        create table if not exists ${tableName(b)} (
                            id text primary key,
                            data jsonb not null,
                            updated_at timestamptz not null
                        )
                        """.trimIndent()
                    )
                    st.execute(
                        """
                        create table if not exists ${logTableName(b)} (
                            id bigserial primary key,
                            data jsonb not null,
                            created_at timestamptz not null
                        )
                        """.trimIndent()
                    )
                }
            }
        }
    }

    private fun tableName(bucket: JsonBucket): String = "${cfg.schema}.${StorageNaming.bucketSnake(bucket)}_records"
    private fun logTableName(bucket: JsonBucket): String = "${cfg.schema}.${StorageNaming.bucketSnake(bucket)}_logs"

    private fun partitionedLogTableName(bucket: JsonBucket, partition: String): String {
        val base =
            when (bucket) {
                JsonBucket.LOGS -> StorageNaming.logPartitionName(partition)
                else -> StorageNaming.bucketSnake(bucket) + "_" + StorageNaming.toSnakeCase(partition) + "_logs"
            }
        val safe = base.replace(Regex("[^a-z0-9_]"), "")
        return "${cfg.schema}.$safe"
    }

    private fun ensurePartitionedLogTable(qualifiedTable: String) {
        if (!ensuredLogTables.add(qualifiedTable)) return
        ds.connection.use { c ->
            c.createStatement().use { st ->
                st.execute(
                    """
                    create table if not exists $qualifiedTable (
                        id bigserial primary key,
                        data jsonb not null,
                        created_at timestamptz not null
                    )
                    """.trimIndent()
                )
            }
        }
    }
}

