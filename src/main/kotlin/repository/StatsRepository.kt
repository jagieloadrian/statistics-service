package com.anjo.repository

import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    suspend fun saveStats(key: String, value: Map<String, String>): Boolean

    suspend fun addKeyStats(key: String, value: String): Boolean
    fun getKeyStats(key: String): Flow<String>

    suspend fun getStatsByKeys(keys:List<String>): Map<String, Flow<Map<String, String>>>
    fun getStats(key: String): Flow<Map<String, String>>
}