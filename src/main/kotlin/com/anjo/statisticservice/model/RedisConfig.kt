package com.anjo.statisticservice.model

data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String,
    val timeout: Long,
    val clientName: String,
    val ssl: Boolean,
)