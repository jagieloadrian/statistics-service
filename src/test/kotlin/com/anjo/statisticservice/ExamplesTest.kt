package com.anjo.statisticservice

import com.anjo.statisticservice.definitions.Bundle
import com.anjo.statisticservice.definitions.Dashboard
import com.anjo.statisticservice.definitions.Dataset
import com.anjo.statisticservice.definitions.Query
import com.anjo.statisticservice.definitions.Source
import com.anjo.statisticservice.definitions.definitionJson
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The shipped definition bundles work end to end with the device payloads (SC-002). */
class ExamplesTest {
    @BeforeEach
    fun clean() = Pg.truncateAll()

    /** PUTs every document of a bundle and returns the source token per source. */
    private suspend fun HttpClient.load(bundle: Bundle): Map<String, String> {
        bundle.datasets.forEach { putJson("/api/v2/datasets/${it.id}", definitionJson.encodeToString(Dataset.serializer(), it)).status shouldBe HttpStatusCode.OK }
        bundle.sources.forEach { putJson("/api/v2/sources/${it.id}", definitionJson.encodeToString(Source.serializer(), it)).status shouldBe HttpStatusCode.OK }
        bundle.dashboards.forEach { putJson("/api/v2/dashboards/${it.id}", definitionJson.encodeToString(Dashboard.serializer(), it)).status shouldBe HttpStatusCode.OK }
        return bundle.sources.associate { it.id to postJson("/api/v2/sources/${it.id}/credential", "").json().jsonObject["token"]!!.jsonPrimitive.content }
    }

    /** Runs every widget query the way the dashboard does: `$var` substituted, time range applied. */
    private suspend fun HttpClient.widgets(d: Dashboard, vars: Map<String, String>, from: Instant, to: Instant): Map<String, List<Pair<JsonObject, List<List<String>>>>> {
        fun sub(v: String) = if (v.startsWith("$")) vars.getValue(v.drop(1)) else v
        return d.widgets.associate { w ->
            val q = w.query.copy(filter = w.query.filter.mapValues { (_, vs) -> vs.map(::sub) }, bucket = sub(w.query.bucket), from = from.toString(), to = to.toString())
            val r = postJson("/api/v2/query", definitionJson.encodeToString(Query.serializer(), q))
            r.status shouldBe HttpStatusCode.OK
            w.id to r.json().jsonObject["series"]!!.jsonArray.map { s ->
                s.jsonObject["dims"]!!.jsonObject to s.jsonObject["rows"]!!.jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.content } }
            }
        }
    }

    private fun bundle(name: String) = definitionJson.decodeFromString(Bundle.serializer(), File("definitions/$name.json").readText())
    private fun payload(name: String) = Json.parseToJsonElement(javaClass.getResource("/payloads/$name.json")!!.readText()).jsonObject

    private fun JsonObject.with(path: String, v: Any): JsonObject {
        val head = path.substringBefore('.')
        val rest = path.substringAfter('.', "")
        val value: JsonElement = if (rest.isNotEmpty()) this[head]!!.jsonObject.with(rest, v)
        else when (v) { is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
        return JsonObject(this + (head to value))
    }

    @Test
    fun `temperature bundle`() = testApp {
        val b = bundle("temperature")
        val token = client.load(b).getValue("temperature")
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val t = listOf(30, 20, 10).map { now - it.minutes }
        val sample = payload("temperature")
        listOf(10.0 to 20.0, 12.0 to 30.0, 14.0 to 40.0).forEachIndexed { i, (temp, hum) ->
            client.postJson("/api/v2/ingest/temperature", sample.with("timestamp", t[i]).with("temperature", temp).with("humidity", hum).toString(), token)
                .status shouldBe HttpStatusCode.OK
        }
        client.postJson("/api/v2/ingest/temperature", sample.with("deviceId", "other").with("timestamp", t[0]).with("temperature", 50).toString(), token)

        val w = client.widgets(b.dashboards.single(), mapOf("device" to "RPI_Pico_2w_1", "bucket" to "raw"), now - 1.hours, now)
        w.getValue("devices").associate { (dims, rows) -> dims["device"]!!.jsonPrimitive.content to rows } shouldBe mapOf(
            "RPI_Pico_2w_1" to listOf(listOf(t[0].toString(), t[2].toString(), "14.0")),
            "other" to listOf(listOf(t[0].toString(), t[0].toString(), "50.0")),
        )
        w.getValue("climate").single().second shouldBe listOf(
            listOf(t[0].toString(), "10.0", "20.0"), listOf(t[1].toString(), "12.0", "30.0"), listOf(t[2].toString(), "14.0", "40.0"),
        )
        w.getValue("summary").single().second shouldBe listOf(listOf("10.0", "12.0", "14.0"))
    }

    @Test
    fun `epidemic bundle`() = testApp {
        val b = bundle("epidemic")
        val token = client.load(b).getValue("epidemic")
        val sample = payload("epidemic")
        // generation to (infected, recovered, dead)
        listOf(Triple(10, 0, 0), Triple(30, 5, 1), Triple(20, 40, 4)).forEachIndexed { i, (inf, rec, dead) ->
            val p = sample.with("meta.generation", i + 1).with("meta.timestamp", "2026-01-05T15:3${i}:00")
                .with("state.infected", inf).with("state.recovered", rec).with("state.dead", dead)
            client.postJson("/api/v2/ingest/epidemic", p.toString(), token).json().toString() shouldBe """{"accepted":4,"duplicates":0}"""
        }

        val w = client.widgets(b.dashboards.single(), mapOf("run" to "5"), Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-06T00:00:00Z"))
        val (runDims, runRows) = w.getValue("runs").single()
        runDims.toString() shouldBe """{"run":"5","device":"RPI_Pico_2w_1"}"""
        runRows shouldBe listOf(listOf("2026-01-05T15:30:00Z", "2026-01-05T15:32:00Z", "263.0", "3.0", "30.0"))
        w.getValue("timeline").single().second.map { it.take(3) } shouldBe listOf(
            listOf("2026-01-05T15:30:00Z", "1.0", "45.0"), listOf("2026-01-05T15:31:00Z", "2.0", "45.0"), listOf("2026-01-05T15:32:00Z", "3.0", "45.0"),
        )
        val summary = w.getValue("summary").single().second.single()
        summary.dropLast(1) shouldBe listOf("30.0", "2.0", "40.0", "4.0")
        summary.last().toDouble() shouldBe (44.0 / 263).plusOrMinus(1e-9)
    }
}
