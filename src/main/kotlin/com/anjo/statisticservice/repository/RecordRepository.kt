package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.DimText
import com.anjo.statisticservice.db.ILike
import com.anjo.statisticservice.db.Records
import com.anjo.statisticservice.db.Sql
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.Record
import com.anjo.statisticservice.model.TIMESTAMP_FIELD
import com.anjo.statisticservice.model.dto.DimensionValue
import com.anjo.statisticservice.model.measureType
import com.anjo.statisticservice.model.numberMeasures
import com.anjo.statisticservice.service.mapping.Expr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import java.sql.Timestamp
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

object RecordRepository {
    fun insert(r: Record): Boolean =
        Records
            .insertIgnore {
                it[orgId] = Db.ORG
                it[datasetId] = r.datasetId
                it[ts] = r.ts
                it[dims] = r.dims
                it[vals] = r.vals
                it[ident] = r.ident
                it[tsEstimated] = r.tsEstimated
            }.insertedCount > 0

    fun deleteDataset(datasetId: String) {
        Records.deleteWhere { of(datasetId) }
    }

    fun deleteBefore(
        datasetId: String,
        cutoff: Instant,
    ) {
        Records.deleteWhere { of(datasetId) and (ts less cutoff) }
    }

    fun dimensionValues(
        datasetId: String,
        dim: String,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<DimensionValue> {
        val value = DimText(dim)
        val first = Records.ts.min()
        val last = Records.ts.max()
        val count = Records.ts.count()
        return Records
            .select(value, first, last, count)
            .where { of(datasetId) and value.isNotNull() }
            .apply { if (!search.isNullOrBlank()) andWhere { ILike(value, "%$search%") } }
            .groupBy(value)
            .orderBy(last, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .mapNotNull { row -> row[value]?.let { DimensionValue(it, iso(row[first]), iso(row[last]), row[count]) } }
    }

    fun query(
        ds: Dataset,
        q: Query,
        from: Instant,
        to: Instant,
        raw: Boolean,
        bucketSeconds: Long?,
        limit: Int?,
    ): List<List<JsonElement>> {
        val time =
            when {
                raw -> Records.ts
                bucketSeconds != null -> Sql("date_bin('$bucketSeconds seconds'::interval, ts, TIMESTAMPTZ '1970-01-01')")
                else -> null
            }
        val groups = q.groupBy.map(::DimText)
        val select =
            buildList {
                addAll(listOfNotNull(time))
                addAll(groups)
                q.measures.mapTo(this) { Sql(this@RecordRepository.measureSql(it, ds, raw)) }
            }
        val query =
            Records
                .select(select)
                .where { of(ds.id) and (Records.ts greaterEq from) and (Records.ts less to) }
        q.filter.forEach { (k, v) -> query.andWhere { DimText(k) inList v } }
        if (!raw && (time != null || groups.isNotEmpty())) query.groupBy(*(listOfNotNull(time) + groups).toTypedArray())
        query.orderBy(*(groups + listOfNotNull(time)).map { it to SortOrder.ASC }.toTypedArray())
        limit?.let(query::limit)
        return query.map { row -> select.map { json(row[it]) } }
    }

    private fun measureSql(
        m: QueryMeasure,
        ds: Dataset,
        raw: Boolean,
    ): String {
        val (x, type) = field(ds, m.field)
        if (raw) return x
        return when (val agg = m.agg ?: defaultAggregation(type)) {
            "count" -> "count($x)"
            "sum", "avg", "min", "max" -> "$agg($x)"
            "first" -> "(array_agg($x ORDER BY ts))[1]"
            "last" -> "(array_agg($x ORDER BY ts DESC))[1]"
            "at_max" -> "(array_agg($x ORDER BY ${rankedBy(ds, m)} DESC NULLS LAST))[1]"
            "at_min" -> "(array_agg($x ORDER BY ${rankedBy(ds, m)} ASC NULLS LAST))[1]"
            else -> error("Unvalidated aggregation '$agg'")
        }
    }

    private fun defaultAggregation(type: MeasureType?) =
        when (type) {
            null -> "max"
            MeasureType.NUMBER -> "avg"
            else -> "last"
        }

    private fun rankedBy(
        ds: Dataset,
        m: QueryMeasure,
    ): String = field(ds, checkNotNull(m.by) { "Unvalidated 'by' of '${m.field}'" }).first

    private fun field(
        ds: Dataset,
        name: String,
    ): Pair<String, MeasureType?> {
        if (name == TIMESTAMP_FIELD) return TIMESTAMP_FIELD to null
        ds.calculated[name]?.let { return Expr.toSql(it, ds.numberMeasures()) to MeasureType.NUMBER }
        return when (checkNotNull(ds.measureType(name)) { "Unvalidated measure '$name'" }) {
            MeasureType.NUMBER -> "(vals->>'$name')::float8" to MeasureType.NUMBER
            MeasureType.BOOLEAN -> "(vals->>'$name')::boolean" to MeasureType.BOOLEAN
            MeasureType.TEXT -> "(vals->>'$name')" to MeasureType.TEXT
        }
    }

    private fun of(datasetId: String) = (Records.orgId eq Db.ORG) and (Records.datasetId eq datasetId)

    private fun json(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(iso(value))
        }

    private fun iso(value: Any?): String =
        when (value) {
            is Timestamp -> value.toInstant().toKotlinInstant().toString()
            else -> value.toString()
        }
}
