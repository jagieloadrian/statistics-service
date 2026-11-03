package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.OidcConfig
import io.ktor.server.config.ApplicationConfig
import java.net.URI

private val HTTP_SCHEMES = setOf("http", "https")

internal fun ApplicationConfig.oidcConfig(problems: MutableList<String>): OidcConfig? {
    val issuer = setting("ktor.auth.oidc.issuer")
    val clientId = setting("ktor.auth.oidc.clientId")
    return when {
        issuer.isBlank() && clientId.isBlank() -> null
        issuer.isBlank() || clientId.isBlank() -> null.also { problems += "OIDC_ISSUER and OIDC_CLIENT_ID must be set together" }
        !issuer.isHttpUrl() -> null.also { problems += "OIDC_ISSUER must be an absolute http(s) URL" }
        else -> OidcConfig(issuer, clientId, setting("ktor.auth.oidc.clientSecret"), setting("ktor.auth.oidc.name").ifBlank { "SSO" })
    }
}

private fun String.isHttpUrl(): Boolean =
    runCatching { URI(this) }.getOrNull()?.let { it.isAbsolute && it.scheme in HTTP_SCHEMES && !it.host.isNullOrBlank() } == true
