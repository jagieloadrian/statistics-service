package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.KafkaConfig
import com.anjo.statisticservice.model.SERVICE_NAME
import io.ktor.server.config.ApplicationConfig

private val KAFKA_TOPIC = Regex("^[A-Za-z0-9._-]{1,249}$")
private val KAFKA_SECURITY_PROTOCOLS = setOf("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL")
private val KAFKA_SASL_MECHANISMS = setOf("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512")

internal fun ApplicationConfig.kafkaConfig(problems: MutableList<String>): KafkaConfig {
    fun kafka(key: String) = setting("ktor.broker.kafka.$key").trim()
    val config =
        KafkaConfig(
            bootstrapServers = kafka("bootstrapServers"),
            groupId = kafka("groupId").ifBlank { SERVICE_NAME },
            topic = kafka("topic").ifBlank { "stats.ingest" },
            securityProtocol = kafka("securityProtocol").ifBlank { "PLAINTEXT" },
            saslMechanism = kafka("saslMechanism").ifBlank { "PLAIN" },
            user = kafka("user"),
            password = setting("ktor.broker.kafka.password"),
        )
    if (config.bootstrapServers.isBlank()) problems += "KAFKA_BOOTSTRAP_SERVERS is required when BROKER=kafka"
    if (!KAFKA_TOPIC.matches(config.topic)) problems += "KAFKA_TOPIC must match ${KAFKA_TOPIC.pattern}"
    if (config.securityProtocol !in KAFKA_SECURITY_PROTOCOLS) {
        problems += "KAFKA_SECURITY_PROTOCOL must be one of ${KAFKA_SECURITY_PROTOCOLS.joinToString()}"
    }
    problems += listOfNotNull(config.saslProblem())
    return config
}

private fun KafkaConfig.saslProblem(): String? {
    val credentials = user.isNotBlank() || password.isNotBlank()
    return when {
        !usesSasl && credentials -> "KAFKA_USER and KAFKA_PASSWORD need KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT or SASL_SSL"
        !usesSasl -> null
        saslMechanism !in KAFKA_SASL_MECHANISMS -> "KAFKA_SASL_MECHANISM must be one of ${KAFKA_SASL_MECHANISMS.joinToString()}"
        user.isBlank() || password.isBlank() -> "KAFKA_USER and KAFKA_PASSWORD are required with SASL"
        else -> null
    }
}
