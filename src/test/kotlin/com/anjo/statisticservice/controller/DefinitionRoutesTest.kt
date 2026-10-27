package com.anjo.statisticservice.controller

import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefinitionRoutesTest : FunSpec({
    beforeTest { Pg.truncateAll() }

    test("put, get, list and delete dataset and source") {
        testApp {
            client.climateSource()
            client.get("/api/v2/datasets").json().jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content } shouldBe listOf("climate")
            client.get("/api/v2/sources/climate").json().jsonObject["dataset"]!!.jsonPrimitive.content shouldBe "climate"
            client.get("/api/v2/sources/climate").bodyAsText() shouldNotContain "token"

            client.delete("/api/v2/sources/climate").status shouldBe HttpStatusCode.OK
            client.get("/api/v2/sources/climate").status shouldBe HttpStatusCode.NotFound
            client.delete("/api/v2/datasets/climate").status shouldBe HttpStatusCode.OK
            client.delete("/api/v2/datasets/climate").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("invalid references and mismatched ids give 400") {
        testApp {
            client.putJson("/api/v2/sources/climate", CLIMATE_SOURCE).status shouldBe HttpStatusCode.BadRequest // no dataset yet
            client.putJson("/api/v2/datasets/climate", CLIMATE_DATASET)
            client.putJson("/api/v2/sources/climate", CLIMATE_SOURCE.replace("\"humidity\":{", "\"pressure\":{")).status shouldBe HttpStatusCode.BadRequest
            client.putJson("/api/v2/datasets/other", CLIMATE_DATASET).status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("mapping-test returns records and errors and stores nothing") {
        testApp {
            client.climateSource()
            val ok = client.postJson(
                "/api/v2/sources/climate/mapping-test",
                """{"payload":{"deviceId":"pico-1","timestamp":"2026-10-27T10:00:00Z","temperature":21}}""",
            ).json().jsonObject
            ok["records"]!!.jsonArray.single().jsonObject["dims"].toString() shouldBe """{"device":"pico-1"}"""
            ok["errors"]!!.jsonArray.size shouldBe 0

            val bad = client.postJson("/api/v2/sources/climate/mapping-test", """{"payload":{"deviceId":"pico-1","timestamp":"2026-10-27T10:00:00Z"}}""").json().jsonObject
            bad["errors"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("vals.temperature: missing at path 'temperature'")

            val query = """{"dataset":"climate","measures":[{"field":"temperature"}],"bucket":"raw","from":"2026-10-27T00:00:00Z","to":"2026-10-28T00:00:00Z"}"""
            client.postJson("/api/v2/query", query).json().jsonObject["series"]!!.jsonArray.size shouldBe 0
        }
    }

    test("credential rotation returns a new token each time") {
        testApp {
            val first = client.climateSource()
            val second = client.postJson("/api/v2/sources/climate/credential", "").json().jsonObject["token"]!!.jsonPrimitive.content
            second shouldNotBe first
            client.postJson("/api/v2/sources/nope/credential", "").status shouldBe HttpStatusCode.NotFound
        }
    }
})
