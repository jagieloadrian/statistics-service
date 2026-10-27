package com.anjo.statisticservice

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import com.anjo.statisticservice.testutils.testApp
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun shouldReturnOkWhenTestHealthEndpoint() = testApp {
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun shouldReturnOkWhenTestReadyEndpoint() = testApp {
        client.get("/ready").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
