package com.anjo.statisticservice.controller

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.repository.UserRepository
import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.testutils.FakeOidcProvider
import com.anjo.statisticservice.testutils.ADMIN_EMAIL
import com.anjo.statisticservice.testutils.ADMIN_PASSWORD
import com.anjo.statisticservice.testutils.FakeOidcProvider.Companion.user
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.TestApp
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val REDIRECT = "http://127.0.0.1:51234/callback"

/** Runs [block] against an app configured for a fresh [FakeOidcProvider]; [TestApp.client] is the bootstrap admin when there is one. */
private fun oidcApp(extraConfig: Map<String, String> = emptyMap(), block: suspend TestApp.(FakeOidcProvider) -> Unit) {
    val idp = FakeOidcProvider()
    try {
        val provider = mapOf("ktor.auth.oidc.issuer" to idp.issuer, "ktor.auth.oidc.clientId" to idp.clientId, "ktor.auth.oidc.name" to "Fake")
        testApp(provider + extraConfig) { block(idp) }
    } finally {
        idp.stop()
    }
}

private suspend fun TestApp.oidcSignIn(code: String = "code-1", verifier: String = "verifier-1"): HttpResponse =
    anonymous.postJson("/api/v2/auth/oidc", """{"code":"$code","codeVerifier":"$verifier","redirectUri":"$REDIRECT"}""")

private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

private suspend fun HttpResponse.user() = json().jsonObject["user"]!!.jsonObject

/** No local admin: the first verified provider sign-in with ADMIN_EMAIL becomes admin. */
private val PROVIDER_ADMIN = mapOf("ktor.auth.adminEmail" to "Boss@Test.local", "ktor.auth.adminPassword" to "")

private val LOCAL_OFF = mapOf("ktor.auth.local" to "false")

