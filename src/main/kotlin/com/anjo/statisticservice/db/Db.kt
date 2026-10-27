package com.anjo.statisticservice.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class Db(url: String, user: String, password: String) : AutoCloseable {
    private val ds = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 10
    })

    private val database: Database

    init {
        // a database created before Flyway already has the V1 schema and is baselined there
        Flyway.configure().dataSource(ds).baselineOnMigrate(true).load().migrate()
        database = Database.connect(ds)
    }

    /** Runs [block] in one transaction; repositories called inside it share it. */
    suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override fun close() = ds.close()

    companion object {
        const val ORG: Short = 1
    }
}

fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(toJavaInstant(), ZoneOffset.UTC)
