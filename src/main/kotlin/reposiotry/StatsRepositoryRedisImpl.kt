@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package com.anjo.reposiotry

import com.anjo.configuration.RedisClientProvider
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.Range
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsRepositoryRedisImpl(clientProvider: RedisClientProvider) : StatsRepository {
    private val connection = clientProvider.client.connect()
    val commands: RedisCoroutinesCommands<String, String> = connection.coroutines()

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
        return commands.smembers(key)
    }

    override fun getStats(key: String): Flow<Map<String, String>> {
        val range = Range.unbounded<String>()
        return commands.xrange(key, range)
            .map { it.body }

    }
}