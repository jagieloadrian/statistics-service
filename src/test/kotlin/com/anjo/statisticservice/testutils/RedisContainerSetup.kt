package com.anjo.statisticservice.testutils

import com.redis.testcontainers.RedisContainer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.MapApplicationConfig
import org.testcontainers.junit.jupiter.Container
import redis.clients.jedis.params.RestoreParams
import kotlin.use

import redis.clients.jedis.RedisClient as JedisClient

abstract class RedisContainerSetup {

    private val logger = KotlinLogging.logger {}

    companion object {
        @Container
        @JvmStatic
        private val redis: RedisContainer =
            RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
                .withReuse(true)
    }

    val port : Int = redis.redisPort
    val host: String = redis.host
    val uri: String = redis.redisURI

    fun getRedisConfig(): MapApplicationConfig {
        return MapApplicationConfig(
            "ktor.redis.host" to host,
            "ktor.redis.port" to port.toString(),
        )
    }

    fun importEpidemicData() {
        val jedis = JedisClient.create(uri)
        jedis.ping()
        logger.info { "Adding dump into redis container" }
        val dump =  Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("redis/output.dump")
            ?.use { it.readBytes() } ?: error("Resource not found")
        jedis.use { jediClient ->
            val key = "epidemic:device:testId:run:12"
            val restore = jediClient.restore(key, 0, dump, RestoreParams.restoreParams().replace())
            logger.info { "Restored db with result: $restore" }
            val setAdd = jediClient.sadd("epidemic:runs", key)
            logger.info { "Set add result: $setAdd" }
        }
    }

    fun flushAll() {
        val jedis = JedisClient.create(uri)
        jedis.flushAll()
        logger.info { "Flushed All" }
    }
}