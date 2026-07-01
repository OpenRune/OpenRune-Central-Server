package dev.or2.central.db

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import javax.sql.DataSource

object FlywayMigrator {
    private val log = LoggerFactory.getLogger(FlywayMigrator::class.java)

    private fun configure(dataSource: DataSource): Flyway =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .initSql("SET client_min_messages TO WARNING")
            .load()

    fun migrate(dataSource: DataSource) {
        val result = configure(dataSource).migrate()
        dataSource.connection.use { conn ->
            val meta = conn.metaData
            PostgresStartupLog.logMigrations(log, meta.url, meta.userName.orEmpty(), result.migrationsExecuted)
        }
    }

    fun migrate(jdbcUrl: String, user: String, password: String) {
        val result =
            configure(
                object : DataSource {
                    override fun getConnection() = DriverManager.getConnection(jdbcUrl, user, password)

                    override fun getConnection(username: String?, password: String?) =
                        DriverManager.getConnection(jdbcUrl, username, password)

                    override fun getLogWriter() = null

                    override fun setLogWriter(out: java.io.PrintWriter?) = Unit

                    override fun setLoginTimeout(seconds: Int) = Unit

                    override fun getLoginTimeout(): Int = 0

                    override fun getParentLogger() = throw java.sql.SQLFeatureNotSupportedException()

                    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw java.sql.SQLFeatureNotSupportedException()

                    override fun isWrapperFor(iface: Class<*>?) = false
                },
            ).migrate()
        PostgresStartupLog.logMigrations(log, jdbcUrl, user, result.migrationsExecuted)
    }
}
