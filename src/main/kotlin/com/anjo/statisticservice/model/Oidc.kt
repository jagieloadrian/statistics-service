package com.anjo.statisticservice.model

import com.auth0.jwk.JwkProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class OidcClaims(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
)

@Serializable
class OidcDiscovery(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("userinfo_endpoint") val userinfoEndpoint: String? = null,
    @SerialName("jwks_uri") val jwksUri: String,
)

@Serializable
class OidcTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
)

data class OidcProviderMetadata(
    val discovery: OidcDiscovery,
    val keys: JwkProvider,
)
