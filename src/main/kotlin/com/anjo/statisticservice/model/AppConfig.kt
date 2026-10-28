package com.anjo.statisticservice.model

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
