package com.anjo.statisticservice.testutils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class FakeOidcProvider(
    val clientId: String = "statistics-ui",
) {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val issuer = "http://127.0.0.1:${server.address.port}"

    var claims: Map<String, Any> = user("alice-sub", "alice@test.local", name = "Alice")
    var userinfoClaims: Map<String, Any>? = null
    var signWithOtherKey = false
    var tokenIssuer: String? = null
    var audience = clientId
    var expiresAt: Instant? = null
    var failToken = false

    var lastTokenRequest: Map<String, String> = emptyMap()
        private set

    var lastTraceparent: String? = null
        private set

    private val key = rsa()
    private val otherKey = rsa()

    init {
        server.createContext("/.well-known/openid-configuration") {
            lastTraceparent = it.requestHeaders.getFirst("traceparent")
            it.reply(
                200,
                """{"issuer":"$issuer","authorization_endpoint":"$issuer/authorize","token_endpoint":"$issuer/token",""" +
                    """"userinfo_endpoint":"$issuer/userinfo","jwks_uri":"$issuer/jwks"}""",
            )
        }
        server.createContext("/jwks") {
            val pub = key.public as RSAPublicKey
            it.reply(
                200,
                """{"keys":[{"kty":"RSA","kid":"k1","use":"sig","alg":"RS256","n":"${b64(
                    pub.modulus,
                )}","e":"${b64(pub.publicExponent)}"}]}""",
            )
        }
        server.createContext("/token") {
            lastTokenRequest =
                it.requestBody
                    .readAllBytes()
                    .decodeToString()
                    .split('&')
                    .filter(String::isNotEmpty)
                    .associate { p -> decode(p.substringBefore('=')) to decode(p.substringAfter('=', "")) }
            if (failToken) {
                it.reply(400, """{"error":"invalid_grant"}""")
            } else {
                it.reply(200, """{"access_token":"at-1","token_type":"Bearer","id_token":"${idToken()}"}""")
            }
        }
        server.createContext("/userinfo") {
            if (it.requestHeaders.getFirst("Authorization") != "Bearer at-1") {
                it.reply(401, "{}")
            } else {
                it.reply(
                    200,
                    JsonObject(
                        (userinfoClaims ?: claims).mapValues { (_, v) ->
                            if (v is Boolean) JsonPrimitive(v) else JsonPrimitive("$v")
                        },
                    ).toString(),
                )
            }
        }
        server.start()
    }

    fun stop() = server.stop(0)

    private fun idToken(): String {
        val signingKey = if (signWithOtherKey) otherKey else key
        val now = Clock.System.now()
        return JWT
            .create()
            .withKeyId("k1")
            .withIssuer(tokenIssuer ?: issuer)
            .withAudience(audience)
            .withIssuedAt(now.toJavaInstant())
            .withExpiresAt((expiresAt ?: now + 5.minutes).toJavaInstant())
            .withPayload(claims)
            .sign(Algorithm.RSA256(signingKey.public as RSAPublicKey, signingKey.private as RSAPrivateKey))
    }

    companion object {
        fun user(
            sub: String,
            email: String?,
            verified: Boolean = true,
            name: String? = null,
        ): Map<String, Any> =
            buildMap {
                put("sub", sub)
                email?.let { put("email", it) }
                put("email_verified", verified)
                name?.let { put("name", it) }
            }
    }
}

private fun rsa(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

private fun b64(n: BigInteger) =
    Base64.getUrlEncoder().withoutPadding().encodeToString(
        n
            .toByteArray()
            .dropWhile {
                it == 0.toByte()
            }.toByteArray(),
    )

private fun decode(s: String) = URLDecoder.decode(s, Charsets.UTF_8)

private fun HttpExchange.reply(
    status: Int,
    body: String,
) {
    val bytes = body.toByteArray()
    responseHeaders.add("Content-Type", "application/json")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
