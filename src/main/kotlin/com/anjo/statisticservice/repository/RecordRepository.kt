package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.Record
import com.anjo.statisticservice.model.dto.DimensionValue
import com.anjo.statisticservice.model.numberMeasures
import com.anjo.statisticservice.service.mapping.Expr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.time.OffsetDateTime
import kotlin.time.Instant

object RecordRepository {

    /** false = duplicate identity, nothing stored */
    fun insert(c: Connection, r: Record): Boolean =
        c.update(
            """INSERT INTO records (org_id, dataset_id, ts, dims, vals, ident, ts_estimated)
               VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) ON CONFLICT DO NOTHING""",
            Db.ORG, r.datasetId, r.ts,
            JsonObject(r.dims.mapValues { JsonPrimitive(it.value) }).toString(),
            JsonObject(r.vals).toString(), r.ident, r.tsEstimated,
        ) > 0

    fun deleteDataset(c: Connection, datasetId: String) {
        c.update("DELETE FROM records WHERE org_id = ? AND dataset_id = ?", Db.ORG, datasetId)
    }

    fun deleteOlderThan(c: Connection, datasetId: String, days: Int) {
        c.update("DELETE FROM records WHERE org_id = ? AND dataset_id = ? AND ts < now() - make_interval(days => ?)", Db.ORG, datasetId, days)
    }

    /** [dim] must be a dimension of the dataset; it is inlined. */
    fun dimensionValues(c: Connection, datasetId: String, dim: String, search: String?, limit: Int, offset: Int): List<DimensionValue> {
        val col = "dims->>'$dim'"
        val params = mutableListOf<Any?>(Db.ORG, datasetId)
        val where = if (search.isNullOrBlank()) "" else " AND $col ILIKE ?".also { params += "%$search%" }
        params += limit
        params += offset
        return c.query(
            """SELECT $col, min(ts), max(ts), count(*) FROM records
               WHERE org_id = ? AND dataset_id = ? AND $col IS NOT NULL$where
               GROUP BY 1 ORDER BY max(ts) DESC LIMIT ? OFFSET ?""",
            *params.toTypedArray(),
        ) { DimensionValue(it.getString(1), iso(it.getObject(2)), iso(it.getObject(3)), it.getLong(4)) }
    }

    /**
     * Rows of `[time], groupBy values..., measures...` in [from, to): every record when [raw], else one row per
     * group and bucket of [bucketSeconds] (no time column when null). Group by and filter names must be dimensions.
     */
    fun query(c: Connection, ds: Dataset, q: Query, from: Instant, to: Instant, raw: Boolean, bucketSeconds: Long?, limit: Int?): List<List<JsonElement>> {
        val time = when {
            raw -> "ts"
            bucketSeconds != null -> "date_bin('$bucketSeconds seconds'::interval, ts, TIMESTAMPTZ '1970-01-01')"
            else -> null
        }
        val select = listOfNotNull(time) + q.groupBy.map { "dims->>'$it'" } + q.measures.map { measureSql(it, ds, raw) }
        val params = mutableListOf<Any?>(Db.ORG, ds.id, from, to)
        val filters = q.filter.entries.joinToString("") { (k, v) -> params.add(v); " AND dims->>'$k' = ANY(?)" }
        val tsCols = if (time == null) 0 else 1
        val groupCols = q.groupBy.size
        val groupBy = if (raw) "" else (1..tsCols + groupCols).joinToString(", ").let { if (it.isEmpty()) "" else " GROUP BY $it" }
        val orderBy = ((tsCols + 1)..(tsCols + groupCols)).toList() + (if (tsCols == 1) listOf(1) else emptyList())
        val sql = buildString {
            append("SELECT ${select.joinToString(", ")} FROM records WHERE org_id = ? AND dataset_id = ? AND ts >= ? AND ts < ?")
            append(filters).append(groupBy)
            if (orderBy.isNotEmpty()) append(" ORDER BY ${orderBy.joinToString(", ")}")
            if (limit != null) append(" LIMIT $limit")
        }
        return c.query(sql, *params.toTypedArray()) { rs -> (1..select.size).map { json(rs.getObject(it)) } }
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
}
