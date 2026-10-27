package com.anjo.statisticservice

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun shouldReturnOkWhenTestHealthEndpoint() = testApplication {
        environment { config = ApplicationConfig("application-test.yaml") }
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun shouldReturnOkWhenTestReadyEndpoint() = testApplication {
        environment { config = ApplicationConfig("application-test.yaml") }
        client.get("/ready").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
