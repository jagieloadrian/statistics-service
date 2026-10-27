package com.anjo.statisticservice.testutils

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

object Pg {
    val container: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:16").apply { start() }

    fun config() = MapApplicationConfig(
        "ktor.db.url" to container.jdbcUrl,
        "ktor.db.user" to container.username,
        "ktor.db.password" to container.password,
    )

    /** Creates the schema if needed and empties every table except organizations. */
    fun truncateAll() {
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { c ->
            c.createStatement().use {
                it.execute(Pg::class.java.getResource("/schema.sql")!!.readText())
                it.execute("TRUNCATE records, definitions, source_credentials, dashboard_shares, auth_tokens, users")
            }
        }
    }
}

fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    environment { config = ApplicationConfig("application-test.yaml").mergeWith(Pg.config()) }
    block()
}
