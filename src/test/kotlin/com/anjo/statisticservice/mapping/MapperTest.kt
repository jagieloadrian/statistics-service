package com.anjo.statisticservice.mapping

import com.anjo.statisticservice.definitions.Bundle
import com.anjo.statisticservice.definitions.Dataset
import com.anjo.statisticservice.definitions.Dimension
import com.anjo.statisticservice.definitions.FieldSpec
import com.anjo.statisticservice.definitions.Measure
import com.anjo.statisticservice.definitions.MeasureType
import com.anjo.statisticservice.definitions.Rule
import com.anjo.statisticservice.definitions.Source
import com.anjo.statisticservice.definitions.TimestampSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class MapperTest {
    private val now = Instant.parse("2026-10-27T10:00:00Z")
    private val ds = Dataset(
        id = "climate",
        measures = listOf(
            Measure("temperature"), Measure("humidity"),
            Measure("heating", MeasureType.boolean), Measure("note", MeasureType.text),
        ),
        dimensions = listOf(Dimension("device")),
    )
    private val source = Source(
        id = "climate", dataset = "climate",
        mapping = listOf(
            Rule(
                timestamp = TimestampSpec("timestamp", maxAgeHours = 24),
                identity = listOf("deviceId", "timestamp"),
                dims = mapOf("device" to FieldSpec(path = "deviceId")),
                vals = mapOf(
                    "temperature" to FieldSpec(path = "temperature", min = -50.0, max = 80.0),
                    "humidity" to FieldSpec(path = "humidity", required = false, scale = 100.0),
                    "heating" to FieldSpec(path = "heating", required = false),
                    "note" to FieldSpec(path = "note", required = false),
                ),
            ),
        ),
    )

    private fun map(json: String, src: Source = source) = Mapper.map(src, ds, Json.parseToJsonElement(json), now)
    private fun reasons(json: String, src: Source = source) = assertThrows<MappingException> { map(json, src) }.reasons

    @Test
    fun `flat payload gives one cast record and ignores extra fields`() {
        val r = map("""{"deviceId":"pico-1","timestamp":"2026-10-27T09:30:00Z","temperature":"21.5","humidity":0.4,"heating":"true","note":"ok","extra":1}""").single()
        r.ts shouldBe Instant.parse("2026-10-27T09:30:00Z")
        r.tsEstimated shouldBe false
        r.dims shouldBe mapOf("device" to "pico-1")
        r.vals shouldBe mapOf(
            "temperature" to JsonPrimitive(21.5), "humidity" to JsonPrimitive(40.0),
            "heating" to JsonPrimitive(true), "note" to JsonPrimitive("ok"),
        )
        r.ident shouldBe "pico-1|2026-10-27T09:30:00Z"
    }

    @Test
    fun `local date-time is read as UTC and missing timestamp uses arrival time`() {
        map("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00","temperature":1}""").single().ts shouldBe Instant.parse("2026-10-27T09:00:00Z")
        val r = map("""{"deviceId":"a","temperature":1}""", source.copy(mapping = listOf(source.mapping[0].copy(identity = emptyList())))).single()
        r.ts shouldBe now
        r.tsEstimated shouldBe true
        r.ident shouldBe null
    }

    @Test
    fun `epoch formats`() {
        val ms = source.copy(mapping = listOf(source.mapping[0].copy(timestamp = TimestampSpec("timestamp", "epoch_ms"))))
        val s = source.copy(mapping = listOf(source.mapping[0].copy(timestamp = TimestampSpec("timestamp", "epoch_s"))))
        map("""{"deviceId":"a","timestamp":1793091600000,"temperature":1}""", ms).single().ts shouldBe Instant.fromEpochSeconds(1793091600)
        map("""{"deviceId":"a","timestamp":1793091600,"temperature":1}""", s).single().ts shouldBe Instant.fromEpochSeconds(1793091600)
    }

    @Test
    fun `each failed constraint gives one reason`() {
        reasons("""{"deviceId":" ","timestamp":"2026-10-20T00:00:00Z","temperature":99,"heating":"maybe"}""") shouldContainExactlyInAnyOrder listOf(
            "dims.device: must not be blank",
            "timestamp: older than 24 h",
            "vals.temperature: 99.0 is above max 80.0",
            "vals.heating: expected a boolean, got 'maybe'",
        )
        reasons("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00Z","temperature":-60}""") shouldBe listOf("vals.temperature: -60.0 is below min -50.0")
        reasons("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00Z"}""") shouldBe listOf("vals.temperature: missing at path 'temperature'")
        reasons("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00Z","temperature":"warm"}""") shouldBe listOf("vals.temperature: expected a number, got 'warm'")
    }

    @Test
    fun `each iterates object entries with key and item paths plus const dims`() {
        val epiDs = Dataset(id = "epi", measures = listOf(Measure("infected")), dimensions = listOf(Dimension("run"), Dimension("type")))
        val epi = Source(
            id = "epi", dataset = "epi",
            mapping = listOf(
                Rule(dims = mapOf("run" to FieldSpec(path = "meta.runId")), vals = mapOf("infected" to FieldSpec(path = "state.infected")), constDims = mapOf("type" to "total")),
                Rule(each = "state.byType", dims = mapOf("run" to FieldSpec(path = "meta.runId"), "type" to FieldSpec(path = "@key")), vals = mapOf("infected" to FieldSpec(path = "@.infected"))),
            ),
        )
        val recs = Mapper.map(epi, epiDs, Json.parseToJsonElement("""{"meta":{"runId":"r1"},"state":{"infected":5,"byType":{"0":{"infected":2},"1":{"infected":3}}}}"""), now)
        recs.map { it.dims["type"] to it.vals["infected"] } shouldBe listOf("total" to JsonPrimitive(5.0), "0" to JsonPrimitive(2.0), "1" to JsonPrimitive(3.0))
        recs.map { it.dims["run"] }.distinct() shouldBe listOf("r1")
    }

    @Test
    fun `native format accepts records and rejects undeclared keys`() {
        val native = source.copy(mapping = emptyList())
        val recs = map("""[{"ts":"2026-10-27T09:00:00Z","dims":{"device":"x"},"vals":{"temperature":20}},{"dims":{"device":"y"},"vals":{"temperature":21}}]""", native)
        recs.size shouldBe 2
        recs[0].ts shouldBe Instant.parse("2026-10-27T09:00:00Z")
        recs[1].tsEstimated shouldBe true
        map("""{"dims":{"device":"x"},"vals":{"temperature":20}}""", native).size shouldBe 1
        reasons("""{"dims":{"device":"x","room":"k"},"vals":{"pressure":1}}""", native) shouldContainExactlyInAnyOrder
            listOf("[0].dims.room: not declared in dataset", "[0].vals.pressure: not declared in dataset")
    }

    @Test
    fun `epidemic sample payload gives total plus one record per byType entry`() {
        val bundle = Json.decodeFromString(Bundle.serializer(), java.io.File("definitions/epidemic.json").readText())
        val payload = Json.parseToJsonElement(javaClass.getResource("/payloads/epidemic.json")!!.readText())
        val recs = Mapper.map(bundle.sources.single(), bundle.datasets.single(), payload, now)
        recs.map { it.dims["type"] } shouldBe listOf("total", "0", "1", "2")
        recs.map { it.ident } shouldBe listOf("RPI_Pico_2w_1|5|32", "RPI_Pico_2w_1|5|32|0", "RPI_Pico_2w_1|5|32|1", "RPI_Pico_2w_1|5|32|2")
        recs[0].vals["population"] shouldBe JsonPrimitive(263.0)
        recs[0].vals["lockdown"] shouldBe JsonPrimitive(false)
        recs[2].vals["dead"] shouldBe JsonPrimitive(1.0)
        recs.all { it.dims["run"] == "5" && it.ts == Instant.parse("2026-01-05T15:38:14Z") } shouldBe true
    }
}
