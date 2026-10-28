package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class AccessControlTest : FunSpec({
    beforeTest { Pg.truncateAll() }

    fun dashboard(id: String, visibility: String, dataset: String) =
        """{"id":"$id","visibility":"$visibility","widgets":[{"id":"w","type":"stat","query":{"dataset":"$dataset","measures":[{"field":"temperature"}]}}]}"""

    fun query(dataset: String) = """{"dataset":"$dataset","measures":[{"field":"temperature"}],"bucket":"none"}"""

    suspend fun HttpClient.ids(path: String) = get(path).json().jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

    infix fun HttpResponse.expect(code: HttpStatusCode) = withClue("${call.request.method.value} ${call.request.url.encodedPath}") { status shouldBe code }

    test("roles and dashboard visibility") {
        testApp {
            client.postJson("/api/v2/import", File("definitions/temperature.json").readText()) expect OK
            client.putJson("/api/v2/datasets/secret", """{"id":"secret","measures":[{"name":"temperature"}]}""") expect OK
            val editor = userClient("editor@test.local", EDITOR)
            val viewer = userClient("viewer@test.local", VIEWER)
            val viewerId = client.get("/api/v2/users").json().jsonArray.first { it.jsonObject.getValue("email").jsonPrimitive.content == "viewer@test.local" }
                .jsonObject.getValue("id").jsonPrimitive.content

            anonymous.get("/api/v2/dashboards") expect Unauthorized
            anonymous.postJson("/api/v2/query", query("temperature")) expect Unauthorized
            anonymous.get("/api/v2/export") expect Unauthorized

            editor.putJson("/api/v2/dashboards/ed-private", dashboard("ed-private", "private", "secret")) expect OK
            editor.putJson("/api/v2/dashboards/ed-shared", dashboard("ed-shared", "users", "temperature")) expect OK
            editor.putJson("/api/v2/dashboards/ed-shared/shares", """{"userIds":[$viewerId]}""") expect OK
            editor.putJson("/api/v2/dashboards/temperature", dashboard("temperature", "all", "temperature")) expect Forbidden
            editor.delete("/api/v2/dashboards/temperature") expect Forbidden
            editor.get("/api/v2/sources") expect Forbidden
            editor.get("/api/v2/sources/temperature/status") expect Forbidden
            editor.putJson("/api/v2/datasets/secret", """{"id":"secret"}""") expect Forbidden
            editor.postJson("/api/v2/import", "{}") expect Forbidden
            editor.postJson("/api/v2/users", """{"email":"x@test.local","role":"viewer","password":"long-enough-1"}""") expect Forbidden
            editor.get("/api/v2/datasets") expect OK
            editor.postJson("/api/v2/query", query("secret")) expect OK
            editor.ids("/api/v2/dashboards") shouldBe listOf("ed-private", "ed-shared", "temperature")
            editor.get("/api/v2/export").json().jsonObject.getValue("sources").jsonArray.size shouldBe 0

            viewer.ids("/api/v2/dashboards") shouldBe listOf("ed-shared", "temperature")
            viewer.get("/api/v2/dashboards/ed-private") expect Forbidden
            viewer.get("/api/v2/dashboards/ed-shared") expect OK
            viewer.putJson("/api/v2/dashboards/ed-shared", dashboard("ed-shared", "users", "temperature")) expect Forbidden
            viewer.putJson("/api/v2/dashboards/ed-shared/shares", """{"userIds":[]}""") expect Forbidden
            viewer.putJson("/api/v2/dashboards/new", dashboard("new", "private", "temperature")) expect Forbidden
            viewer.postJson("/api/v2/query", query("temperature")) expect OK
            viewer.postJson("/api/v2/query", query("secret")) expect Forbidden
            viewer.get("/api/v2/datasets/temperature/dimensions/device/values") expect OK
            viewer.get("/api/v2/datasets/secret/dimensions/device/values") expect Forbidden
            viewer.get("/api/v2/datasets/temperature") expect OK
            viewer.get("/api/v2/datasets") expect Forbidden
            listOf("/api/v2/sources", "/api/v2/sources/temperature", "/api/v2/sources/temperature/status", "/api/v2/users", "/api/v2/export")
                .forEach { viewer.get(it) expect Forbidden }
            viewer.postJson("/api/v2/sources/temperature/credential", "") expect Forbidden
            viewer.postJson("/api/v2/import", "{}") expect Forbidden

            client.ids("/api/v2/dashboards") shouldBe listOf("ed-private", "ed-shared", "temperature")
            client.putJson("/api/v2/dashboards/ed-private", dashboard("ed-private", "private", "secret")) expect OK
            client.get("/api/v2/sources/temperature/status") expect OK
            client.postJson("/api/v2/query", query("nope")) expect BadRequest
        }
    }
})
