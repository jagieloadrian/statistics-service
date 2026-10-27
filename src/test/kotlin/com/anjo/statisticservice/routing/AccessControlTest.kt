package com.anjo.statisticservice.routing

import com.anjo.statisticservice.auth.EDITOR
import com.anjo.statisticservice.auth.VIEWER
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.assertions.withClue
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** Role × endpoint matrix from data-model.md. */
class AccessControlTest {
    @BeforeEach
    fun clean() = Pg.truncateAll()

    private fun dashboard(id: String, visibility: String, dataset: String) =
        """{"id":"$id","visibility":"$visibility","widgets":[{"id":"w","type":"stat","query":{"dataset":"$dataset","measures":[{"field":"temperature"}]}}]}"""

    private fun query(dataset: String) = """{"dataset":"$dataset","measures":[{"field":"temperature"}],"bucket":"none"}"""

    private suspend fun HttpClient.ids(path: String) = get(path).json().jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private infix fun HttpResponse.expect(code: HttpStatusCode) = withClue("${call.request.method.value} ${call.request.url.encodedPath}") { status shouldBe code }

    @Test
    fun `roles and dashboard visibility`() = testApp {
        client.postJson("/api/v2/import", File("definitions/temperature.json").readText()) expect OK // dashboard 'temperature', visibility all
        client.putJson("/api/v2/datasets/secret", """{"id":"secret","measures":[{"name":"temperature"}]}""") expect OK
        val editor = userClient("editor@test.local", EDITOR)
        val viewer = userClient("viewer@test.local", VIEWER)
        val viewerId = client.get("/api/v2/users").json().jsonArray.first { it.jsonObject["email"]!!.jsonPrimitive.content == "viewer@test.local" }
            .jsonObject["id"]!!.jsonPrimitive.content

        // anonymous: nothing but login and ingest
        anonymous.get("/api/v2/dashboards") expect Unauthorized
        anonymous.postJson("/api/v2/query", query("temperature")) expect Unauthorized
        anonymous.get("/api/v2/export") expect Unauthorized

        // editor: own dashboards and sharing, no sources/datasets writes/import/users admin
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
        editor.get("/api/v2/export").json().jsonObject["sources"]!!.jsonArray.size shouldBe 0

        // viewer: only shared/all dashboards and their datasets, no writes
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

        // admin: everything, all dashboards
        client.ids("/api/v2/dashboards") shouldBe listOf("ed-private", "ed-shared", "temperature")
        client.putJson("/api/v2/dashboards/ed-private", dashboard("ed-private", "private", "secret")) expect OK
        client.get("/api/v2/sources/temperature/status") expect OK
        client.postJson("/api/v2/query", query("nope")) expect BadRequest
    }
}
