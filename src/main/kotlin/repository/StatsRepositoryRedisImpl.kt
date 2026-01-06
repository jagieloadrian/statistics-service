@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package com.anjo.repository

import com.anjo.configuration.RedisClientProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.Range
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsRepositoryRedisImpl(clientProvider: RedisClientProvider) : StatsRepository {
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
        val response = commands.xadd(key, value)
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
            logger.info {"Calling all values from key: $it"}
            async { it to getStats(it) }
        }
        jobs.awaitAll().toMap()
    }

    override fun getStats(key: String): Flow<Map<String, String>> {
        val range = Range.unbounded<String>()
        return commands.xrange(key, range)
            .map { it.body }
    }

    fun close() {
        runCatching { connection.close() }
    }
}