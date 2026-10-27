package com.anjo.statisticservice.records

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.definitions.Dataset
import com.anjo.statisticservice.definitions.MeasureType
import com.anjo.statisticservice.definitions.Query
import com.anjo.statisticservice.definitions.QueryMeasure
import com.anjo.statisticservice.definitions.numberMeasures
import com.anjo.statisticservice.mapping.Expr
import com.anjo.statisticservice.mapping.Record
import com.anjo.statisticservice.routing.badRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
data class Series(val dims: Map<String, String>, val columns: List<String>, val rows: List<List<JsonElement>>)

@Serializable
data class QueryResult(val bucket: String, val series: List<Series>, val truncated: Boolean = false)

@Serializable
data class DimensionValue(val value: String, val firstSeen: String, val lastSeen: String, val count: Long)

data class InsertResult(val accepted: Int, val duplicates: Int)

class RecordStore(private val db: Db) {

    suspend fun insert(records: List<Record>): InsertResult = db.tx { c ->
        val inserted = records.sumOf { r ->
            c.update(
                """INSERT INTO records (org_id, dataset_id, ts, dims, vals, ident, ts_estimated)
                   VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) ON CONFLICT DO NOTHING""",
                Db.ORG, r.datasetId, r.ts,
                JsonObject(r.dims.mapValues { JsonPrimitive(it.value) }).toString(),
                JsonObject(r.vals).toString(), r.ident, r.tsEstimated,
            )
        }
        InsertResult(inserted, records.size - inserted)
    }

    suspend fun purgeRetention(datasets: List<Dataset>) {
        datasets.filter { it.retentionDays != null }.forEach { d ->
            db.tx { c ->
                c.update(
                    "DELETE FROM records WHERE org_id = ? AND dataset_id = ? AND ts < now() - make_interval(days => ?)",
                    Db.ORG, d.id, d.retentionDays,
                )
            }
        }
    }

    suspend fun dimensionValues(ds: Dataset, dim: String, search: String?, limit: Int, offset: Int): List<DimensionValue> {
        if (ds.dimensions.none { it.name == dim }) throw badRequest("Unknown dimension '$dim'")
        val col = "dims->>'$dim'" // name validated against the dataset
        val params = mutableListOf<Any?>(Db.ORG, ds.id)
        val where = if (search.isNullOrBlank()) "" else " AND $col ILIKE ?".also { params += "%$search%" }
        params += limit.coerceIn(1, 500)
        params += offset.coerceAtLeast(0)
        return db.tx { c ->
            c.query(
                """SELECT $col, min(ts), max(ts), count(*) FROM records
                   WHERE org_id = ? AND dataset_id = ? AND $col IS NOT NULL$where
                   GROUP BY 1 ORDER BY max(ts) DESC LIMIT ? OFFSET ?""",
                *params.toTypedArray(),
            ) { DimensionValue(it.getString(1), iso(it.getObject(2)), iso(it.getObject(3)), it.getLong(4)) }
        }
    }

    suspend fun query(q: Query, ds: Dataset, now: Instant): QueryResult {
        val (from, to) = timeRange(q, now)
        if (to <= from) throw badRequest("'to' must be after 'from'")
        if (q.measures.isEmpty()) throw badRequest("measures: at least one is required")
        val dims = ds.dimensions.map { it.name }.toSet()
        (q.groupBy + q.filter.keys).filterNot { it in dims }.forEach { throw badRequest("Unknown dimension '$it'") }

        val bucket = when (q.bucket) {
            "auto" -> autoBucket(to - from, q.maxPoints)
            "raw", "none" -> q.bucket
            in BUCKETS -> q.bucket
            else -> throw badRequest("bucket: raw | none | auto | ${BUCKETS.keys.joinToString(" | ")}")
        }
        val raw = bucket == "raw"
        val cols = q.measures.map { m -> (m.alias ?: defaultAlias(m, raw)) to measureSql(m, ds, raw) }

        val select = buildList {
            if (bucket != "none") add(if (raw) "ts" else "date_bin('${BUCKETS[bucket]!!.inWholeSeconds} seconds'::interval, ts, TIMESTAMPTZ '1970-01-01')")
            q.groupBy.forEach { add("dims->>'$it'") }
            cols.forEach { add(it.second) }
        }
        val params = mutableListOf<Any?>(Db.ORG, ds.id, from, to)
        val filters = q.filter.entries.joinToString("") { (k, v) -> params.add(v); " AND dims->>'$k' = ANY(?)" }
        val tsCols = if (bucket == "none") 0 else 1
        val groupCols = q.groupBy.size
        val groupBy = if (raw) "" else (1..tsCols + groupCols).joinToString(", ").let { if (it.isEmpty()) "" else " GROUP BY $it" }
        val orderBy = ((tsCols + 1)..(tsCols + groupCols)).toList() + (if (tsCols == 1) listOf(1) else emptyList())
        val sql = buildString {
            append("SELECT ${select.joinToString(", ")} FROM records WHERE org_id = ? AND dataset_id = ? AND ts >= ? AND ts < ?")
            append(filters).append(groupBy)
            if (orderBy.isNotEmpty()) append(" ORDER BY ${orderBy.joinToString(", ")}")
            // ponytail: raw cap is per query, not per series; split per series if a dashboard needs it
            if (raw) append(" LIMIT ${RAW_LIMIT + 1}")
        }

        val rows = db.tx { c -> c.query(sql, *params.toTypedArray()) { rs -> (1..select.size).map { rs.getObject(it) } } }
        val truncated = raw && rows.size > RAW_LIMIT
        val columns = (if (tsCols == 1) listOf("ts") else emptyList()) + cols.map { it.first }
        val series = rows.take(RAW_LIMIT).groupBy { row -> q.groupBy.indices.associate { q.groupBy[it] to (row[tsCols + it]?.toString() ?: "") } }
            .map { (key, rs) ->
                Series(key, columns, rs.map { row ->
                    (if (tsCols == 1) listOf(JsonPrimitive(iso(row[0]))) else emptyList()) +
                        row.drop(tsCols + groupCols).map(::json)
                })
            }
        return QueryResult(bucket, series, truncated)
    }

