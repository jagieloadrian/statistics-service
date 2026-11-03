package com.anjo.statisticservice.model

import kotlin.time.Duration

data class JetStreamConfig(
    val url: String,
    val stream: String,
    val subjectPrefix: String,
    val durable: String,
    val maxAge: Duration,
    val maxBytes: Long,
    val user: String,
    val password: String,
    val token: String,
) : BrokerConfig
