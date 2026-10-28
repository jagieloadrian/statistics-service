package com.anjo.statisticservice.testutils

import com.anjo.statisticservice.db.Db
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

object Pg {
    val container: PostgreSQLContainer =
        PostgreSQLContainer("postgres:16").apply {
            start()
            Db(jdbcUrl, username, password).close()
        }

    fun config() =
        MapApplicationConfig(
            "ktor.db.url" to container.jdbcUrl,
            "ktor.db.user" to container.username,
            "ktor.db.password" to container.password,
            "ktor.auth.adminEmail" to ADMIN_EMAIL,
            "ktor.auth.adminPassword" to ADMIN_PASSWORD,
        )

    fun truncateAll() = truncate("records", "definitions", "source_credentials", "dashboard_shares", "auth_tokens", "users")

    fun truncate(vararg tables: String) = sql("TRUNCATE ${tables.joinToString()}")

    fun sql(statement: String) {
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { c ->
            c.createStatement().use { it.execute(statement) }
        }
    }
}

const val ADMIN_EMAIL = "admin@test.local"
const val ADMIN_PASSWORD = "admin-password"

class TestApp(
    private val builder: ApplicationTestBuilder,
    val client: HttpClient,
) {
    val anonymous get() = builder.client

    suspend fun login(
        email: String,
        password: String,
    ): HttpClient =
        withToken(
            anonymous
                .postJson(
                    "/api/v2/auth/login",
                    """{"email":"$email","password":"$password"}""",
                ).json()
                .jsonObject
                .getValue("token")
                .jsonPrimitive.content,
        )

    fun withToken(token: String): HttpClient {
        val bearer =
            createClientPlugin("userToken") {
                onRequest { req, _ ->
                    if (HttpHeaders.Authorization !in
                        req.headers
                    ) {
                        req.headers[HttpHeaders.Authorization] = "Bearer $token"
                    }
                }
            }
        return builder.createClient { install(bearer) }
    }

    suspend fun userClient(
        email: String,
        role: String,
        password: String = "password-$role",
    ): HttpClient {
        client.postJson("/api/v2/users", """{"email":"$email","role":"$role","password":"$password"}""")
        return login(email, password)
    }
}

fun testApp(
    extraConfig: Map<String, String> = emptyMap(),
    block: suspend TestApp.() -> Unit,
) = testApplication {
    val cfg = ApplicationConfig("application-test.yaml").mergeWith(Pg.config()).mergeWith(MapApplicationConfig(extraConfig.toList()))
    environment { config = cfg }
    startApplication()
    val admin = cfg.property("ktor.auth.local").getString() != "false" && cfg.property("ktor.auth.adminPassword").getString().isNotBlank()
    TestApp(this, client).run { TestApp(this@testApplication, if (admin) login(ADMIN_EMAIL, ADMIN_PASSWORD) else anonymous).block() }
}

private fun HttpRequestBuilder.json(
    body: String,
    token: String?,
) {
    contentType(ContentType.Application.Json)
    setBody(body)
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
}

suspend fun HttpClient.putJson(
    path: String,
    body: String,
    token: String? = null,
) = put(path) { json(body, token) }

suspend fun HttpClient.postJson(
    path: String,
    body: String,
    token: String? = null,
) = post(path) { json(body, token) }

suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())
