package com.anjo.statisticservice.routing

import com.anjo.statisticservice.model.responsedto.EpidemicRun
import com.anjo.statisticservice.model.responsedto.EpidemicShortRun
import com.anjo.statisticservice.model.responsedto.EpidemicSummary
import com.anjo.statisticservice.testutils.RedisContainerSetup
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class StatsEpidemicExposerRoutingTest : RedisContainerSetup() {

    private val epidemicApiPath = "api/v1/stats/expose/epidemic"
//    private val temperatureApiPath = "api/v1/stats/expose/temperature"

    @AfterEach
    fun tearDown() {
        flushAll()
    }

    @Test
    fun `given call {api v1 stats expose epidemic runs} then return list of short runs`() = testApplication {
        //given
        val redisContainerProps = getRedisConfig()
        environment {
            config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        importEpidemicData()

        //when and then
        client.get("$epidemicApiPath/runs") {
            contentType(ContentType.Application.Json)
        }.apply {
            status shouldBe HttpStatusCode.OK
            val response = body<List<EpidemicShortRun>>()

            response.shouldNotBeEmpty()
            response shouldHaveSize 1
            response.first() shouldNotBe null
        }
    }

    @Test
    fun `given call {api v1 stats expose epidemic device deviceId run runId} then return run with points`() =
        testApplication {
            //given
            val redisContainerProps = getRedisConfig()
            environment {
                config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            importEpidemicData()

            //when and then
            client.get("$epidemicApiPath/device/testId/run/12") {
                contentType(ContentType.Application.Json)
            }.apply {
                status shouldBe HttpStatusCode.OK
                val response = body<EpidemicRun>()

                response.shouldNotBeNull()
                response.runId shouldBe "12"
                response.meta.population shouldBe 200
                response.timeline shouldHaveSize 4
            }
        }

    @Test
    fun `given call {api v1 stats expose epidemic device deviceId run runId summary} then return summary of run`() =
        testApplication {
            //given
            val redisContainerProps = getRedisConfig()
            environment {
                config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            importEpidemicData()

            //when and then
            client.get("$epidemicApiPath/device/testId/run/12/summary") {
                contentType(ContentType.Application.Json)
            }.apply {
                status shouldBe HttpStatusCode.OK
                val response = body<EpidemicSummary>()

                response.shouldNotBeNull()
                response.peakInfected shouldBe 55
                response.timeToPeak shouldBe 7
                response.duration shouldBe 10
            }
        }

    @Test
    fun `given call {api v1 stats expose epidemic runs} then return empty list of short runs`() = testApplication {
        //given
        val redisContainerProps = getRedisConfig()
        environment {
            config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        //when and then
        client.get("$epidemicApiPath/runs") {
            contentType(ContentType.Application.Json)
        }.apply {
            status shouldBe HttpStatusCode.OK
            val response = body<List<EpidemicShortRun>>()

            response.shouldBeEmpty()
        }
    }

    @Test
    fun `given call {api v1 stats expose epidemic device deviceId run runId} with non exist key then return not found`() =
        testApplication {
            //given
            val redisContainerProps = getRedisConfig()
            environment {
                config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            importEpidemicData()

            //when and then
            client.get("$epidemicApiPath/device/nonExistDevice/run/nonExistRun") {
                contentType(ContentType.Application.Json)
            }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `given call {api v1 stats expose epidemic device deviceId run runId summary} with non exist key then return not found`() =
        testApplication {
            //given
            val redisContainerProps = getRedisConfig()
            environment {
                config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            importEpidemicData()

            //when and then
            client.get("$epidemicApiPath/device/nonExistDevice/run/nonExistRun/summary") {
                contentType(ContentType.Application.Json)
            }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }
}