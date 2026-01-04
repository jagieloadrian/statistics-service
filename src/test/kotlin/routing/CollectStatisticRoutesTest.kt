package com.anjo.routing

import com.anjo.model.EpidemicDto
import com.anjo.model.EpidemicMetaDto
import com.anjo.model.EpidemicParamsDto
import com.anjo.model.EpidemicStateDto
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
                timestamp = 0L,
                generation = 0
            ),
            EpidemicParamsDto(
                populationSize = 0,
                infectionProb = 0.0,
                infectionTtlMin = 2,
                infectionTtlMax = 5
            ),
            state = EpidemicStateDto(
                susceptible = 0,
                infected = 0,
                recovered = 0
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
                timestamp = 1234L,
                generation = 1
            ),
            EpidemicParamsDto(
                populationSize = 100,
                infectionProb = 0.3,
                infectionTtlMin = 2,
                infectionTtlMax = 5
            ),
            state = EpidemicStateDto(
                susceptible = 25,
                infected = 25,
                recovered = 50
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