    private fun measureSql(m: QueryMeasure, ds: Dataset, raw: Boolean): String {
        val (x, type) = field(ds, m.field)
        if (raw) return x
        val agg = m.agg ?: when (type) {
            null -> "max"
            MeasureType.number -> "avg"
            else -> "last"
        }
        if (agg in setOf("sum", "avg") && type != MeasureType.number) throw badRequest("agg '$agg' needs a number measure: '${m.field}'")
        if (type == null && agg !in setOf("min", "max", "first", "last", "count")) throw badRequest("agg '$agg' not allowed on ts")
        return when (agg) {
            "count" -> "count($x)"
            "sum", "avg", "min", "max" -> "$agg($x)"
            "first" -> "(array_agg($x ORDER BY ts))[1]"
            "last" -> "(array_agg($x ORDER BY ts DESC))[1]"
            "at_max", "at_min" -> {
                val (by, byType) = field(ds, m.by ?: throw badRequest("agg '$agg' needs 'by'"))
                if (byType != MeasureType.number) throw badRequest("'by' must be a number measure: '${m.by}'")
                "(array_agg($x ORDER BY $by ${if (agg == "at_max") "DESC" else "ASC"} NULLS LAST))[1]"
            }
            else -> throw badRequest("agg: count | sum | avg | min | max | first | last | at_max | at_min")
        }
    }

    /** SQL value and type of `ts`, a declared or a calculated measure; names validated before being inlined. */
    private fun field(ds: Dataset, name: String): Pair<String, MeasureType?> {
        if (name == "ts") return "ts" to null
        ds.calculated[name]?.let { return Expr.toSql(it, ds.numberMeasures()) to MeasureType.number }
        return when (ds.measures.firstOrNull { it.name == name }?.type ?: throw badRequest("Unknown measure '$name'")) {
            MeasureType.number -> "(vals->>'$name')::float8" to MeasureType.number
            MeasureType.boolean -> "(vals->>'$name')::boolean" to MeasureType.boolean
            MeasureType.text -> "(vals->>'$name')" to MeasureType.text
        }
    }

    private fun defaultAlias(m: QueryMeasure, raw: Boolean) = if (raw || m.agg == null) m.field else "${m.field}_${m.agg}"

    private fun timeRange(q: Query, now: Instant): Pair<Instant, Instant> = runCatching {
        when {
            q.from != null && q.to != null -> Instant.parse(q.from) to Instant.parse(q.to)
            else -> (now - Duration.parseIsoString(q.range?.last ?: "P1D")) to now
        }
    }.getOrElse { throw badRequest("from/to must be ISO-8601 instants, range.last an ISO-8601 duration") }

    private fun autoBucket(span: Duration, maxPoints: Int): String =
        BUCKETS.entries.firstOrNull { span / it.value <= maxPoints.coerceAtLeast(1).toDouble() }?.key ?: "1d"

    private fun json(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is java.sql.Timestamp, is OffsetDateTime -> JsonPrimitive(iso(v))
        else -> JsonPrimitive(v.toString())
    }

    private fun iso(v: Any?): String = when (v) {
        is OffsetDateTime -> v.toInstant().toString()
        is java.sql.Timestamp -> v.toInstant().toString()
        else -> v.toString()
    }

    companion object {
        const val RAW_LIMIT = 10_000
        val BUCKETS = linkedMapOf(
            "1m" to 1.minutes, "5m" to 5.minutes, "10m" to 10.minutes, "30m" to 30.minutes,
            "1h" to 1.hours, "6h" to 6.hours, "1d" to 1.days,
        )
    }
}
