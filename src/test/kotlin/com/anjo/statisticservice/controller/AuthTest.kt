package com.anjo.statisticservice.controller

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.service.Passwords
import com.anjo.statisticservice.testutils.ADMIN_EMAIL
import com.anjo.statisticservice.testutils.ADMIN_PASSWORD
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthTest : FunSpec({
    beforeTest { Pg.truncateAll() }

    fun creds(email: String, password: String) = """{"email":"$email","password":"$password"}"""

    test("pbkdf2 hash and verify") {
        val h = Passwords.hash("correct horse")
        h shouldStartWith "pbkdf2\$600000\$"
        Passwords.verify("correct horse", h) shouldBe true
        Passwords.verify("wrong horse", h) shouldBe false
        Passwords.verify("correct horse", null) shouldBe false
        (Passwords.hash("correct horse") == h) shouldBe false // salted
    }

    test("login answers one generic 401 and the 6th attempt in a minute is 429") {
        testApp {
            // testApp already signed the admin in once
            val wrongPassword = anonymous.postJson("/api/v2/auth/login", creds(ADMIN_EMAIL, "not-the-password"))
            val unknownEmail = anonymous.postJson("/api/v2/auth/login", creds("nobody@test.local", "not-the-password"))
            wrongPassword.status shouldBe HttpStatusCode.Unauthorized
            unknownEmail.status shouldBe HttpStatusCode.Unauthorized
            wrongPassword.json() shouldBe unknownEmail.json()
            anonymous.postJson("/api/v2/auth/login", creds(ADMIN_EMAIL, ADMIN_PASSWORD)).json().jsonObject["user"]!!
                .jsonObject["role"]!!.jsonPrimitive.content shouldBe "admin"
            anonymous.postJson("/api/v2/auth/login", creds(ADMIN_EMAIL, "x")).status shouldBe HttpStatusCode.Unauthorized
            anonymous.postJson("/api/v2/auth/login", creds(ADMIN_EMAIL, ADMIN_PASSWORD)).status shouldBe HttpStatusCode.TooManyRequests
        }
    }

    test("no token or a bad one gives 401") {
        testApp {
            anonymous.get("/api/v2/me").status shouldBe HttpStatusCode.Unauthorized
            anonymous.get("/api/v2/me") { headers.append("Authorization", "Bearer nope") }.status shouldBe HttpStatusCode.Unauthorized
            client.get("/api/v2/me").json().jsonObject["email"]!!.jsonPrimitive.content shouldBe ADMIN_EMAIL
        }
    }

    test("logout revokes the token and idle tokens expire after 7 days") {
        testApp {
            val viewer = userClient("v@test.local", VIEWER)
            viewer.post("/api/v2/auth/logout").status shouldBe HttpStatusCode.NoContent
            viewer.get("/api/v2/me").status shouldBe HttpStatusCode.Unauthorized

            val editor = userClient("e@test.local", EDITOR)
            editor.get("/api/v2/me").status shouldBe HttpStatusCode.OK
            Pg.sql("UPDATE auth_tokens SET last_seen = now() - interval '8 days' WHERE user_id = (SELECT id FROM users WHERE email = 'e@test.local')")
            editor.get("/api/v2/me").status shouldBe HttpStatusCode.Unauthorized
            client.get("/api/v2/me").status shouldBe HttpStatusCode.OK
        }
    }

    test("password change keeps the current session and signs out the others") {
        testApp {
            val a = userClient("e@test.local", EDITOR, "first-password")
            val b = login("e@test.local", "first-password")
            a.putJson("/api/v2/me/password", """{"current":"wrong-password","new":"second-password"}""").status shouldBe HttpStatusCode.BadRequest
            a.putJson("/api/v2/me/password", """{"current":"first-password","new":"short"}""").status shouldBe HttpStatusCode.BadRequest
            a.putJson("/api/v2/me/password", """{"current":"first-password","new":"second-password"}""").status shouldBe HttpStatusCode.NoContent
            a.get("/api/v2/me").status shouldBe HttpStatusCode.OK
            b.get("/api/v2/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("users are validated and prefs saved") {
        testApp {
            client.postJson("/api/v2/users", """{"email":"x@test.local","role":"viewer","password":"short"}""").status shouldBe HttpStatusCode.BadRequest
            client.postJson("/api/v2/users", """{"email":"x@test.local","role":"boss","password":"long-enough-1"}""").status shouldBe HttpStatusCode.BadRequest
            client.postJson("/api/v2/users", """{"email":"X@test.local","role":"viewer","password":"long-enough-1"}""").status shouldBe HttpStatusCode.Created
            client.postJson("/api/v2/users", """{"email":"x@test.local","role":"viewer","password":"long-enough-1"}""").status shouldBe HttpStatusCode.Conflict

            val prefs = """{"timeZone":"Europe/Warsaw","units":"imperial","theme":"dark","homeDashboard":"temperature","favourites":["temperature"]}"""
            client.putJson("/api/v2/me/prefs", prefs).json().jsonObject["prefs"].toString() shouldBe prefs
            client.putJson("/api/v2/me/prefs", """{"timeZone":"Mars/Base"}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("last admin cannot be demoted or deleted") {
        testApp {
            val me = client.get("/api/v2/me").json().jsonObject["id"]!!.jsonPrimitive.content
            client.putJson("/api/v2/users/$me", """{"role":"editor"}""").status shouldBe HttpStatusCode.Conflict
            client.delete("/api/v2/users/$me").status shouldBe HttpStatusCode.Conflict

            client.postJson("/api/v2/users", """{"email":"a2@test.local","role":"admin","password":"long-enough-1"}""")
            client.putJson("/api/v2/users/$me", """{"role":"editor"}""").status shouldBe HttpStatusCode.OK
            client.get("/api/v2/users").status shouldBe HttpStatusCode.OK // editors may list users for sharing
            client.postJson("/api/v2/users", """{"email":"z@test.local","role":"viewer","password":"long-enough-1"}""").status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("bootstrap admin only while there are no users") {
        testApp {
            Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password).use { db ->
                AuthService(db).bootstrapAdmin("second@test.local", "long-enough-1") shouldBe false
            }
            client.get("/api/v2/users").json().jsonArray.map { it.jsonObject["email"]!!.jsonPrimitive.content } shouldBe listOf(ADMIN_EMAIL)
        }
    }
})
