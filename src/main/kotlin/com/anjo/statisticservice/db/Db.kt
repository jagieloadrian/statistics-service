package com.anjo.statisticservice.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import kotlin.time.Duration.Companion.seconds

class Db(
    url: String,
    user: String,
    password: String,
) : AutoCloseable {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = MAX_POOL_SIZE
                connectionTimeout = CONNECTION_TIMEOUT_MILLIS
            },
        )

    private val database: Database

    init {
        Flyway
            .configure()
            .dataSource(dataSource)
            .baselineOnMigrate(true)
            .load()
            .migrate()
        database = Database.connect(dataSource)
    }

    suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    suspend fun isReachable(): Boolean =
        try {
            withTimeoutOrNull(READINESS_TIMEOUT) {
                runInterruptible(Dispatchers.IO) { dataSource.connection.use { it.isValid(VALIDATION_TIMEOUT_SECONDS) } }
            } == true
        } catch (e: SQLException) {
            false
        }

    override fun close() = dataSource.close()

    companion object {
        const val ORG: Short = 1
        private const val MAX_POOL_SIZE = 10
        private const val CONNECTION_TIMEOUT_MILLIS = 5_000L
        private const val VALIDATION_TIMEOUT_SECONDS = 1
        private val READINESS_TIMEOUT = 2.seconds
    }
}
