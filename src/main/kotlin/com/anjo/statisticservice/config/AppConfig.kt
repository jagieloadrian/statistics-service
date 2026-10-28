package com.anjo.statisticservice.config

import io.ktor.server.config.ApplicationConfig
import java.net.URI

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
)

fun ApplicationConfig.toAppConfig(): AppConfig {
    val problems = mutableListOf<String>()

    fun setting(key: String) = propertyOrNull(key)?.getString().orEmpty()

    fun flag(
        key: String,
        envName: String,
        default: Boolean,
    ): Boolean =
        when (setting(key).ifBlank { default.toString() }) {
            "true" -> true
            "false" -> false
            else -> default.also { problems += "$envName must be true or false" }
        }

    val db = DbConfig(setting("ktor.db.url"), setting("ktor.db.user"), setting("ktor.db.password"))
    if (!db.url.startsWith("jdbc:postgresql://")) problems += "DB_URL must be a jdbc:postgresql:// URL"
    if (db.user.isBlank()) problems += "DB_USER is required"

    val localSignIn = flag("ktor.auth.local", "AUTH_LOCAL", default = true)
    val issuer = setting("ktor.auth.oidc.issuer")
    val clientId = setting("ktor.auth.oidc.clientId")
    val oidc =
        when {
            issuer.isBlank() && clientId.isBlank() -> null
            issuer.isBlank() || clientId.isBlank() -> null.also { problems += "OIDC_ISSUER and OIDC_CLIENT_ID must be set together" }
            !issuer.isHttpUrl() -> null.also { problems += "OIDC_ISSUER must be an absolute http(s) URL" }
            else -> OidcConfig(issuer, clientId, setting("ktor.auth.oidc.clientSecret"), setting("ktor.auth.oidc.name").ifBlank { "SSO" })
        }
    if (!localSignIn && oidc == null) problems += "Enable local sign-in (AUTH_LOCAL=true) or configure OIDC_ISSUER and OIDC_CLIENT_ID"

    val config =
        AppConfig(
            db = db,
            localSignIn = localSignIn,
            adminEmail = setting("ktor.auth.adminEmail"),
            adminPassword = setting("ktor.auth.adminPassword"),
            oidc = oidc,
            corsHosts = setting("ktor.cors.hosts").split(',').map(String::trim).filter(String::isNotEmpty),
            trustProxy = flag("ktor.deployment.trustProxy", "TRUST_PROXY", default = false),
        )
    check(problems.isEmpty()) { "Invalid configuration: ${problems.joinToString("; ")}" }
    return config
}

private fun String.isHttpUrl(): Boolean =
    runCatching { URI(this) }.getOrNull()?.let { it.isAbsolute && it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } == true
