package com.anjo

import com.redis.testcontainers.RedisContainer
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers
class ApplicationTest {

    companion object {
        @Container
        @JvmStatic
        val redis: RedisContainer = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
            .withReuse(true)
    }

    @Test
    fun shouldReturnOkWhenTestHealthEndpoint() = testApplication {
        val redisContainerProps = MapApplicationConfig(
            "ktor.redis.host" to redis.host,
            "ktor.redis.port" to redis.redisPort.toString(),
        )
        environment {
            config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
        }
        //Application module started by application-test.yaml
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun shouldReturnOkWhenTestReadyEndpoint() = testApplication {
        val redisContainerProps = MapApplicationConfig(
            "ktor.redis.host" to redis.host,
            "ktor.redis.port" to redis.redisPort.toString(),
        )
        environment {
            config = ApplicationConfig("application-test.yaml").mergeWith(redisContainerProps)
        }
        //Application module started by application-test.yaml
        client.get("/ready").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
