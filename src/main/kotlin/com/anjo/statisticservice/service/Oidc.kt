package com.anjo.statisticservice.service

import com.anjo.statisticservice.config.OidcConfig
import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.dto.OidcStartResponse
import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.NetworkException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }
private val TIMEOUT = 5.seconds
private const val CLOCK_SKEW_SECONDS = 60L
private const val JWKS_CACHE_SIZE = 10L
private const val JWKS_CACHE_HOURS = 24L
private const val JWKS_FETCHES_PER_MINUTE = 10L

data class OidcClaims(val subject: String, val email: String?, val emailVerified: Boolean, val name: String?)

class Oidc(private val config: OidcConfig) {
    val issuer: String get() = config.issuer
    val name: String get() = config.name

    @Serializable
    private class Discovery(
        val issuer: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("userinfo_endpoint") val userinfoEndpoint: String? = null,
        @SerialName("jwks_uri") val jwksUri: String,
    )

    @Serializable
    private class Tokens(
        @SerialName("access_token") val accessToken: String,
        @SerialName("id_token") val idToken: String,
    )

    private data class ProviderMetadata(val discovery: Discovery, val keys: JwkProvider)

    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT.toJavaDuration()).build()

    @Volatile
    private var metadata: ProviderMetadata? = null

    suspend fun start(redirectUri: String): OidcStartResponse =
        withContext(Dispatchers.IO) {
            val endpoint = metadata().discovery.authorizationEndpoint
            val state = newToken()
            val verifier = newToken()
            val query =
                form(
                    "response_type" to "code",
                    "client_id" to config.clientId,
                    "redirect_uri" to redirectUri,
                    "scope" to "openid email profile",
                    "state" to state,
                    "code_challenge" to b64url(sha256(verifier)),
                    "code_challenge_method" to "S256",
                )
            val separator = if ('?' in endpoint) "&" else "?"
            OidcStartResponse(endpoint + separator + query, state, verifier)
        }

    suspend fun redeem(code: String, codeVerifier: String, redirectUri: String): OidcClaims =
        withContext(Dispatchers.IO) {
            val (discovery, keys) = metadata()
            val tokens = requestTokens(discovery, code, codeVerifier, redirectUri)
            val idToken = verify(tokens.idToken, discovery, keys)
            val claims =
                OidcClaims(
                    subject = idToken.subject ?: throw unauthorized("Invalid ID token"),
                    email = idToken.getClaim("email").asString(),
                    emailVerified = idToken.getClaim("email_verified").asBoolean() == true,
                    name = idToken.getClaim("name").asString(),
                )
            val userinfoEndpoint = discovery.userinfoEndpoint
            if (claims.email != null || userinfoEndpoint == null) claims else userinfo(userinfoEndpoint, tokens.accessToken, claims)
        }

    private fun requestTokens(discovery: Discovery, code: String, codeVerifier: String, redirectUri: String): Tokens {
        val request =
            request(discovery.tokenEndpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
        val fields = mutableListOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to redirectUri, "code_verifier" to codeVerifier)
        if (config.clientSecret.isBlank()) {
            fields += "client_id" to config.clientId
        } else {
            val credentials = "${enc(config.clientId)}:${enc(config.clientSecret)}"
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray()))
        }
        val response = send(request.POST(HttpRequest.BodyPublishers.ofString(form(*fields.toTypedArray()))))
        return response
            .takeIf { it.isSuccessful() }
            ?.let { runCatching { json.decodeFromString<Tokens>(it.body()) }.getOrNull() }
            ?: throw unauthorized("Provider refused sign-in")
    }

    private fun verify(token: String, discovery: Discovery, keys: JwkProvider): DecodedJWT =
        try {
            val decoded = JWT.decode(token)
            val key = keys.get(decoded.keyId).publicKey as? RSAPublicKey ?: throw unauthorized("Invalid ID token")
            JWT
                .require(Algorithm.RSA256(key, null))
                .withIssuer(discovery.issuer)
                .withAudience(config.clientId)
                .acceptLeeway(CLOCK_SKEW_SECONDS)
                .build()
                .verify(decoded)
        } catch (e: NetworkException) {
            throw unavailable(e)
        } catch (e: JwkException) {
            throw unauthorized("Invalid ID token")
        } catch (e: JWTVerificationException) {
            throw unauthorized("Invalid ID token")
        }

    private fun userinfo(endpoint: String, accessToken: String, claims: OidcClaims): OidcClaims {
        val response = send(request(endpoint).header("Authorization", "Bearer $accessToken").GET())
        val info =
            response
                .takeIf { it.isSuccessful() }
                ?.let { runCatching { json.parseToJsonElement(it.body()).jsonObject }.getOrNull() }
                ?: throw unauthorized("Provider refused sign-in")
        if (info.claim("sub")?.contentOrNull != claims.subject) throw unauthorized("Provider answered for another user")
        return claims.copy(
            email = info.claim("email")?.contentOrNull,
            emailVerified = info.claim("email_verified")?.booleanOrNull == true,
            name = claims.name ?: info.claim("name")?.contentOrNull,
        )
    }

    private fun metadata(): ProviderMetadata =
        metadata ?: try {
            val response = send(request("${config.issuer.trimEnd('/')}/.well-known/openid-configuration").GET())
            check(response.statusCode() == HTTP_OK) { "discovery answered ${response.statusCode()}" }
            val discovery = json.decodeFromString<Discovery>(response.body())
            check(discovery.issuer == config.issuer) { "discovery names issuer ${discovery.issuer}, OIDC_ISSUER is ${config.issuer}" }
            val keys =
                JwkProviderBuilder(URI(discovery.jwksUri).toURL())
                    .timeouts(TIMEOUT.inWholeMilliseconds.toInt(), TIMEOUT.inWholeMilliseconds.toInt())
                    .cached(JWKS_CACHE_SIZE, JWKS_CACHE_HOURS, TimeUnit.HOURS)
                    .rateLimited(JWKS_FETCHES_PER_MINUTE, 1, TimeUnit.MINUTES)
                    .build()
            ProviderMetadata(discovery, keys).also { metadata = it }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw unavailable(e)
        }

    private fun request(uri: String): HttpRequest.Builder = HttpRequest.newBuilder(URI(uri)).timeout(TIMEOUT.toJavaDuration())

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        try {
            http.send(request.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw unavailable(e)
        }

    private fun unavailable(cause: Exception): ApiException {
        logger.warn { "Sign-in provider ${config.issuer}: $cause" }
        return ApiException(HttpStatusCode.ServiceUnavailable, "unavailable", "Sign-in provider unavailable")
    }

    private companion object {
        const val HTTP_OK = 200
    }
}

private fun HttpResponse<String>.isSuccessful() = statusCode() in 200..299

private fun JsonObject.claim(key: String) = runCatching { get(key)?.jsonPrimitive }.getOrNull()

private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

private fun form(vararg fields: Pair<String, String>) = fields.joinToString("&") { (key, value) -> "$key=${enc(value)}" }

private fun b64url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
