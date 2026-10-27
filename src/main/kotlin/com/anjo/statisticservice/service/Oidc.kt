package com.anjo.statisticservice.service

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
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }
private val TIMEOUT = Duration.ofSeconds(5)

/** What the provider vouches for about the signed-in user. */
data class OidcClaims(val subject: String, val email: String?, val emailVerified: Boolean, val name: String?)

/**
 * Authorization Code + PKCE against one OpenID Connect provider; [name] is what the sign-in button shows.
 * The backend redeems the code itself and verifies the ID token against the provider's JWKS.
 */
class Oidc(val issuer: String, private val clientId: String, private val clientSecret: String, val name: String) {

    @Serializable
    private class Discovery(
        val issuer: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("userinfo_endpoint") val userinfoEndpoint: String? = null,
        @SerialName("jwks_uri") val jwksUri: String,
    )

    @Serializable
    private class Tokens(@SerialName("access_token") val accessToken: String, @SerialName("id_token") val idToken: String)

    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    /** Fetched on first use and kept, so the platform starts and serves passwords while the provider is down. */
    @Volatile
    private var provider: Pair<Discovery, JwkProvider>? = null

    /** State and PKCE verifier stay with the client; the backend keeps nothing between start and [redeem]. */
    suspend fun start(redirectUri: String): OidcStartResponse = withContext(Dispatchers.IO) {
        val (d) = provider()
        val state = newToken()
        val verifier = newToken()
        // ponytail: no nonce — code+PKCE, ID token straight from the token endpoint
        val query = form(
            "response_type" to "code", "client_id" to clientId, "redirect_uri" to redirectUri, "scope" to "openid email profile",
            "state" to state, "code_challenge" to b64url(sha256(verifier)), "code_challenge_method" to "S256",
        )
        OidcStartResponse(d.authorizationEndpoint + (if ('?' in d.authorizationEndpoint) "&" else "?") + query, state, verifier)
    }

    /** Redeems [code] and returns the verified ID token's claims; email from userinfo when the token has none. */
    suspend fun redeem(code: String, codeVerifier: String, redirectUri: String): OidcClaims = withContext(Dispatchers.IO) {
        val (d, jwks) = provider()
        val request = HttpRequest.newBuilder(URI(d.tokenEndpoint)).timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
        val fields = mutableListOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to redirectUri, "code_verifier" to codeVerifier)
        if (clientSecret.isBlank()) fields += "client_id" to clientId
        else request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("${enc(clientId)}:${enc(clientSecret)}".toByteArray()))
        val response = send(request.POST(HttpRequest.BodyPublishers.ofString(form(*fields.toTypedArray()))).build())
        val tokens = response.takeIf { it.statusCode() in 200..299 }
            ?.let { runCatching { json.decodeFromString<Tokens>(it.body()) }.getOrNull() }
            ?: throw unauthorized("Provider refused sign-in")
        val id = verify(tokens.idToken, d, jwks)
        val claims = OidcClaims(
            id.subject ?: throw unauthorized("Invalid ID token"),
            id.getClaim("email").asString(),
            id.getClaim("email_verified").asBoolean() == true,
            id.getClaim("name").asString(),
        )
        if (claims.email != null || d.userinfoEndpoint == null) claims else userinfo(d.userinfoEndpoint, tokens.accessToken, claims)
    }

    private fun verify(token: String, d: Discovery, jwks: JwkProvider): DecodedJWT = try {
        val decoded = JWT.decode(token)
        // ponytail: RS256 only, add ES256 when a provider needs it
        val key = jwks.get(decoded.keyId).publicKey as? RSAPublicKey ?: throw unauthorized("Invalid ID token")
        JWT.require(Algorithm.RSA256(key, null)).withIssuer(d.issuer).withAudience(clientId).acceptLeeway(60).build().verify(decoded)
    } catch (e: NetworkException) {
        throw unavailable(e)
    } catch (e: JwkException) {
        throw unauthorized("Invalid ID token")
    } catch (e: JWTVerificationException) {
        throw unauthorized("Invalid ID token")
    }

    private fun userinfo(endpoint: String, accessToken: String, claims: OidcClaims): OidcClaims {
        val response = send(HttpRequest.newBuilder(URI(endpoint)).timeout(TIMEOUT).header("Authorization", "Bearer $accessToken").GET().build())
        val info = response.takeIf { it.statusCode() in 200..299 }
            ?.let { runCatching { json.parseToJsonElement(it.body()).jsonObject }.getOrNull() }
            ?: throw unauthorized("Provider refused sign-in")
        fun claim(key: String) = runCatching { info[key]?.jsonPrimitive }.getOrNull()
        if (claim("sub")?.contentOrNull != claims.subject) throw unauthorized("Provider answered for another user")
        return claims.copy(
            email = claim("email")?.contentOrNull,
            emailVerified = claim("email_verified")?.booleanOrNull == true,
            name = claims.name ?: claim("name")?.contentOrNull,
        )
    }

    private fun provider(): Pair<Discovery, JwkProvider> = provider ?: try {
        val response = send(HttpRequest.newBuilder(URI("${issuer.trimEnd('/')}/.well-known/openid-configuration")).timeout(TIMEOUT).GET().build())
        check(response.statusCode() == 200) { "discovery answered ${response.statusCode()}" }
        val d = json.decodeFromString<Discovery>(response.body())
        check(d.issuer == issuer) { "discovery names issuer ${d.issuer}, OIDC_ISSUER is $issuer" }
        val jwks = JwkProviderBuilder(URI(d.jwksUri).toURL()).timeouts(5000, 5000)
            .cached(10, 24, TimeUnit.HOURS).rateLimited(10, 1, TimeUnit.MINUTES).build()
        (d to jwks).also { provider = it }
    } catch (e: ApiException) {
        throw e
    } catch (e: Exception) {
        throw unavailable(e)
    }

    private fun send(request: HttpRequest): HttpResponse<String> = try {
        http.send(request, HttpResponse.BodyHandlers.ofString())
    } catch (e: IOException) {
        throw unavailable(e)
    }

    private fun unavailable(cause: Exception): ApiException {
        logger.warn { "Sign-in provider $issuer: $cause" }
        return ApiException(HttpStatusCode.ServiceUnavailable, "unavailable", "Sign-in provider unavailable")
    }
}

private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

private fun form(vararg fields: Pair<String, String>) = fields.joinToString("&") { (k, v) -> "$k=${enc(v)}" }

private fun b64url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
