package com.anjo.statisticservice.model

import kotlin.time.Duration

const val SERVICE_NAME = "statistics-service"

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
)

data class OidcConfig(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val name: String,
)

data class AppConfig(
    val db: DbConfig,
    val localSignIn: Boolean,
    val adminEmail: String,
    val adminPassword: String,
    val oidc: OidcConfig?,
    val corsHosts: List<String>,
    val trustProxy: Boolean,
    val broker: BrokerConfig?,
)

sealed interface BrokerConfig

data class KafkaConfig(
    val bootstrapServers: String,
    val groupId: String,
    val topic: String,
    val securityProtocol: String,
    val saslMechanism: String,
    val user: String,
    val password: String,
) : BrokerConfig {
    val usesSasl get() = securityProtocol.startsWith("SASL_")
}

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
