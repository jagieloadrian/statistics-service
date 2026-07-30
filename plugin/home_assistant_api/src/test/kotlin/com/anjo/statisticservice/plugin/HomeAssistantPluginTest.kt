package com.anjo.statisticservice.plugin

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HomeAssistantPluginTest {

    private val plugin = HomeAssistantPlugin()

    @Test
    fun `id is home-assistant`() {
        plugin.id shouldBe "home-assistant"
    }

    @Test
    fun `expose responds 200 with an empty body`() = runTest {
        testApplication {
            val routeSet = plugin.expose()
            install(ContentNegotiation) { json() }
            routing {
                routeSet.configure(this)
            }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }
}
