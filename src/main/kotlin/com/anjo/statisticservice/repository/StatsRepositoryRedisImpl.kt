@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package com.anjo.statisticservice.repository

import com.anjo.statisticservice.configuration.RedisClientProvider
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.Range
import io.lettuce.core.XAddArgs
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant

class StatsRepositoryRedisImpl(clientProvider: RedisClientProvider) :
    StatsRepository {
    private val logger = KotlinLogging.logger { }
    private val connection by lazy(LazyThreadSafetyMode.NONE) {
        clientProvider.client.connect()
    }
    private val commands by lazy(LazyThreadSafetyMode.NONE) {
        connection.coroutines()
    }

    override suspend fun saveStats(
        key: String,
        value: Map<String, String>
    ): Boolean {
        val instant = value[TIMESTAMP_KEY]?.let {
            LocalDateTime.parse(it).toInstant(UtcOffset.ZERO)
        }?.toString() ?: Clock.System.now().toString()
        val xAddArgs = XAddArgs().id("$instant-0")
        val response = commands.xadd(key = key, args = xAddArgs, body = value)
        return !response.isNullOrEmpty()
    }

    override suspend fun addKeyStats(key: String, value: String): Boolean {
        val result = commands.sadd(key, value)
        return result != null && result != 0L
    }

    override fun getKeyStats(key: String): Flow<String> {
        logger.info { "Getting keys from key $key" }
        return commands.smembers(key)
    }

    override suspend fun getStatsByKeys(keys: List<String>): Map<String, Flow<Map<String, String>>> = coroutineScope {
        val jobs = keys.map {
            logger.info { "Calling all values from key: $it" }
            async { it to getStats(it) }
        }
        jobs.awaitAll().toMap()
    }

    override fun getStats(key: String): Flow<Map<String, String>> {
        val range = Range.unbounded<String>()
        return commands.xrange(key, range)
            .map { it.body }
    }

    override fun getStats(
        key: String,
        from: Instant,
        to: Instant
    ): Flow<Map<String, String>> {
        val range = Range.create("${from.toEpochMilliseconds()}-0", "${to.toEpochMilliseconds()}-0")
        return commands.xrange(key, range)
            .map { it.body }
    }

    fun close() {
        runCatching { connection.close() }
    }
}