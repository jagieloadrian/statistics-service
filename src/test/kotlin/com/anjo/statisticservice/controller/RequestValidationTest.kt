package com.anjo.statisticservice.controller

import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private suspend fun HttpResponse.shouldBeBadRequestWith(vararg reasons: String) {
    status shouldBe HttpStatusCode.BadRequest
    val error = json().jsonObject
    error.getValue("error").jsonPrimitive.content shouldBe "bad_request"
    error.getValue("details").jsonArray.map { it.jsonPrimitive.content } shouldContainAll reasons.toList()
}

class RequestValidationTest : FunSpec({
    beforeTest { Pg.truncateAll() }

    test("a new user is rejected with every broken rule listed") {
        testApp {
            client.postJson("/api/v2/users", """{"email":"nobody","role":"boss","password":"short"}""").shouldBeBadRequestWith(
                "email: not an email address",
                "role: admin | editor | viewer",
                "password: at least 10 characters, at most 1024",
            )
        }
    }

    test("a query without measures, with an unknown bucket and reversed range is rejected") {
        testApp {
            val query = """{"dataset":"climate","bucket":"2w","from":"2026-10-02T00:00:00Z","to":"2026-10-01T00:00:00Z"}"""
            client.postJson("/api/v2/query", query).shouldBeBadRequestWith(
                "measures: at least one is required",
                "bucket: auto | raw | none | 1m | 5m | 10m | 30m | 1h | 6h | 1d",
                "'to' must be after 'from'",
            )
        }
    }

    test("preferences, sign-in and definitions are validated before any lookup") {
        testApp {
            client.putJson("/api/v2/me/prefs", """{"units":"furlongs","theme":"neon"}""")
                .shouldBeBadRequestWith("units: metric | imperial", "theme: system | light | dark")
            anonymous.postJson("/api/v2/auth/oidc/start", """{"redirectUri":"callback"}""")
                .shouldBeBadRequestWith("redirectUri: absolute URI of at most 2048 characters")
            anonymous.postJson("/api/v2/auth/login", """{"email":" ","password":""}""")
                .shouldBeBadRequestWith("email: required, at most 254 characters", "password: required, at most 1024 characters")
            client.putJson("/api/v2/datasets/climate", """{"id":"climate","version":2,"measures":[{"name":"ts"}]}""")
                .shouldBeBadRequestWith("version: must be 1", "name: 'ts' is reserved")
            client.putJson("/api/v2/dashboards/home/shares", """{"userIds":[0]}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("malformed path parameters and bodies answer 400") {
        testApp {
            client.delete("/api/v2/users/abc").status shouldBe HttpStatusCode.BadRequest
            client.postJson("/api/v2/users", """{"email":""").status shouldBe HttpStatusCode.BadRequest
        }
    }
})
