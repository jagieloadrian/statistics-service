package com.anjo.statisticservice.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface StatsRepository {
    suspend fun saveStats(key: String, value: Map<String, String>): Boolean

    suspend fun addKeyStats(key: String, value: String): Boolean
    fun getKeyStats(key: String): Flow<String>

    suspend fun getStatsByKeys(keys:List<String>): Map<String, Flow<Map<String, String>>>
    fun getStats(key: String): Flow<Map<String, String>>

    fun getStats(key: String, from: Instant, to: Instant): Flow<Map<String, String>>
}