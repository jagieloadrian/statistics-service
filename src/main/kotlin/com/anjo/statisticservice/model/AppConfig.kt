package com.anjo.statisticservice.model

const val SERVICE_NAME = "statistics-service"

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
