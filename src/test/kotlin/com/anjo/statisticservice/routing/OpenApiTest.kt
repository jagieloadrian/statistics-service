package com.anjo.statisticservice.routing

import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.testApp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.ktor.client.request.get
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Every /api/v2 operation is documented in Swagger with a summary. */
class OpenApiTest {
    @BeforeEach
    fun clean() = Pg.truncateAll()

    @Test
    fun `every api v2 operation has a summary`() = testApp {
        val paths = anonymous.get("/api/v2/swagger/documentation.yaml").json().jsonObject["paths"]!!.jsonObject
            .filterKeys { it.startsWith("/api/v2/") }
        paths.size shouldBeGreaterThan 20
        paths.flatMap { (path, ops) -> ops.jsonObject.filterValues { "summary" !in it.jsonObject }.keys.map { "$it $path" } }.shouldBeEmpty()
    }
}
