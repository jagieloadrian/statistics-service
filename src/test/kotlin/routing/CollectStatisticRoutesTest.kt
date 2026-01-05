package com.anjo.routing

import com.anjo.model.DetailedData
import com.anjo.model.EpidemicDto
import com.anjo.model.EpidemicMetaDto
import com.anjo.model.EpidemicStateDto
import com.anjo.model.HumanType
import com.redis.testcontainers.RedisContainer
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class CollectStatisticRoutesTest {

    companion object {
        @Container
        @JvmStatic
        val redis: RedisContainer =
            RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
                .withReuse(true)
    }

    private val apiPath = "api/v1/stats/epidemic"

    @Test
    fun `given wrongly request body when call {api v1 stats epidemic} then return list of errors`() = testApplication {
        //given
        val redisContainerProps = MapApplicationConfig(
            "ktor.redis.host" to redis.host,
            "ktor.redis.port" to redis.redisPort.toString(),
        )
        environment {
            config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val requestBody = EpidemicDto(
            EpidemicMetaDto(
                deviceId = "",
                runId = 0,
                timestamp = Clock.System.now(),
                generation = 0
            ),
            state = EpidemicStateDto(
                susceptible = 0,
                infected = 0,
                recovered = 0,
                population = 0,
                mobilityMultiplier = 0.0,
                dead = 0,
                exposed = 0,
                lockdown = false,
                detailedDataByType = mapOf(),
            )
        )

        //when and then
        client.post(apiPath) {
            setBody(requestBody)
            contentType(ContentType.Application.Json)
        }.apply {
            status shouldBe HttpStatusCode.BadRequest
            val response = body<List<String>>()
            response.shouldNotBeEmpty()
        }
    }

    @Test
    fun `given request body when call {api v1 stats epidemic} then return ok`() = testApplication {
        //given
        val redisContainerProps = MapApplicationConfig(
            "ktor.redis.host" to redis.host,
            "ktor.redis.port" to redis.redisPort.toString(),
        )
        environment {
            config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val requestBody = EpidemicDto(
            EpidemicMetaDto(
                deviceId = "testId",
                runId = 1,
                timestamp = Clock.System.now(),
                generation = 1
            ),
            state = EpidemicStateDto(
                susceptible = 25,
                infected = 25,
                recovered = 50,
                population = 100,
                mobilityMultiplier = 0.3,
                dead = 0,
                exposed = 0,
                lockdown = false,
                detailedDataByType = mapOf(
                    HumanType.CHILD to DetailedData(
                        susceptible = 1,
                        infected = 1,
                        recovered = 1,
                        dead = 1,
                        exposed = 1
                    )
                )
            )
        )

        //when and then
        client.post(apiPath) {
            setBody(requestBody)
            contentType(ContentType.Application.Json)
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

}