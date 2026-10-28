package com.anjo.statisticservice.controller

import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class ImportExportTest :
    FunSpec({
        beforeTest { Pg.truncateAll() }

        suspend fun HttpClient.importFile(name: String) =
            postJson("/api/v2/import", File("definitions/$name.json").readText()).json().toString()

        test("export then import into an empty DB reproduces the documents without credentials") {
            testApp {
                client.importFile("temperature") shouldBe """{"created":3,"updated":0,"errors":[]}"""
                client.importFile("epidemic") shouldBe """{"created":3,"updated":0,"errors":[]}"""
                val token =
                    client
                        .postJson(
                            "/api/v2/sources/temperature/credential",
                            "",
                        ).json()
                        .jsonObject
                        .getValue("token")
                        .jsonPrimitive.content
                val exported = client.get("/api/v2/export").bodyAsText()
                exported shouldNotContain token
                exported shouldNotContain "token"

                Pg.truncate("records", "definitions", "source_credentials")
                client.postJson("/api/v2/import", exported).json().toString() shouldBe """{"created":6,"updated":0,"errors":[]}"""
                client.get("/api/v2/export").bodyAsText() shouldBe exported
                client.postJson("/api/v2/import", exported).json().toString() shouldBe """{"created":0,"updated":6,"errors":[]}"""
            }
        }

        test("invalid bundle imports nothing") {
            testApp {
                val bundle = """{"version":1,
                "datasets":[{"id":"ok","measures":[{"name":"v"}]}],
                "sources":[{"id":"bad","dataset":"missing"}]}"""
                val r = client.postJson("/api/v2/import", bundle)
                r.status shouldBe HttpStatusCode.BadRequest
                r
                    .json()
                    .jsonObject
                    .getValue("errors")
                    .jsonArray
                    .map { it.jsonPrimitive.content } shouldBe
                    listOf("source bad: Unknown dataset 'missing'")
                client
                    .get("/api/v2/datasets")
                    .json()
                    .jsonArray.size shouldBe 0
            }
        }

        test("removing a referenced measure or deleting a used dataset needs force") {
            testApp {
                client.importFile("temperature")
                val withoutHumidity = """{"id":"temperature","measures":[{"name":"temperature"}],"dimensions":[{"name":"device"}]}"""
                val r = client.putJson("/api/v2/datasets/temperature", withoutHumidity)
                r.status shouldBe HttpStatusCode.Conflict
                r
                    .json()
                    .jsonObject
                    .getValue("details")
                    .jsonArray
                    .map { it.jsonPrimitive.content } shouldBe
                    listOf("temperature/climate", "source temperature")
                client.putJson("/api/v2/datasets/temperature?force=true", withoutHumidity).status shouldBe HttpStatusCode.OK

                val token =
                    client
                        .postJson(
                            "/api/v2/sources/temperature/credential",
                            "",
                        ).json()
                        .jsonObject
                        .getValue("token")
                        .jsonPrimitive.content
                client.postJson(
                    "/api/v2/ingest/temperature",
                    """{"deviceId":"d","timestamp":"2099-01-01T00:00:00Z","temperature":1}""",
                    token,
                )
                client.delete("/api/v2/datasets/temperature").status shouldBe HttpStatusCode.Conflict
                client.delete("/api/v2/datasets/temperature?force=true").status shouldBe HttpStatusCode.OK

                client.putJson("/api/v2/datasets/temperature", withoutHumidity)
                val query =
                    """{"dataset":"temperature","measures":[{"field":"temperature"}],"bucket":"raw",""" +
                        """"from":"2098-01-01T00:00:00Z","to":"2100-01-01T00:00:00Z"}"""
                client
                    .postJson("/api/v2/query", query)
                    .json()
                    .jsonObject
                    .getValue("series")
                    .jsonArray.size shouldBe 0
            }
        }
    })
