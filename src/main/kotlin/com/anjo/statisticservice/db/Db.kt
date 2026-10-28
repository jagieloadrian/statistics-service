package com.anjo.statisticservice.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class Db(url: String, user: String, password: String) : AutoCloseable {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = MAX_POOL_SIZE
            },
        )

    private val database: Database

    init {
        Flyway.configure().dataSource(dataSource).baselineOnMigrate(true).load().migrate()
        database = Database.connect(dataSource)
    }

    suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override fun close() = dataSource.close()

    companion object {
        const val ORG: Short = 1
        private const val MAX_POOL_SIZE = 10
    }
}
