package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.Records
import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Dimension
import com.anjo.statisticservice.model.MAX_RAW_ROWS
import com.anjo.statisticservice.model.Measure
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.Record
import com.anjo.statisticservice.model.dto.DimensionValue
import com.anjo.statisticservice.model.dto.IngestResult
import com.anjo.statisticservice.model.dto.QueryResult
import com.anjo.statisticservice.repository.RecordRepository
import com.anjo.statisticservice.testutils.Pg
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RecordServiceTest :
    FunSpec({
        val db = Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password)
        val store = RecordService(db)
        val base = Instant.parse("2026-10-27T10:00:00Z")
        val now = Instant.parse("2026-10-27T12:00:00Z")
        val ds =
            Dataset(
                id = "t",
                measures = listOf(Measure("temp"), Measure("gen")),
                dimensions = listOf(Dimension("device")),
                calculated = mapOf("ratio" to "temp / gen"),
                retentionDays = 1,
            )

        beforeTest { Pg.truncateAll() }

        afterSpec { db.close() }

        fun rec(
            atSeconds: Int,
            device: String,
            temp: Double,
            ident: String? = null,
            ts: Instant = base + atSeconds.seconds,
        ) = Record("t", ts, false, mapOf("device" to device), mapOf("temp" to JsonPrimitive(temp)), ident)

        suspend fun q(
            bucket: String,
            vararg m: QueryMeasure,
            groupBy: List<String> = emptyList(),
            filter: Map<String, List<String>> = emptyMap(),
            maxPoints: Int = 1000,
        ) = store.query(
            Query("t", m.toList(), filter, groupBy, base.toString(), now.toString(), bucket = bucket, maxPoints = maxPoints),
            ds,
            now,
        )

        fun QueryResult.cells() = series.map { s -> s.rows.map { row -> row.map { it.jsonPrimitive.content } } }

        suspend fun seed() = store.insert(listOf(rec(40, "a", 3.0), rec(10, "a", 1.0), rec(70, "a", 5.0)))

        test("insert counts duplicates by identity") {
            store.insert(listOf(rec(0, "a", 1.0, "k1"), rec(1, "a", 2.0, "k1"), rec(2, "a", 3.0))) shouldBe IngestResult(2, 1)
            store.insert(listOf(rec(0, "a", 1.0, "k1"))) shouldBe IngestResult(0, 1)
        }

        test("raw query is ordered by ts") {
            seed()
            val r = q("raw", QueryMeasure("temp"))
            r.bucket shouldBe "raw"
            r.series.single().columns shouldBe listOf("ts", "temp")
            r.cells() shouldBe
                listOf(
                    listOf(
                        listOf("2026-10-27T10:00:10Z", "1.0"),
                        listOf("2026-10-27T10:00:40Z", "3.0"),
                        listOf("2026-10-27T10:01:10Z", "5.0"),
                    ),
                )
        }

        test("buckets apply every aggregation") {
            seed()
            val aggs = listOf("count", "sum", "avg", "min", "max", "first", "last").map { QueryMeasure("temp", it) }.toTypedArray()
            val minute = q("1m", *aggs)
            minute.series.single().columns shouldBe
                listOf("ts", "temp_count", "temp_sum", "temp_avg", "temp_min", "temp_max", "temp_first", "temp_last")
            minute.cells() shouldBe
                listOf(
                    listOf(
                        listOf("2026-10-27T10:00:00Z", "2", "4.0", "2.0", "1.0", "3.0", "1.0", "3.0"),
                        listOf("2026-10-27T10:01:00Z", "1", "5.0", "5.0", "5.0", "5.0", "5.0", "5.0"),
                    ),
                )
            q("1h", *aggs).cells() shouldBe listOf(listOf(listOf("2026-10-27T10:00:00Z", "3", "9.0", "3.0", "1.0", "5.0", "1.0", "5.0")))
        }

        test("groupBy and filter make one series per allowed value, none gives one row") {
            store.insert(listOf(rec(0, "a", 1.0), rec(1, "a", 3.0), rec(2, "b", 10.0), rec(3, "c", 99.0)))
            val r =
                q(
                    "none",
                    QueryMeasure("temp", "avg", alias = "t"),
                    groupBy = listOf("device"),
                    filter = mapOf("device" to listOf("a", "b")),
                )
            r.series.map { it.dims } shouldBe listOf(mapOf("device" to "a"), mapOf("device" to "b"))
            r.series.first().columns shouldBe listOf("t")
            r.cells() shouldBe listOf(listOf(listOf("2.0")), listOf(listOf("10.0")))
        }

        test("auto picks the smallest bucket within maxPoints and echoes it") {
            seed()
            q("auto", QueryMeasure("temp")).bucket shouldBe "1m"
            q("auto", QueryMeasure("temp"), maxPoints = 10).bucket shouldBe "30m"
        }

        test("raw over 10 000 rows is truncated") {
            db.tx {
                exec(
                    """INSERT INTO records (org_id, dataset_id, ts, dims, vals, ident, ts_estimated)
                   SELECT 1, 't', TIMESTAMPTZ '2026-10-27T10:00:00Z' + (i || ' ms')::interval, '{"device":"a"}', jsonb_build_object('temp', i), NULL, false
                   FROM generate_series(1, 10001) i""",
                )
            }
            val r = q("raw", QueryMeasure("temp"))
            r.truncated shouldBe true
            r.series
                .single()
                .rows.size shouldBe MAX_RAW_ROWS
        }

        test("unknown names are rejected") {
            shouldThrow<ApiException> { q("raw", QueryMeasure("pressure")) }
            shouldThrow<ApiException> { q("raw", QueryMeasure("temp"), groupBy = listOf("room")) }
            shouldThrow<ApiException> { q("1m", QueryMeasure("temp", "median")) }
        }

        test("dimension values with first and last seen, search and paging") {
            store.insert(listOf(rec(0, "alpha", 1.0), rec(300, "alpha", 1.0), rec(600, "beta", 1.0)))
            val all = store.dimensionValues(ds, "device", null, 50, 0)
            all.map { it.value } shouldBe listOf("beta", "alpha")
            all[1] shouldBe DimensionValue("alpha", "2026-10-27T10:00:00Z", "2026-10-27T10:05:00Z", 2)
            store.dimensionValues(ds, "device", "LPH", 50, 0).map { it.value } shouldBe listOf("alpha")
            store.dimensionValues(ds, "device", null, 1, 1).map { it.value } shouldBe listOf("alpha")
        }

        test("retention purge deletes only records older than retentionDays") {
            val real = Clock.System.now()
            store.insert(listOf(rec(0, "a", 1.0, ts = real - 2.days), rec(0, "a", 2.0, ts = real)))
            store.purgeRetention(listOf(ds, ds.copy(id = "other", retentionDays = null)))
            db.tx { Records.selectAll().count() } shouldBe 1L
        }

        test("at_max and at_min pick by another measure, ts takes min and max") {
            store.insert(
                listOf(1.0 to 10.0, 2.0 to 30.0, 3.0 to 20.0).mapIndexed { i, (gen, temp) ->
                    Record(
                        "t",
                        base + (i * 10).seconds,
                        false,
                        mapOf("device" to "a"),
                        mapOf(
                            "gen" to JsonPrimitive(gen),
                            "temp" to JsonPrimitive(temp),
                        ),
                        null,
                    )
                },
            )
            q(
                "none",
                QueryMeasure("gen", "at_max", by = "temp", alias = "peakGen"),
                QueryMeasure("gen", "at_min", by = "temp", alias = "lowGen"),
                QueryMeasure("ts", "min", alias = "from"),
                QueryMeasure("ts", "max", alias = "to"),
            ).cells() shouldBe listOf(listOf(listOf("2.0", "1.0", "2026-10-27T10:00:00Z", "2026-10-27T10:00:20Z")))
            shouldThrow<ApiException> { q("none", QueryMeasure("gen", "at_max")) }
            shouldThrow<ApiException> { q("none", QueryMeasure("ts", "avg")) }
        }

        test("text and boolean default to last and ts to max, records without the grouped dimension form an empty group") {
            val mixed = ds.copy(measures = ds.measures + Measure("note", MeasureType.TEXT) + Measure("on", MeasureType.BOOLEAN))

            fun mixedRec(
                atSeconds: Int,
                dims: Map<String, String>,
                note: String,
                on: Boolean,
            ) = Record("t", base + atSeconds.seconds, false, dims, mapOf("note" to JsonPrimitive(note), "on" to JsonPrimitive(on)), null)
            store.insert(
                listOf(
                    mixedRec(0, mapOf("device" to "a"), "early", false),
                    mixedRec(10, mapOf("device" to "a"), "late", true),
                    mixedRec(20, emptyMap(), "orphan", false),
                ),
            )
            val measures = listOf(QueryMeasure("note"), QueryMeasure("on"), QueryMeasure("ts"))
            val grouped =
                store.query(
                    Query("t", measures, groupBy = listOf("device"), from = base.toString(), to = now.toString(), bucket = "none"),
                    mixed,
                    now,
                )
            grouped.series.map { it.dims } shouldBe listOf(mapOf("device" to "a"), mapOf("device" to ""))
            grouped.series.first().columns shouldBe listOf("note", "on", "ts")
            grouped.cells() shouldBe
                listOf(
                    listOf(listOf("late", "true", "2026-10-27T10:00:10Z")),
                    listOf(listOf("orphan", "false", "2026-10-27T10:00:20Z")),
                )
            store
                .query(Query("t", measures.take(2), from = base.toString(), to = now.toString(), bucket = "raw"), mixed, now)
                .cells() shouldBe
                listOf(
                    listOf(
                        listOf("2026-10-27T10:00:00Z", "early", "false"),
                        listOf("2026-10-27T10:00:10Z", "late", "true"),
                        listOf("2026-10-27T10:00:20Z", "orphan", "false"),
                    ),
                )
        }

        test("auto falls back to daily buckets when even those exceed maxPoints") {
            store
                .query(
                    Query("t", listOf(QueryMeasure("temp")), from = (now - 3.days).toString(), to = now.toString(), maxPoints = 1),
                    ds,
                    now,
                ).bucket shouldBe "1d"
        }

        test("dimension values ignore a blank search and refuse unknown dimensions") {
            store.insert(listOf(rec(0, "alpha", 1.0)))
            store.dimensionValues(ds, "device", " ", 50, 0).map { it.value } shouldBe listOf("alpha")
            shouldThrow<ApiException> { store.dimensionValues(ds, "room", null, 50, 0) }.message shouldBe "Unknown dimension 'room'"
        }

        test("the repository refuses measures and aggregations that skipped validation") {
            suspend fun unvalidated(m: QueryMeasure) =
                shouldThrow<IllegalStateException> {
                    db.tx { RecordRepository.query(ds, Query("t", listOf(m)), base, now, raw = false, bucketSeconds = null, limit = null) }
                }.message
            unvalidated(QueryMeasure("temp", "median")) shouldBe "Unvalidated aggregation 'median'"
            unvalidated(QueryMeasure("pressure", "avg")) shouldBe "Unvalidated measure 'pressure'"
            unvalidated(QueryMeasure("temp", "at_max")) shouldBe "Unvalidated 'by' of 'temp'"
        }

        test("calculated measure works raw and bucketed, division by zero is null") {
            store.insert(
                listOf(10.0 to 2.0, 9.0 to 0.0).mapIndexed { i, (temp, gen) ->
                    Record(
                        "t",
                        base + (i * 10).seconds,
                        false,
                        mapOf("device" to "a"),
                        mapOf(
                            "gen" to JsonPrimitive(gen),
                            "temp" to JsonPrimitive(temp),
                        ),
                        null,
                    )
                },
            )
            val raw = q("raw", QueryMeasure("ratio"))
            raw.series
                .single()
                .rows
                .map { it[1].toString() } shouldBe listOf("5.0", "null")
            q("1h", QueryMeasure("ratio", "max")).cells() shouldBe listOf(listOf(listOf("2026-10-27T10:00:00Z", "5.0")))
        }
    })
