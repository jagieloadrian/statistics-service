package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.DbConfig
import com.anjo.statisticservice.model.OidcConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.MapApplicationConfig

class AppConfigLoaderTest :
    FunSpec({
        val db = arrayOf("ktor.db.url" to "jdbc:postgresql://db/stats", "ktor.db.user" to "stats")

        fun load(vararg settings: Pair<String, String>) = MapApplicationConfig(*db, *settings).toAppConfig()

        fun problems(vararg settings: Pair<String, String>) =
            shouldThrow<IllegalStateException> { MapApplicationConfig(*settings).toAppConfig() }.message.orEmpty()

        test("database settings alone give local sign-in without OIDC, CORS or proxy trust") {
            val config = load()
            config.db shouldBe DbConfig("jdbc:postgresql://db/stats", "stats", "")
            config.localSignIn shouldBe true
            config.oidc shouldBe null
            config.corsHosts shouldBe emptyList()
            config.trustProxy shouldBe false
            config.adminEmail shouldBe ""
        }

        test("every setting is read, the provider name defaults to SSO and CORS hosts are trimmed") {
            val config =
                load(
                    "ktor.auth.local" to "false",
                    "ktor.auth.adminEmail" to "root@example.com",
                    "ktor.auth.adminPassword" to "secret-password",
                    "ktor.auth.oidc.issuer" to "https://id.example.com/realms/home",
                    "ktor.auth.oidc.clientId" to "stats",
                    "ktor.auth.oidc.clientSecret" to "s3cret",
                    "ktor.auth.oidc.name" to " ",
                    "ktor.cors.hosts" to " ui.example.com, ,localhost:3000 ",
                    "ktor.deployment.trustProxy" to "true",
                )
            config.localSignIn shouldBe false
            config.adminEmail shouldBe "root@example.com"
            config.adminPassword shouldBe "secret-password"
            config.oidc shouldBe OidcConfig("https://id.example.com/realms/home", "stats", "s3cret", "SSO")
            config.corsHosts shouldBe listOf("ui.example.com", "localhost:3000")
            config.trustProxy shouldBe true
            load("ktor.auth.oidc.issuer" to "http://id.local", "ktor.auth.oidc.clientId" to "c", "ktor.auth.oidc.name" to "Keycloak")
                .oidc
                ?.name shouldBe "Keycloak"
        }

        test("all problems are reported together") {
            val message =
                problems(
                    "ktor.db.url" to "jdbc:mysql://db/stats",
                    "ktor.db.user" to " ",
                    "ktor.auth.local" to "yes",
                    "ktor.auth.oidc.issuer" to "https://id.example.com",
                    "ktor.deployment.trustProxy" to "1",
                )
            message shouldContain "DB_URL must be a jdbc:postgresql:// URL"
            message shouldContain "DB_USER is required"
            message shouldContain "AUTH_LOCAL must be true or false"
            message shouldContain "OIDC_ISSUER and OIDC_CLIENT_ID must be set together"
            message shouldContain "TRUST_PROXY must be true or false"
            problems(*db, "ktor.auth.oidc.clientId" to "stats") shouldContain "OIDC_ISSUER and OIDC_CLIENT_ID must be set together"
        }

        test("the issuer must be an absolute http(s) URL with a host") {
            listOf("ftp://id.example.com", "id.example.com/realm", "http://bad host", "https:///realm", "mailto:a@b.c").forEach { issuer ->
                withClue(issuer) {
                    problems(*db, "ktor.auth.oidc.issuer" to issuer, "ktor.auth.oidc.clientId" to "stats") shouldContain
                        "OIDC_ISSUER must be an absolute http(s) URL"
                }
            }
        }

        test("local sign-in off needs a provider") {
            problems(*db, "ktor.auth.local" to "false") shouldContain
                "Enable local sign-in (AUTH_LOCAL=true) or configure OIDC_ISSUER and OIDC_CLIENT_ID"
        }
    })