class OidcAuthTest : FunSpec({
    beforeTest { Pg.truncateAll() }

    suspend fun TestApp.userCount() = client.get("/api/v2/users").json().jsonArray.size

    test("config lists the enabled sign-in methods") {
        testApp {
            val config = anonymous.get("/api/v2/auth/config").json().jsonObject
            config.str("local") shouldBe "true"
            (config["oidc"] ?: JsonNull) shouldBe JsonNull
        }
        oidcApp {
            val config = anonymous.get("/api/v2/auth/config").json().jsonObject
            config.str("local") shouldBe "true"
            config["oidc"]!!.jsonObject.str("name") shouldBe "Fake"
        }
        oidcApp(LOCAL_OFF) {
            val config = anonymous.get("/api/v2/auth/config").json().jsonObject
            config.str("local") shouldBe "false"
            config["oidc"]!!.jsonObject.str("name") shouldBe "Fake"
        }
    }

    test("start returns the provider URL with state and an S256 challenge of the verifier") {
        oidcApp { idp ->
            val start = anonymous.postJson("/api/v2/auth/oidc/start", """{"redirectUri":"$REDIRECT"}""").json().jsonObject
            val url = Url(start.str("authorizationUrl"))
            "${url.protocol.name}://${url.host}:${url.port}${url.encodedPath}" shouldBe "${idp.issuer}/authorize"
            val p = url.parameters
            p["response_type"] shouldBe "code"
            p["client_id"] shouldBe idp.clientId
            p["redirect_uri"] shouldBe REDIRECT
            p["scope"] shouldBe "openid email profile"
            p["state"] shouldBe start.str("state")
            p["code_challenge_method"] shouldBe "S256"
            val sha = MessageDigest.getInstance("SHA-256").digest(start.str("codeVerifier").toByteArray())
            p["code_challenge"] shouldBe Base64.getUrlEncoder().withoutPadding().encodeToString(sha)
        }
    }

    test("first sign-in creates a viewer without password, the next one finds the same account") {
        oidcApp { idp ->
            val first = oidcSignIn().json().jsonObject
            val user = first["user"]!!.jsonObject
            user.str("email") shouldBe "alice@test.local"
            user.str("name") shouldBe "Alice"
            user.str("role") shouldBe "viewer"
            user.str("hasPassword") shouldBe "false"
            idp.lastTokenRequest shouldBe mapOf(
                "grant_type" to "authorization_code", "code" to "code-1", "redirect_uri" to REDIRECT,
                "code_verifier" to "verifier-1", "client_id" to idp.clientId,
            )

            val alice = withToken(first.str("token"))
            alice.get("/api/v2/me").json().jsonObject.str("id") shouldBe user.str("id")
            oidcSignIn("code-2").json().jsonObject["user"]!!.jsonObject.str("id") shouldBe user.str("id")
            userCount() shouldBe 2

            alice.post("/api/v2/auth/logout").status shouldBe HttpStatusCode.NoContent
            alice.get("/api/v2/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("without a provider start and exchange are 404") {
        testApp {
            anonymous.postJson("/api/v2/auth/oidc/start", """{"redirectUri":"$REDIRECT"}""").status shouldBe HttpStatusCode.NotFound
            oidcSignIn().status shouldBe HttpStatusCode.NotFound
        }
    }

    test("an unreachable provider is 503") {
        oidcApp { idp ->
            idp.stop()
            anonymous.postJson("/api/v2/auth/oidc/start", """{"redirectUri":"$REDIRECT"}""").status shouldBe HttpStatusCode.ServiceUnavailable
            oidcSignIn().status shouldBe HttpStatusCode.ServiceUnavailable
            client.get("/api/v2/me").status shouldBe HttpStatusCode.OK // password sign-in unaffected
        }
    }

    test("broken ID tokens, refused codes and missing emails are 401 and create no user") {
        val cases: Map<String, FakeOidcProvider.() -> Unit> = mapOf(
            "signed with another key" to { signWithOtherKey = true },
            "wrong issuer" to { tokenIssuer = "https://evil.example" },
            "wrong audience" to { audience = "someone-else" },
            "expired" to { expiresAt = Instant.now().minusSeconds(3600) },
            "code refused" to { failToken = true },
            "userinfo for another user" to { claims = mapOf("sub" to "alice-sub"); userinfoClaims = user("mallory-sub", "alice@test.local") },
            "no email anywhere" to { claims = mapOf("sub" to "alice-sub"); userinfoClaims = mapOf("sub" to "alice-sub") },
        )
        // one app per case: start and exchange share the 5/min login limit
        for ((case, breakIt) in cases) oidcApp { idp ->
            idp.breakIt()
            withClue(case) {
                oidcSignIn().status shouldBe HttpStatusCode.Unauthorized
                userCount() shouldBe 1
            }
        }
    }

    test("verified ADMIN_EMAIL becomes admin in any letter case, unverified stays viewer") {
        oidcApp(PROVIDER_ADMIN) { idp ->
            idp.claims = user("boss-sub", "boss@test.local", verified = false)
            oidcSignIn().user().str("role") shouldBe "viewer"
        }
        Pg.truncateAll()
        oidcApp(PROVIDER_ADMIN) { idp ->
            idp.claims = user("boss-sub", "BOSS@test.local")
            oidcSignIn().user().str("role") shouldBe "admin"
        }
    }

    test("a local account is linked by verified email and keeps id, role and password") {
        oidcApp { idp ->
            val id = client.postJson("/api/v2/users", """{"email":"Alice@test.local","role":"editor","password":"alice-password"}""")
                .json().jsonObject.str("id")
            idp.claims = user("alice-sub", "alice@test.local", verified = false)
            oidcSignIn().status shouldBe HttpStatusCode.Unauthorized

            idp.claims = user("alice-sub", "alice@test.local")
            val linked = oidcSignIn().user()
            linked.str("id") shouldBe id
            linked.str("role") shouldBe "editor"
            linked.str("hasPassword") shouldBe "true"
            login("alice@test.local", "alice-password").get("/api/v2/me").status shouldBe HttpStatusCode.OK
        }
    }

    test("an account linked to another identity is not relinked") {
        oidcApp { idp ->
            oidcSignIn().status shouldBe HttpStatusCode.OK
            idp.claims = user("other-sub", "alice@test.local")
            oidcSignIn("code-2").status shouldBe HttpStatusCode.Unauthorized
            userCount() shouldBe 2
        }
    }

    test("roles are managed in the app and survive the next sign-in") {
        oidcApp {
            val id = oidcSignIn().user().str("id")
            client.putJson("/api/v2/users/$id", """{"role":"editor"}""").status shouldBe HttpStatusCode.OK
            oidcSignIn("code-2").user().str("role") shouldBe "editor"
        }
    }

    test("the only admin cannot be demoted or deleted when it signs in through the provider") {
        oidcApp(PROVIDER_ADMIN) { idp ->
            idp.claims = user("boss-sub", "boss@test.local")
            val answer = oidcSignIn().json().jsonObject
            val id = answer["user"]!!.jsonObject.str("id")
            val boss = withToken(answer.str("token"))
            boss.putJson("/api/v2/users/$id", """{"role":"editor"}""").status shouldBe HttpStatusCode.Conflict
            boss.delete("/api/v2/users/$id").status shouldBe HttpStatusCode.Conflict
        }
    }

    test("deleting a provider user ends their session; the next sign-in is a new viewer") {
        oidcApp {
            val answer = oidcSignIn().json().jsonObject
            val id = answer["user"]!!.jsonObject.str("id")
            client.delete("/api/v2/users/$id").status shouldBe HttpStatusCode.NoContent
            withToken(answer.str("token")).get("/api/v2/me").status shouldBe HttpStatusCode.Unauthorized
            val again = oidcSignIn("code-2").user()
            again.str("id") shouldNotBe id
            again.str("role") shouldBe "viewer"
        }
    }

    test("no admin password, no bootstrap admin") {
        Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password).use { db ->
            AuthService(db).bootstrapAdmin("boss@test.local", "") shouldBe false
            db.tx { UserRepository.count() } shouldBe 0L
        }
    }

    test("with local sign-in off password sign-in and password change are 403") {
        oidcApp(LOCAL_OFF) {
            anonymous.postJson("/api/v2/auth/login", """{"email":"$ADMIN_EMAIL","password":"$ADMIN_PASSWORD"}""").status shouldBe
                    HttpStatusCode.Forbidden
            val alice = withToken(oidcSignIn().json().jsonObject.str("token"))
            alice.putJson("/api/v2/me/password", """{"current":"whatever-1","new":"long-enough-1"}""").status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("a provider-only user has no password to change") {
        oidcApp {
            val alice = withToken(oidcSignIn().json().jsonObject.str("token"))
            alice.putJson("/api/v2/me/password", """{"current":"whatever-1","new":"long-enough-1"}""").status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("local sign-in off without a provider refuses to start") {
        shouldThrowAny { testApp(LOCAL_OFF) {} }.message shouldContain "OIDC_ISSUER"
    }

    test("devices keep their source tokens when only the provider signs users in") {
        oidcApp(PROVIDER_ADMIN + LOCAL_OFF) { idp ->
            idp.claims = user("boss-sub", "boss@test.local")
            val session = oidcSignIn().json().jsonObject.str("token")
            val source = withToken(session).climateSource()
            val payload = """{"deviceId":"pico-1","timestamp":"2099-01-01T00:00:00Z","temperature":21.5}"""
            anonymous.postJson("/api/v2/ingest/climate", payload, source).status shouldBe HttpStatusCode.OK
            anonymous.postJson("/api/v2/ingest/climate", payload, session).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("email and name come from userinfo when the ID token has none") {
        oidcApp { idp ->
            idp.claims = mapOf("sub" to "bob-sub")
            idp.userinfoClaims = user("bob-sub", "bob@test.local", name = "Bob")
            val user = oidcSignIn().json().jsonObject["user"]!!.jsonObject
            user.str("email") shouldBe "bob@test.local"
            user.str("name") shouldBe "Bob"
        }
    }
})
