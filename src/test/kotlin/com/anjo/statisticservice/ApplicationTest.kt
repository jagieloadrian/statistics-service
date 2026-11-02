package com.anjo.statisticservice

import com.anjo.statisticservice.di.drainInFlightCallsOnStop
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.sql.Connection
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ApplicationTest :
    FunSpec({

        test("liveness answers without touching dependencies") {
            testApp {
                val response = anonymous.get("/health/live")
                response.status shouldBe HttpStatusCode.OK
                response
                    .json()
                    .jsonObject
                    .getValue("status")
                    .jsonPrimitive.content shouldBe "UP"
            }
        }

        test("readiness answers 200 while the database is reachable") {
            testApp {
                anonymous.get("/health/ready").status shouldBe HttpStatusCode.OK
            }
        }

        test("an unreachable database is reported as not ready") {
            val db = Pg.db()
            db.isReachable() shouldBe true
            db.tx { (connection.connection as Connection).networkTimeout } shouldBe 60_000
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

        test("stopping waits for in-flight calls to finish") {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    drainInFlightCallsOnStop()
                    routing {
                        get("/slow") {
                            delay(500.milliseconds)
                            call.respondText("done")
                        }
                    }
                }
                startApplication()
                coroutineScope {
                    val response = async { client.get("/slow") }
                    delay(100.milliseconds)
                    val waited =
                        measureTime { withContext(Dispatchers.IO) { app.monitor.raise(ApplicationStopPreparing, app.environment) } }
                    waited shouldBeGreaterThan 250.milliseconds
                    response.await().bodyAsText() shouldBe "done"
                }
            }
        }
    })
