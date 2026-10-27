package com.anjo.statisticservice.testutils

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

private fun HttpRequestBuilder.json(body: String, token: String?) {
    contentType(ContentType.Application.Json)
    setBody(body)
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
}

suspend fun HttpClient.putJson(path: String, body: String, token: String? = null) = put(path) { json(body, token) }
suspend fun HttpClient.postJson(path: String, body: String, token: String? = null) = post(path) { json(body, token) }
suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())
