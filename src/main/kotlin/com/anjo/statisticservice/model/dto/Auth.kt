package com.anjo.statisticservice.model.dto

import com.anjo.statisticservice.model.User
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: User)

@Serializable
data class PasswordChange(val current: String, val new: String)

@Serializable
data class NewUser(val email: String, val name: String = "", val role: String, val password: String)

@Serializable
data class UserUpdate(val name: String? = null, val role: String? = null)

/** Enabled sign-in methods; public, so no secrets or provider URLs. */
@Serializable
data class AuthConfig(val local: Boolean, val oidc: OidcInfo?)

@Serializable
data class OidcInfo(val name: String)

@Serializable
data class OidcStartRequest(val redirectUri: String)

/** The client keeps [state] and [codeVerifier] and sends the user to [authorizationUrl]. */
@Serializable
data class OidcStartResponse(val authorizationUrl: String, val state: String, val codeVerifier: String)

@Serializable
data class OidcLoginRequest(val code: String, val codeVerifier: String, val redirectUri: String)
