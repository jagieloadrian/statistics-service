package com.anjo.statisticservice.validation

import com.anjo.statisticservice.controller.configureStatusPages
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.definitionJson
import com.anjo.statisticservice.model.dto.LoginRequest
import com.anjo.statisticservice.model.dto.NewUser
import com.anjo.statisticservice.model.dto.OidcLoginRequest
import com.anjo.statisticservice.model.dto.OidcStartRequest
import com.anjo.statisticservice.model.dto.PasswordChange
import com.anjo.statisticservice.model.dto.Shares
import com.anjo.statisticservice.model.dto.UserUpdate
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private inline fun <reified T : Any> Route.accept(path: String) {
    post(path) {
        call.receive<T>()
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun rules(block: suspend HttpClient.() -> Unit) =
    testApplication {
        application {
            install(ContentNegotiation) { json(definitionJson) }
            configureStatusPages()
            configureRequestValidation()
            routing {
                accept<LoginRequest>("/login")
                accept<OidcStartRequest>("/oidc/start")
                accept<OidcLoginRequest>("/oidc/login")
                accept<PasswordChange>("/password")
                accept<NewUser>("/users")
                accept<UserUpdate>("/user")
                accept<Prefs>("/prefs")
                accept<Shares>("/shares")
                accept<Query>("/query")
            }
        }
        client.block()
    }

private suspend fun HttpClient.reasons(
    path: String,
    body: String,
): List<String> {
    val response = postJson(path, body)
    if (response.status == HttpStatusCode.NoContent) return emptyList()
    response.status shouldBe HttpStatusCode.BadRequest
    return response
        .json()
        .jsonObject
        .getValue("details")
        .jsonArray
        .map { it.jsonPrimitive.content }
}

private fun long(n: Int) = "x".repeat(n)

class RequestRulesTest :
    FunSpec({
        test("sign-in requests must fit their length limits") {
            rules {
                reasons("/login", """{"email":"a@b.c","password":"x"}""") shouldBe emptyList()
                reasons("/login", """{"email":"${long(255)}","password":"${long(1025)}"}""") shouldBe
                    listOf("email: required, at most 254 characters", "password: required, at most 1024 characters")
                reasons("/oidc/start", """{"redirectUri":"https://app.example.com/cb"}""") shouldBe emptyList()
                reasons("/oidc/start", """{"redirectUri":"https://app.example.com/${long(2048)}"}""") shouldBe
                    listOf("redirectUri: absolute URI of at most 2048 characters")
                reasons("/oidc/start", """{"redirectUri":"https://bad host/cb"}""") shouldBe
                    listOf("redirectUri: absolute URI of at most 2048 characters")
                reasons("/oidc/login", """{"code":"c","codeVerifier":"v","redirectUri":"https://app/cb"}""") shouldBe emptyList()
                reasons("/oidc/login", """{"code":" ","codeVerifier":"","redirectUri":"cb"}""") shouldBe
                    listOf(
                        "code: required, at most 2048 characters",
                        "codeVerifier: required, at most 128 characters",
                        "redirectUri: absolute URI of at most 2048 characters",
                    )
                reasons("/oidc/login", """{"code":"${long(2049)}","codeVerifier":"${long(129)}","redirectUri":"https://app/cb"}""") shouldBe
                    listOf("code: required, at most 2048 characters", "codeVerifier: required, at most 128 characters")
            }
        }

        test("passwords and users are checked field by field") {
            rules {
                reasons("/password", """{"current":"old","new":"0123456789"}""") shouldBe emptyList()
                reasons("/password", """{"current":"","new":"${long(1025)}"}""") shouldBe
                    listOf("current: required", "password: at least 10 characters, at most 1024")
                reasons("/users", """{"email":"a@b.c","role":"viewer","password":"0123456789"}""") shouldBe emptyList()
                reasons("/users", """{"email":"${long(250)}@b.cd","name":"${long(201)}","role":"admin","password":"0123456789"}""") shouldBe
                    listOf("email: not an email address", "name: at most 200 characters")
                reasons("/user", "{}") shouldBe emptyList()
                reasons("/user", """{"name":"Ann","role":"editor"}""") shouldBe emptyList()
                reasons("/user", """{"name":"${long(201)}","role":"boss"}""") shouldBe
                    listOf("name: at most 200 characters", "role: admin | editor | viewer")
            }
        }

        test("preferences need a known time zone and short dashboard ids") {
            rules {
                reasons("/prefs", "{}") shouldBe emptyList()
                reasons("/prefs", """{"timeZone":"Europe/Warsaw","homeDashboard":"home","favourites":["home"]}""") shouldBe emptyList()
                val favourites = List(201) { "\"${long(64)}\"" }.joinToString(",")
                reasons("/prefs", """{"timeZone":"Mars/Base","homeDashboard":"${long(64)}","favourites":[$favourites]}""") shouldBe
                    listOf(
                        "timeZone: unknown zone 'Mars/Base'",
                        "homeDashboard: at most 63 characters",
                        "favourites: at most 200",
                        "favourites: ids of at most 63 characters",
                    )
                reasons("/shares", """{"userIds":[1,2]}""") shouldBe emptyList()
                reasons("/shares", """{"userIds":[1,-2]}""") shouldBe listOf("userIds: must be positive")
            }
        }

        test("queries need a dataset, a sane point budget and a readable time range") {
            rules {
                reasons("/query", """{"dataset":"d","measures":[{"field":"x"}],"range":{"last":"PT1H"}}""") shouldBe emptyList()
                reasons("/query", """{"dataset":" ","measures":[{"field":"x"}],"maxPoints":0,"from":"yesterday","to":"today"}""") shouldBe
                    listOf(
                        "dataset: required",
                        "maxPoints: 1..10000",
                        "from/to must be ISO-8601 instants, range.last an ISO-8601 duration",
                    )
                reasons("/query", """{"dataset":"d","measures":[{"field":"x"}],"maxPoints":10001,"range":{"last":"forever"}}""") shouldBe
                    listOf("maxPoints: 1..10000", "from/to must be ISO-8601 instants, range.last an ISO-8601 duration")
            }
        }
    })
