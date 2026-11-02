package com.anjo.statisticservice.controller

import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val CLIMATE_DATASET = """{"id":"climate","measures":[{"name":"temperature"},{"name":"humidity"}],"dimensions":[{"name":"device"}]}"""
const val CLIMATE_SOURCE = """{"id":"climate","dataset":"climate","mapping":[{
  "timestamp":{"path":"timestamp"},"identity":["deviceId","timestamp"],
  "dims":{"device":{"path":"deviceId"}},
  "vals":{"temperature":{"path":"temperature"},"humidity":{"path":"humidity","required":false}}}]}"""

const val PUBSUB_CLIMATE_SOURCE = """{"channel":{"type":"pubsub"},"id":"climate","dataset":"climate","mapping":[{
  "timestamp":{"path":"timestamp"},"identity":["deviceId","timestamp"],
  "dims":{"device":{"path":"deviceId"}},"vals":{"temperature":{"path":"temperature"}}}]}"""

suspend fun HttpClient.climateSource(source: String = CLIMATE_SOURCE): String {
    putJson("/api/v2/datasets/climate", CLIMATE_DATASET).status shouldBe HttpStatusCode.OK
    putJson("/api/v2/sources/climate", source).status shouldBe HttpStatusCode.OK
    return postJson("/api/v2/sources/climate/credential", "")
        .json()
        .jsonObject
        .getValue("token")
        .jsonPrimitive.content
}

class IngestRoutesTest :
    FunSpec({
        val payload = """{"deviceId":"pico-1","timestamp":"2099-01-01T00:00:00Z","temperature":21.5}"""

        beforeTest { Pg.truncateAll() }

        test("stores a payload and counts a resend as duplicate") {
            testApp {
                val token = client.climateSource()
                val first = client.postJson("/api/v2/ingest/climate", payload, token)
                first.status shouldBe HttpStatusCode.OK
                first.json().toString() shouldBe """{"accepted":1,"duplicates":0}"""
                client.postJson("/api/v2/ingest/climate", payload, token).json().toString() shouldBe """{"accepted":0,"duplicates":1}"""
            }
        }

        test("dimension values page by limit and offset and ignore unreadable numbers") {
            testApp {
                val token = client.climateSource()
                listOf("pico-1", "pico-2").forEach {
                    client.postJson("/api/v2/ingest/climate", payload.replace("pico-1", it), token).status shouldBe HttpStatusCode.OK
                }

                suspend fun values(query: String) =
                    client
                        .get("/api/v2/datasets/climate/dimensions/device/values$query")
                        .json()
                        .jsonArray
                        .map {
                            it.jsonObject
                                .getValue("value")
                                .jsonPrimitive.content
                        }
                values("").toSet() shouldBe setOf("pico-1", "pico-2")
                values("?limit=1&offset=1").size shouldBe 1
                values("?limit=1&offset=2") shouldBe emptyList()
                values("?limit=many&offset=none").toSet() shouldBe setOf("pico-1", "pico-2")
            }
        }

        test("missing or rotated token gives 401, unknown source 404") {
            testApp {
                val old = client.climateSource()
                client.postJson("/api/v2/ingest/climate", payload).status shouldBe HttpStatusCode.Unauthorized
                anonymous.postJson("/api/v2/ingest/climate", payload).status shouldBe HttpStatusCode.Unauthorized
                client.postJson("/api/v2/sources/climate/credential", "")
                client.postJson("/api/v2/ingest/climate", payload, old).status shouldBe HttpStatusCode.Unauthorized
                client.postJson("/api/v2/ingest/nope", payload, old).status shouldBe HttpStatusCode.NotFound
            }
        }

        test("constraint failure gives 400 with reasons and shows in status") {
            testApp {
                val token = client.climateSource()
                val r = client.postJson("/api/v2/ingest/climate", """{"deviceId":"pico-1","timestamp":"2099-01-01T00:00:00Z"}""", token)
                r.status shouldBe HttpStatusCode.BadRequest
                r
                    .json()
                    .jsonObject
                    .getValue("details")
                    .jsonArray
                    .map { it.jsonPrimitive.content } shouldContain
                    "vals.temperature: missing at path 'temperature'"

                val status = client.get("/api/v2/sources/climate/status").json().jsonObject
                status
                    .getValue("rejected")
                    .jsonObject
                    .getValue("hour")
                    .jsonPrimitive.content shouldBe "1"
                status
                    .getValue("rejections")
                    .jsonArray
                    .first()
                    .jsonObject
                    .getValue("reason")
                    .jsonPrimitive.content shouldBe
                    "vals.temperature: missing at path 'temperature'"
                status.getValue("state").jsonPrimitive.content shouldBe "waiting-for-data"
            }
        }

        test("a pubsub source refuses HTTP push with 409") {
            testApp {
                val token = client.climateSource(PUBSUB_CLIMATE_SOURCE)
                client.postJson("/api/v2/ingest/climate", payload, token).status shouldBe HttpStatusCode.Conflict
            }
        }

        test("paused source gives 503 with Retry-After") {
            testApp {
                val token = client.climateSource()
                client.putJson("/api/v2/sources/climate", CLIMATE_SOURCE.replaceFirst("{", """{"paused":true,"""))
                val r = client.postJson("/api/v2/ingest/climate", payload, token)
                r.status shouldBe HttpStatusCode.ServiceUnavailable
                r.headers[HttpHeaders.RetryAfter] shouldNotBe null
            }
        }

        test("over the rate limit gives 429") {
            testApp {
                val token = client.climateSource()
                val statuses =
                    coroutineScope {
                        (1..80).map { async { client.postJson("/api/v2/ingest/climate", payload, token) } }.awaitAll()
                    }
                val limited = statuses.filter { it.status == HttpStatusCode.TooManyRequests }
                (limited.size >= 30) shouldBe true
                limited.first().headers[HttpHeaders.RetryAfter] shouldNotBe null
            }
        }
    })
