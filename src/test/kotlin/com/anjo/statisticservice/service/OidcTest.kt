package com.anjo.statisticservice.service

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.OidcClaims
import com.anjo.statisticservice.model.OidcConfig
import com.anjo.statisticservice.testutils.FakeOidcProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

private const val REDIRECT = "http://127.0.0.1:51234/callback"

class OidcTest :
    FunSpec({
        val idp = FakeOidcProvider()

        afterSpec { idp.stop() }

        beforeTest {
            idp.claims = FakeOidcProvider.user("alice-sub", "alice@test.local", name = "Alice")
            idp.userinfoClaims = null
        }

        fun oidc(
            issuer: String = idp.issuer,
            secret: String = "",
        ) = Oidc(OidcConfig(issuer, idp.clientId, secret, "Fake"))

        suspend fun Oidc.redeem() = redeem("code-1", "verifier-1", REDIRECT)

        suspend fun failure(block: suspend () -> Unit) = shouldThrow<ApiException> { block() }

        test("a public client names itself in the form, a confidential one authenticates instead") {
            val public = oidc()
            public.name shouldBe "Fake"
            public.issuer shouldBe idp.issuer
            public.redeem() shouldBe OidcClaims("alice-sub", "alice@test.local", true, "Alice")
            idp.lastTokenRequest shouldContainKey "client_id"
            oidc(secret = "s3cret").redeem().subject shouldBe "alice-sub"
            idp.lastTokenRequest shouldNotContainKey "client_id"
            idp.lastTokenRequest["code_verifier"] shouldBe "verifier-1"
        }

        test("an ID token without a subject is invalid") {
            idp.claims = mapOf("email" to "alice@test.local")
            failure { oidc().redeem() }.message shouldBe "Invalid ID token"
        }

        test("userinfo fills in email and name only for the same subject") {
            idp.claims = FakeOidcProvider.user("alice-sub", null)
            idp.userinfoClaims = mapOf("sub" to "alice-sub", "email" to "alice@test.local", "name" to "Alice U")
            oidc().redeem() shouldBe OidcClaims("alice-sub", "alice@test.local", false, "Alice U")
            idp.claims = FakeOidcProvider.user("alice-sub", null, name = "Alice")
            idp.userinfoClaims = FakeOidcProvider.user("alice-sub", "alice@test.local", name = "Other")
            oidc().redeem() shouldBe OidcClaims("alice-sub", "alice@test.local", true, "Alice")
            idp.userinfoClaims = FakeOidcProvider.user("mallory-sub", "mallory@test.local")
            failure { oidc().redeem() }.message shouldBe "Provider answered for another user"
        }

        test("a discovery document that is missing or names another issuer makes the provider unavailable") {
            listOf("${idp.issuer}/", "${idp.issuer}/missing").forEach { issuer ->
                failure { oidc(issuer).start(REDIRECT) }.status shouldBe HttpStatusCode.ServiceUnavailable
            }
        }
    })
