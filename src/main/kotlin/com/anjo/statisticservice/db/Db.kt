package com.anjo.statisticservice.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
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

    fun init() {
        val schema = Db::class.java.getResource("/schema.sql")!!.readText()
        ds.connection.use { c -> c.createStatement().use { it.execute(schema) } }
    }

    suspend fun <T> tx(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        ds.connection.use { c ->
            c.autoCommit = false
            try {
                block(c).also { c.commit() }
            } catch (e: Throwable) {
                c.rollback()
                throw e
            }
        }
    }

    override fun close() = ds.close()

    companion object {
        // v1 serves exactly one organization (spec FR-020); every owned row carries it
        const val ORG: Short = 1
    }
}

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { st ->
        st.bind(params)
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
    }

fun Connection.update(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { st ->
        st.bind(params)
        st.executeUpdate()
    }

private fun PreparedStatement.bind(params: Array<out Any?>) {
    params.forEachIndexed { i, p ->
        when (p) {
            is Instant -> setObject(i + 1, OffsetDateTime.ofInstant(p.toJavaInstant(), ZoneOffset.UTC))
            is List<*> -> setArray(i + 1, connection.createArrayOf("text", p.map { it.toString() }.toTypedArray()))
            else -> setObject(i + 1, p)
        }
    }
}
