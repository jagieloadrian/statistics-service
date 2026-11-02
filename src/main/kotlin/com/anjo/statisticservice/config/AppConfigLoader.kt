package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.AppConfig
import com.anjo.statisticservice.model.BrokerConfig
import com.anjo.statisticservice.model.DbConfig
import com.anjo.statisticservice.model.JetStreamConfig
import com.anjo.statisticservice.model.KafkaConfig
import com.anjo.statisticservice.model.OidcConfig
import com.anjo.statisticservice.model.SERVICE_NAME
import io.ktor.server.config.ApplicationConfig
import java.net.URI
import kotlin.time.Duration

private val KAFKA_TOPIC = Regex("^[A-Za-z0-9._-]{1,249}$")
private val KAFKA_SECURITY_PROTOCOLS = setOf("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL")
private val KAFKA_SASL_MECHANISMS = setOf("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512")
private val NATS_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")
private val NATS_SUBJECT = Regex("^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*$")
private val NATS_URL = Regex("^(nats|tls)://\\S+$")
private const val DEFAULT_STREAM_MAX_BYTES = "268435456"

fun ApplicationConfig.toAppConfig(): AppConfig {
    val problems = mutableListOf<String>()
    val db = DbConfig(setting("ktor.db.url"), setting("ktor.db.user"), setting("ktor.db.password"))
    if (!db.url.startsWith("jdbc:postgresql://")) problems += "DB_URL must be a jdbc:postgresql:// URL"
    if (db.user.isBlank()) problems += "DB_USER is required"

    val localSignIn = flag("ktor.auth.local", "AUTH_LOCAL", default = true, problems)
    val oidc = oidcConfig(problems)
    if (!localSignIn && oidc == null) problems += "Enable local sign-in (AUTH_LOCAL=true) or configure OIDC_ISSUER and OIDC_CLIENT_ID"

    val config =
        AppConfig(
            db = db,
            localSignIn = localSignIn,
            adminEmail = setting("ktor.auth.adminEmail"),
            adminPassword = setting("ktor.auth.adminPassword"),
            oidc = oidc,
            corsHosts = setting("ktor.cors.hosts").split(',').map(String::trim).filter(String::isNotEmpty),
            trustProxy = flag("ktor.deployment.trustProxy", "TRUST_PROXY", default = false, problems),
            broker = brokerConfig(problems),
        )
    check(problems.isEmpty()) { "Invalid configuration: ${problems.joinToString("; ")}" }
    return config
}

private fun ApplicationConfig.setting(key: String) = propertyOrNull(key)?.getString().orEmpty()

private fun ApplicationConfig.flag(
    key: String,
    envName: String,
    default: Boolean,
    problems: MutableList<String>,
): Boolean =
    when (setting(key).ifBlank { default.toString() }) {
        "true" -> true
        "false" -> false
        else -> default.also { problems += "$envName must be true or false" }
    }

private fun ApplicationConfig.oidcConfig(problems: MutableList<String>): OidcConfig? {
    val issuer = setting("ktor.auth.oidc.issuer")
    val clientId = setting("ktor.auth.oidc.clientId")
    return when {
        issuer.isBlank() && clientId.isBlank() -> null
        issuer.isBlank() || clientId.isBlank() -> null.also { problems += "OIDC_ISSUER and OIDC_CLIENT_ID must be set together" }
        !issuer.isHttpUrl() -> null.also { problems += "OIDC_ISSUER must be an absolute http(s) URL" }
        else -> OidcConfig(issuer, clientId, setting("ktor.auth.oidc.clientSecret"), setting("ktor.auth.oidc.name").ifBlank { "SSO" })
    }
}

private fun ApplicationConfig.brokerConfig(problems: MutableList<String>): BrokerConfig? =
    when (val type = setting("ktor.broker.type").trim().ifBlank { "none" }) {
        "none" -> null
        "kafka" -> kafkaConfig(problems)
        "jetstream" -> jetStreamConfig(problems)
        else -> null.also { problems += "BROKER must be none, kafka or jetstream, not '$type'" }
    }

private fun ApplicationConfig.kafkaConfig(problems: MutableList<String>): KafkaConfig {
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
    val credentials = config.user.isNotBlank() || config.password.isNotBlank()
    val saslProblem =
        when {
            !config.usesSasl && credentials -> "KAFKA_USER and KAFKA_PASSWORD need KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT or SASL_SSL"
            !config.usesSasl -> null
            config.saslMechanism !in KAFKA_SASL_MECHANISMS -> "KAFKA_SASL_MECHANISM must be one of ${KAFKA_SASL_MECHANISMS.joinToString()}"
            config.user.isBlank() || config.password.isBlank() -> "KAFKA_USER and KAFKA_PASSWORD are required with SASL"
            else -> null
        }
    problems += listOfNotNull(saslProblem)
    return config
}

private fun ApplicationConfig.jetStreamConfig(problems: MutableList<String>): JetStreamConfig {
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

private fun String.isHttpUrl(): Boolean =
    runCatching { URI(this) }.getOrNull()?.let { it.isAbsolute && it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } == true
