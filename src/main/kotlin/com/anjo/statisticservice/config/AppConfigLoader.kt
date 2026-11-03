package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.AppConfig
import com.anjo.statisticservice.model.BrokerConfig
import io.ktor.server.config.ApplicationConfig

fun ApplicationConfig.toAppConfig(): AppConfig {
    val problems = mutableListOf<String>()
    val db = dbConfig(problems)
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

internal fun ApplicationConfig.setting(key: String) = propertyOrNull(key)?.getString().orEmpty()

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

private fun ApplicationConfig.brokerConfig(problems: MutableList<String>): BrokerConfig? =
    when (val type = setting("ktor.broker.type").trim().ifBlank { "none" }) {
        "none" -> null
        "kafka" -> kafkaConfig(problems)
        "jetstream" -> jetStreamConfig(problems)
        else -> null.also { problems += "BROKER must be none, kafka or jetstream, not '$type'" }
    }
