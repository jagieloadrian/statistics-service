package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.Records
import com.anjo.statisticservice.db.toOffsetDateTime
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
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.append
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import java.time.OffsetDateTime
import kotlin.time.Instant

object RecordRepository {

    /** false = duplicate identity, nothing stored */
    fun insert(r: Record): Boolean =
        Records.insertIgnore {
            it[orgId] = Db.ORG
            it[datasetId] = r.datasetId
            it[ts] = r.ts.toOffsetDateTime()
            it[dims] = r.dims
            it[vals] = r.vals
            it[ident] = r.ident
            it[tsEstimated] = r.tsEstimated
        }.insertedCount > 0

    fun deleteDataset(datasetId: String) {
        Records.deleteWhere { of(datasetId) }
    }

    fun deleteBefore(datasetId: String, cutoff: Instant) {
        Records.deleteWhere { of(datasetId) and (ts less cutoff.toOffsetDateTime()) }
    }

    /** [dim] must be a dimension of the dataset. */
    fun dimensionValues(datasetId: String, dim: String, search: String?, limit: Int, offset: Int): List<DimensionValue> {
        val value = DimText(dim)
        val first = Records.ts.min()
        val last = Records.ts.max()
        val count = Records.ts.count()
        return Records.select(value, first, last, count)
            .where { of(datasetId) and value.isNotNull() }
            .apply { if (!search.isNullOrBlank()) andWhere { ILike(value, "%$search%") } }
            .groupBy(value).orderBy(last, SortOrder.DESC).limit(limit).offset(offset.toLong())
            .map { DimensionValue(it[value]!!, iso(it[first]), iso(it[last]), it[count]) }
    }

    /**
     * Rows of `[time], groupBy values..., measures...` in [from, to): every record when [raw], else one row per
     * group and bucket of [bucketSeconds] (no time column when null). Group by and filter names must be dimensions.
     */
    fun query(ds: Dataset, q: Query, from: Instant, to: Instant, raw: Boolean, bucketSeconds: Long?, limit: Int?): List<List<JsonElement>> {
        val time = when {
            raw -> Records.ts
            bucketSeconds != null -> Sql("date_bin('$bucketSeconds seconds'::interval, ts, TIMESTAMPTZ '1970-01-01')")
            else -> null
        }
        val groups = q.groupBy.map(::DimText)
        val select = listOfNotNull(time) + groups + q.measures.map { Sql(measureSql(it, ds, raw)) }
        val query = Records.select(select)
            .where { of(ds.id) and (Records.ts greaterEq from.toOffsetDateTime()) and (Records.ts less to.toOffsetDateTime()) }
        q.filter.forEach { (k, v) -> query.andWhere { DimText(k) inList v } }
        if (!raw && (time != null || groups.isNotEmpty())) query.groupBy(*(listOfNotNull(time) + groups).toTypedArray())
        query.orderBy(*(groups + listOfNotNull(time)).map { it to SortOrder.ASC }.toTypedArray())
        limit?.let(query::limit)
        return query.map { row -> select.map { json(row[it]) } }
    }

    // ponytail: measures stay SQL text (array_agg(... ORDER BY ...)[1], calculated Expr output); typed Exposed expressions if they get reused
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

    private fun of(datasetId: String) = (Records.orgId eq Db.ORG) and (Records.datasetId eq datasetId)

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

/** SQL text built from names validated against the dataset. */
private class Sql(private val sql: String) : Expression<Any?>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(sql)
    }
}

/**
 * `dims->>'name'` with the name inlined; callers validate it. Exposed's extract() binds the path as a parameter,
 * and a parameter in SELECT never matches the one in GROUP BY.
 */
private class DimText(private val name: String) : ExpressionWithColumnType<String?>() {
    override val columnType = TextColumnType()
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(Records.dims, "->>'", name, "'")
    }
}

private class ILike(expr: Expression<*>, pattern: String) : ComparisonOp(expr, stringParam(pattern), "ILIKE")
