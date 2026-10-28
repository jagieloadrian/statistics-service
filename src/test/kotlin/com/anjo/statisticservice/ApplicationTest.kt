package com.anjo.statisticservice

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApplicationTest : FunSpec({

    test("liveness answers without touching dependencies") {
        testApp {
            val response = anonymous.get("/health/live")
            response.status shouldBe HttpStatusCode.OK
            response.json().jsonObject.getValue("status").jsonPrimitive.content shouldBe "UP"
        }
    }

    test("readiness answers 200 while the database is reachable") {
        testApp {
            anonymous.get("/health/ready").status shouldBe HttpStatusCode.OK
        }
    }

    test("an unreachable database is reported as not ready") {
        val db = Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password)
        db.isReachable() shouldBe true
        db.close()
        db.isReachable() shouldBe false
    }

    test("prometheus metrics are exposed outside the api") {
        testApp {
            anonymous.get("/health/live")
            val response = anonymous.get("/metrics")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "ktor_http_server_requests_seconds_count"
        }
    }
})
