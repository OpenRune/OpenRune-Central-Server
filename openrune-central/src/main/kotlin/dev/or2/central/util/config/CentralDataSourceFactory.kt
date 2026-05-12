package dev.or2.central.util.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun createCentralDataSource(config: CentralRuntimeConfig): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            driverClassName = "org.postgresql.Driver"
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = config.dbMaximumPoolSize
            poolName = "openrune-central"
            connectionInitSql = "SET application_name = 'openrune_central'"
        },
    )
