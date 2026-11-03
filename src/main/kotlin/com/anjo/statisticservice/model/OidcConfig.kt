package com.anjo.statisticservice.model

data class OidcConfig(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val name: String,
)
