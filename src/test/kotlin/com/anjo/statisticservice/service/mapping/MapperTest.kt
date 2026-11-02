package com.anjo.statisticservice.service.mapping

import com.anjo.statisticservice.exception.MappingException
import com.anjo.statisticservice.model.Bundle
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Dimension
import com.anjo.statisticservice.model.FieldSpec
import com.anjo.statisticservice.model.Measure
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.Rule
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.TimestampSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

class MapperTest :
    FunSpec({
        val now = Instant.parse("2026-10-27T10:00:00Z")
        val ds =
            Dataset(
                id = "climate",
                measures =
                    listOf(
                        Measure("temperature"),
                        Measure("humidity"),
                        Measure("heating", MeasureType.BOOLEAN),
                        Measure("note", MeasureType.TEXT),
                    ),
                dimensions = listOf(Dimension("device")),
            )
        val source =
            Source(
                id = "climate",
                dataset = "climate",
                mapping =
                    listOf(
                        Rule(
                            timestamp = TimestampSpec("timestamp", maxAgeHours = 24),
                            identity = listOf("deviceId", "timestamp"),
                            dims = mapOf("device" to FieldSpec(path = "deviceId")),
                            vals =
                                mapOf(
                                    "temperature" to FieldSpec(path = "temperature", min = -50.0, max = 80.0),
                                    "humidity" to FieldSpec(path = "humidity", required = false, scale = 100.0),
                                    "heating" to FieldSpec(path = "heating", required = false),
                                    "note" to FieldSpec(path = "note", required = false),
                                ),
                        ),
                    ),
            )

        fun map(
            json: String,
            src: Source = source,
        ) = Mapper.map(src, ds, Json.parseToJsonElement(json), now)

        fun reasons(
            json: String,
            src: Source = source,
        ) = shouldThrow<MappingException> { map(json, src) }.reasons

        test("flat payload gives one cast record and ignores extra fields") {
            val r =
                map(
                    """{"deviceId":"pico-1","timestamp":"2026-10-27T09:30:00Z","temperature":"21.5","humidity":0.4,"heating":"true","note":"ok","extra":1}""",
                ).single()
            r.ts shouldBe Instant.parse("2026-10-27T09:30:00Z")
            r.tsEstimated shouldBe false
            r.dims shouldBe mapOf("device" to "pico-1")
            r.vals shouldBe
                mapOf(
                    "temperature" to JsonPrimitive(21.5),
                    "humidity" to JsonPrimitive(40.0),
                    "heating" to JsonPrimitive(true),
                    "note" to JsonPrimitive("ok"),
                )
            r.ident shouldBe "pico-1|2026-10-27T09:30:00Z"
        }

        test("local date-time is read as UTC and missing timestamp uses arrival time") {
            map("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00","temperature":1}""").single().ts shouldBe
                Instant.parse("2026-10-27T09:00:00Z")
            val r =
                map(
                    """{"deviceId":"a","temperature":1}""",
                    source.copy(mapping = listOf(source.mapping[0].copy(identity = emptyList()))),
                ).single()
            r.ts shouldBe now
            r.tsEstimated shouldBe true
            r.ident shouldBe null
        }

        test("epoch formats") {
            val ms = source.copy(mapping = listOf(source.mapping[0].copy(timestamp = TimestampSpec("timestamp", "epoch_ms"))))
            val s = source.copy(mapping = listOf(source.mapping[0].copy(timestamp = TimestampSpec("timestamp", "epoch_s"))))
            map("""{"deviceId":"a","timestamp":1793091600000,"temperature":1}""", ms).single().ts shouldBe
                Instant.fromEpochSeconds(1793091600)
            map("""{"deviceId":"a","timestamp":1793091600,"temperature":1}""", s).single().ts shouldBe Instant.fromEpochSeconds(1793091600)
        }

        test("each failed constraint gives one reason") {
            reasons(
                """{"deviceId":" ","timestamp":"2026-10-20T00:00:00Z","temperature":99,"heating":"maybe"}""",
            ) shouldContainExactlyInAnyOrder
                listOf(
                    "dims.device: must not be blank",
                    "timestamp: older than 24 h",
                    "vals.temperature: 99.0 is above max 80.0",
                    "vals.heating: expected a boolean, got 'maybe'",
                )
            reasons("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00Z","temperature":-60}""") shouldBe
                listOf("vals.temperature: -60.0 is below min -50.0")
            reasons("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00Z"}""") shouldBe
                listOf("vals.temperature: missing at path 'temperature'")
            reasons("""{"deviceId":"a","timestamp":"2026-10-27T09:00:00Z","temperature":"warm"}""") shouldBe
                listOf("vals.temperature: expected a number, got 'warm'")
        }

        test("each iterates object entries with key and item paths plus const dims") {
            val epiDs =
                Dataset(id = "epi", measures = listOf(Measure("infected")), dimensions = listOf(Dimension("run"), Dimension("type")))
            val epi =
                Source(
                    id = "epi",
                    dataset = "epi",
                    mapping =
                        listOf(
                            Rule(
                                dims = mapOf("run" to FieldSpec(path = "meta.runId")),
                                vals = mapOf("infected" to FieldSpec(path = "state.infected")),
                                constDims =
                                    mapOf("type" to "total"),
                            ),
                            Rule(
                                each = "state.byType",
                                dims = mapOf("run" to FieldSpec(path = "meta.runId"), "type" to FieldSpec(path = "@key")),
                                vals =
                                    mapOf("infected" to FieldSpec(path = "@.infected")),
                            ),
                        ),
                )
            val recs =
                Mapper.map(
                    epi,
                    epiDs,
                    Json.parseToJsonElement(
                        """{"meta":{"runId":"r1"},"state":{"infected":5,"byType":{"0":{"infected":2},"1":{"infected":3}}}}""",
                    ),
                    now,
                )
            recs.map { it.dims["type"] to it.vals["infected"] } shouldBe
                listOf("total" to JsonPrimitive(5.0), "0" to JsonPrimitive(2.0), "1" to JsonPrimitive(3.0))
            recs.map { it.dims["run"] }.distinct() shouldBe listOf("r1")
        }

        test("native format accepts records and rejects undeclared keys") {
            val native = source.copy(mapping = emptyList())
            val recs =
                map(
                    """[{"ts":"2026-10-27T09:00:00Z","dims":{"device":"x"},"vals":{"temperature":20}},{"dims":{"device":"y"},"vals":{"temperature":21}}]""",
                    native,
                )
            recs.size shouldBe 2
            recs[0].ts shouldBe Instant.parse("2026-10-27T09:00:00Z")
            recs[1].tsEstimated shouldBe true
            map("""{"dims":{"device":"x"},"vals":{"temperature":20}}""", native).size shouldBe 1
            reasons("""{"dims":{"device":"x","room":"k"},"vals":{"pressure":1}}""", native) shouldContainExactlyInAnyOrder
                listOf("[0].dims.room: not declared in dataset", "[0].vals.pressure: not declared in dataset")
        }

        test("each walks arrays by index and reports a path that is not a collection") {
            val list =
                source.copy(
                    mapping =
                        listOf(
                            Rule(
                                each = "readings",
                                identity = listOf("@.id"),
                                dims = mapOf("device" to FieldSpec(path = "@key")),
                                vals = mapOf("temperature" to FieldSpec(path = "@.t")),
                            ),
                        ),
                )
            map("""{"readings":[{"id":"a","t":1},{"id":"b","t":2}]}""", list).map {
                Triple(it.dims["device"], it.vals["temperature"], it.ident)
            } shouldBe
                listOf(Triple("0", JsonPrimitive(1.0), "a|0"), Triple("1", JsonPrimitive(2.0), "b|1"))
            reasons("""{"readings":5}""", list) shouldBe listOf("mapping[0].each: no object or array at path 'readings'")
            reasons("""{}""", list) shouldBe listOf("mapping[0].each: no object or array at path 'readings'")
        }

        test("paths walk into arrays by index and stop at anything else") {
            val nested =
                source.copy(
                    mapping =
                        listOf(
                            Rule(
                                dims = mapOf("device" to FieldSpec(path = "devices.1.id")),
                                vals =
                                    mapOf(
                                        "temperature" to FieldSpec(path = "devices.first.t", required = false),
                                        "humidity" to FieldSpec(path = "devices.0.id.deep", required = false),
                                        "note" to FieldSpec(constValue = JsonPrimitive("fixed")),
                                    ),
                            ),
                        ),
                )
            val r = map("""{"devices":[{"id":"a","t":1},{"id":"b"}]}""", nested).single()
            r.dims shouldBe mapOf("device" to "b")
            r.vals shouldBe mapOf("note" to JsonPrimitive("fixed"))
            reasons("""{"devices":[{"id":"a"}]}""", nested) shouldBe listOf("dims.device: missing at path 'devices.1.id'")
        }

        test("key and item paths outside each, nested or null values and missing identity are reasons") {
            val loose =
                source.copy(
                    mapping =
                        listOf(
                            Rule(
                                identity = listOf("serial"),
                                dims = mapOf("device" to FieldSpec(path = "@key")),
                                vals = mapOf("temperature" to FieldSpec(path = "@.t"), "note" to FieldSpec(path = "note")),
                            ),
                        ),
                )
            reasons("""{"note":{"text":"hi"}}""", loose) shouldContainExactlyInAnyOrder
                listOf(
                    "dims.device: missing at path '@key'",
                    "vals.temperature: missing at path '@.t'",
                    "vals.note: expected a value at path 'note'",
                    "identity: missing at path 'serial'",
                )
            val dims = source.copy(mapping = listOf(Rule(dims = mapOf("device" to FieldSpec(path = "d")))))
            reasons("""{"d":null}""", dims) shouldBe listOf("dims.device: missing at path 'd'")
            reasons("""{"d":[1]}""", dims) shouldBe listOf("dims.device: expected a value at path 'd'")
            val blankOk = source.copy(mapping = listOf(Rule(dims = mapOf("device" to FieldSpec(path = "d", notBlank = false)))))
            map("""{"d":" "}""", blankOk).single().dims shouldBe mapOf("device" to " ")
        }

        test("timestamps that are null or not values fall back to arrival time, unreadable ones are reasons") {
            val iso =
                source.copy(
                    mapping = listOf(Rule(timestamp = TimestampSpec("at"), vals = mapOf("temperature" to FieldSpec(path = "t")))),
                )
            listOf("""{"at":null,"t":1}""", """{"at":{"s":1},"t":1}""").forEach { json ->
                val r = map(json, iso).single()
                r.ts shouldBe now
                r.tsEstimated shouldBe true
            }
            map("""{"at":"2020-01-01T00:00:00Z","t":1}""", iso).single().ts shouldBe Instant.parse("2020-01-01T00:00:00Z")
            reasons("""{"at":"soon","t":1}""", iso) shouldBe listOf("timestamp: cannot parse 'soon' as iso")
            listOf("epoch_ms", "epoch_s").forEach { format ->
                val epoch = iso.copy(mapping = listOf(iso.mapping[0].copy(timestamp = TimestampSpec("at", format))))
                reasons("""{"at":"soon","t":1}""", epoch) shouldBe listOf("timestamp: cannot parse 'soon' as $format")
                map("""{"at":"1793091600","t":1}""", epoch).single().tsEstimated shouldBe false
            }
        }

        test("optional values may be null, text must not be blank unless allowed") {
            val text =
                source.copy(
                    mapping =
                        listOf(
                            Rule(
                                vals =
                                    mapOf(
                                        "temperature" to FieldSpec(path = "t", required = false),
                                        "note" to FieldSpec(path = "n"),
                                        "heating" to FieldSpec(path = "h", required = false),
                                    ),
                            ),
                        ),
                )
            map("""{"t":null,"n":"x","h":false}""", text).single().vals shouldBe
                mapOf("note" to JsonPrimitive("x"), "heating" to JsonPrimitive(false))
            reasons("""{"n":" "}""", text) shouldBe listOf("vals.note: must not be blank")
            val blankOk = text.copy(mapping = listOf(Rule(vals = mapOf("note" to FieldSpec(path = "n", notBlank = false)))))
            map("""{"n":" "}""", blankOk).single().vals shouldBe mapOf("note" to JsonPrimitive(" "))
        }

        test("native format reports the body shape, items, timestamps and values it cannot use") {
            val native = source.copy(mapping = emptyList())
            reasons("42", native) shouldBe listOf("body: expected a record object or an array of records")
            reasons("""[1,{"ts":"soon","vals":{"temperature":"warm"}}]""", native) shouldContainExactlyInAnyOrder
                listOf("[0]: expected an object", "[1].ts: cannot parse 'soon'", "vals.temperature: expected a number, got 'warm'")
            val r = map("""{"ts":null,"dims":{"device":{"nested":true}},"vals":5}""", native).single()
            r.tsEstimated shouldBe true
            r.dims shouldBe mapOf("device" to "")
            r.vals shouldBe emptyMap()
            map("""{"dims":"none","vals":{"note":"n"}}""", native).single().dims shouldBe emptyMap()
        }

        test("epidemic sample payload gives total plus one record per byType entry") {
            val bundle = Json.decodeFromString(Bundle.serializer(), java.io.File("definitions/epidemic.json").readText())
            val payload = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/payloads/epidemic.json")).readText())
            val recs = Mapper.map(bundle.sources.single(), bundle.datasets.single(), payload, now)
            recs.map { it.dims["type"] } shouldBe listOf("total", "0", "1", "2")
            recs.map { it.ident } shouldBe
                listOf("RPI_Pico_2w_1|5|32", "RPI_Pico_2w_1|5|32|0", "RPI_Pico_2w_1|5|32|1", "RPI_Pico_2w_1|5|32|2")
            recs[0].vals["population"] shouldBe JsonPrimitive(263.0)
            recs[0].vals["lockdown"] shouldBe JsonPrimitive(false)
            recs[2].vals["dead"] shouldBe JsonPrimitive(1.0)
            recs.all { it.dims["run"] == "5" && it.ts == Instant.parse("2026-01-05T15:38:14Z") } shouldBe true
        }
    })
