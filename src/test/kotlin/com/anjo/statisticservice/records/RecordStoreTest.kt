package com.anjo.statisticservice.records

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.definitions.Dataset
import com.anjo.statisticservice.definitions.Dimension
import com.anjo.statisticservice.definitions.Measure
import com.anjo.statisticservice.definitions.Query
import com.anjo.statisticservice.definitions.QueryMeasure
import com.anjo.statisticservice.mapping.Record
import com.anjo.statisticservice.routing.ApiException
import com.anjo.statisticservice.testutils.Pg
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RecordStoreTest {
    private val db = Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password)
    private val store = RecordStore(db)
    private val base = Instant.parse("2026-10-27T10:00:00Z")
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val ds = Dataset(
        id = "t", measures = listOf(Measure("temp"), Measure("gen")), dimensions = listOf(Dimension("device")),
        calculated = mapOf("ratio" to "temp / gen"), retentionDays = 1,
    )

    @BeforeEach
    fun clean() = Pg.truncateAll()

    @AfterEach
    fun close() = db.close()

    private fun rec(atSeconds: Int, device: String, temp: Double, ident: String? = null, ts: Instant = base + atSeconds.seconds) =
        Record("t", ts, false, mapOf("device" to device), mapOf("temp" to JsonPrimitive(temp)), ident)

    private fun q(bucket: String, vararg m: QueryMeasure, groupBy: List<String> = emptyList(), filter: Map<String, List<String>> = emptyMap(), maxPoints: Int = 1000) =
        runBlocking { store.query(Query("t", m.toList(), filter, groupBy, base.toString(), now.toString(), bucket = bucket, maxPoints = maxPoints), ds, now) }

    private fun QueryResult.cells() = series.map { s -> s.rows.map { row -> row.map { it.jsonPrimitive.content } } }

    private fun seed() = runBlocking { store.insert(listOf(rec(40, "a", 3.0), rec(10, "a", 1.0), rec(70, "a", 5.0))) }

    @Test
    fun `insert counts duplicates by identity`() = runBlocking<Unit> {
        store.insert(listOf(rec(0, "a", 1.0, "k1"), rec(1, "a", 2.0, "k1"), rec(2, "a", 3.0))) shouldBe InsertResult(2, 1)
        store.insert(listOf(rec(0, "a", 1.0, "k1"))) shouldBe InsertResult(0, 1)
    }

    @Test
    fun `raw query is ordered by ts`() {
        seed()
        val r = q("raw", QueryMeasure("temp"))
        r.bucket shouldBe "raw"
        r.series.single().columns shouldBe listOf("ts", "temp")
        r.cells() shouldBe listOf(
            listOf(
                listOf("2026-10-27T10:00:10Z", "1.0"),
                listOf("2026-10-27T10:00:40Z", "3.0"),
                listOf("2026-10-27T10:01:10Z", "5.0"),
            ),
        )
    }

    @Test
    fun `buckets apply every aggregation`() {
        seed()
        val aggs = listOf("count", "sum", "avg", "min", "max", "first", "last").map { QueryMeasure("temp", it) }.toTypedArray()
        val minute = q("1m", *aggs)
        minute.series.single().columns shouldBe listOf("ts", "temp_count", "temp_sum", "temp_avg", "temp_min", "temp_max", "temp_first", "temp_last")
        minute.cells() shouldBe listOf(
            listOf(
                listOf("2026-10-27T10:00:00Z", "2", "4.0", "2.0", "1.0", "3.0", "1.0", "3.0"),
                listOf("2026-10-27T10:01:00Z", "1", "5.0", "5.0", "5.0", "5.0", "5.0", "5.0"),
            ),
        )
        q("1h", *aggs).cells() shouldBe listOf(listOf(listOf("2026-10-27T10:00:00Z", "3", "9.0", "3.0", "1.0", "5.0", "1.0", "5.0")))
    }

    @Test
    fun `groupBy and filter make one series per allowed value, none gives one row`() {
        runBlocking { store.insert(listOf(rec(0, "a", 1.0), rec(1, "a", 3.0), rec(2, "b", 10.0), rec(3, "c", 99.0))) }
        val r = q("none", QueryMeasure("temp", "avg", alias = "t"), groupBy = listOf("device"), filter = mapOf("device" to listOf("a", "b")))
        r.series.map { it.dims } shouldBe listOf(mapOf("device" to "a"), mapOf("device" to "b"))
        r.series.first().columns shouldBe listOf("t")
        r.cells() shouldBe listOf(listOf(listOf("2.0")), listOf(listOf("10.0")))
    }

    @Test
    fun `auto picks the smallest bucket within maxPoints and echoes it`() {
        seed()
        q("auto", QueryMeasure("temp")).bucket shouldBe "1m" // 2 h / 1 m = 120
        q("auto", QueryMeasure("temp"), maxPoints = 10).bucket shouldBe "30m" // 12 at 10m, 4 at 30m
    }

    @Test
    fun `raw over 10 000 rows is truncated`() {
        runBlocking {
            db.tx {
                it.update(
                    """INSERT INTO records (org_id, dataset_id, ts, dims, vals, ident, ts_estimated)
                       SELECT 1, 't', TIMESTAMPTZ '2026-10-27T10:00:00Z' + (i || ' ms')::interval, '{"device":"a"}', jsonb_build_object('temp', i), NULL, false
                       FROM generate_series(1, 10001) i""",
                )
            }
        }
        val r = q("raw", QueryMeasure("temp"))
        r.truncated shouldBe true
        r.series.single().rows.size shouldBe RecordStore.RAW_LIMIT
    }

    @Test
    fun `unknown names are rejected`() {
        assertThrows<ApiException> { q("raw", QueryMeasure("pressure")) }
        assertThrows<ApiException> { q("raw", QueryMeasure("temp"), groupBy = listOf("room")) }
        assertThrows<ApiException> { q("1m", QueryMeasure("temp", "median")) }
    }

    @Test
    fun `dimension values with first and last seen, search and paging`() = runBlocking<Unit> {
        store.insert(listOf(rec(0, "alpha", 1.0), rec(300, "alpha", 1.0), rec(600, "beta", 1.0)))
        val all = store.dimensionValues(ds, "device", null, 50, 0)
        all.map { it.value } shouldBe listOf("beta", "alpha")
        all[1] shouldBe DimensionValue("alpha", "2026-10-27T10:00:00Z", "2026-10-27T10:05:00Z", 2)
        store.dimensionValues(ds, "device", "LPH", 50, 0).map { it.value } shouldBe listOf("alpha")
        store.dimensionValues(ds, "device", null, 1, 1).map { it.value } shouldBe listOf("alpha")
    }

    @Test
    fun `retention purge deletes only records older than retentionDays`() = runBlocking<Unit> {
        val real = Clock.System.now()
        store.insert(listOf(rec(0, "a", 1.0, ts = real - 2.days), rec(0, "a", 2.0, ts = real)))
        store.purgeRetention(listOf(ds, ds.copy(id = "other", retentionDays = null)))
        db.tx { c -> c.query("SELECT count(*) FROM records") { it.getLong(1) } }.single() shouldBe 1L
    }

    @Test
    fun `at_max and at_min pick by another measure, ts takes min and max`() = runBlocking<Unit> {
        store.insert(
            listOf(1.0 to 10.0, 2.0 to 30.0, 3.0 to 20.0).mapIndexed { i, (gen, temp) ->
                Record("t", base + (i * 10).seconds, false, mapOf("device" to "a"), mapOf("gen" to JsonPrimitive(gen), "temp" to JsonPrimitive(temp)), null)
            },
        )
        q(
            "none",
            QueryMeasure("gen", "at_max", by = "temp", alias = "peakGen"), QueryMeasure("gen", "at_min", by = "temp", alias = "lowGen"),
            QueryMeasure("ts", "min", alias = "from"), QueryMeasure("ts", "max", alias = "to"),
        ).cells() shouldBe listOf(listOf(listOf("2.0", "1.0", "2026-10-27T10:00:00Z", "2026-10-27T10:00:20Z")))
        assertThrows<ApiException> { q("none", QueryMeasure("gen", "at_max")) }
        assertThrows<ApiException> { q("none", QueryMeasure("ts", "avg")) }
    }

    @Test
    fun `calculated measure works raw and bucketed, division by zero is null`() = runBlocking<Unit> {
        store.insert(
            listOf(10.0 to 2.0, 9.0 to 0.0).mapIndexed { i, (temp, gen) ->
                Record("t", base + (i * 10).seconds, false, mapOf("device" to "a"), mapOf("gen" to JsonPrimitive(gen), "temp" to JsonPrimitive(temp)), null)
            },
        )
        val raw = q("raw", QueryMeasure("ratio"))
        raw.series.single().rows.map { it[1].toString() } shouldBe listOf("5.0", "null")
        q("1h", QueryMeasure("ratio", "max")).cells() shouldBe listOf(listOf(listOf("2026-10-27T10:00:00Z", "5.0")))
    }
}
