package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.JetStreamConfig
import com.anjo.statisticservice.model.SERVICE_NAME
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration

private val NATS_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")
private val NATS_SUBJECT = Regex("^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*$")
private val NATS_URL = Regex("^(nats|tls)://\\S+$")
private const val DEFAULT_STREAM_MAX_BYTES = "268435456"

internal fun ApplicationConfig.jetStreamConfig(problems: MutableList<String>): JetStreamConfig {
    fun nats(key: String) = setting("ktor.broker.jetstream.$key").trim()
    val maxAge = Duration.parseOrNull(nats("maxAge").ifBlank { "7d" })
    val maxBytes = nats("maxBytes").ifBlank { DEFAULT_STREAM_MAX_BYTES }.toLongOrNull()
    if (maxAge == null || !maxAge.isPositive()) problems += "NATS_STREAM_MAX_AGE must be a positive duration such as 7d or 12h"
    if (maxBytes == null || maxBytes <= 0) problems += "NATS_STREAM_MAX_BYTES must be a positive number of bytes"
    val config =
        JetStreamConfig(
            url = nats("url"),
            stream = nats("stream").ifBlank { "STATS_INGEST" },
            subjectPrefix = nats("subjectPrefix").ifBlank { "stats.ingest" },
            durable = nats("durable").ifBlank { SERVICE_NAME },
            maxAge = maxAge ?: Duration.ZERO,
            maxBytes = maxBytes ?: 0,
            user = nats("user"),
            password = setting("ktor.broker.jetstream.password"),
            token = setting("ktor.broker.jetstream.token"),
        )
    if (!NATS_URL.matches(config.url)) problems += "NATS_URL must be a nats:// or tls:// URL when BROKER=jetstream"
    if (!NATS_NAME.matches(config.stream)) problems += "NATS_STREAM must match ${NATS_NAME.pattern}"
    if (!NATS_NAME.matches(config.durable)) problems += "NATS_DURABLE must match ${NATS_NAME.pattern}"
    if (!NATS_SUBJECT.matches(config.subjectPrefix)) problems += "NATS_SUBJECT_PREFIX must be dot-separated tokens without wildcards"
    if (config.user.isBlank() != config.password.isBlank()) problems += "NATS_USER and NATS_PASSWORD must be set together"
    if (config.token.isNotBlank() && config.user.isNotBlank()) problems += "Use NATS_TOKEN or NATS_USER/NATS_PASSWORD, not both"
    return config
}
