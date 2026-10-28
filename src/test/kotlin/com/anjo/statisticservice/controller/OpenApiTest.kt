package com.anjo.statisticservice.controller

import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.ktor.client.request.get
import kotlinx.serialization.json.jsonObject

class OpenApiTest : FunSpec({
    beforeTest { Pg.truncateAll() }

    test("every api v2 operation has a summary") {
        testApp {
            val paths = anonymous.get("/api/v2/swagger/documentation.yaml").json().jsonObject.getValue("paths").jsonObject
                .filterKeys { it.startsWith("/api/v2/") }
            paths.size shouldBeGreaterThan 20
            paths.flatMap { (path, ops) -> ops.jsonObject.filterValues { "summary" !in it.jsonObject }.keys.map { "$it $path" } }.shouldBeEmpty()
        }
    }
})
