package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.model.AUTO_BUCKET
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.MAX_RAW_ROWS
import com.anjo.statisticservice.model.NO_BUCKET
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.RAW_BUCKET
import com.anjo.statisticservice.model.Record
import com.anjo.statisticservice.model.TIMESTAMP_FIELD
import com.anjo.statisticservice.model.TIME_BUCKETS
import com.anjo.statisticservice.model.dimensionNames
import com.anjo.statisticservice.model.dto.DimensionValue
import com.anjo.statisticservice.model.dto.IngestResult
import com.anjo.statisticservice.model.dto.QueryResult
import com.anjo.statisticservice.model.dto.Series
import com.anjo.statisticservice.repository.RecordRepository
import com.anjo.statisticservice.service.timeRange
import com.anjo.statisticservice.validation.validateQuery
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RecordService(
    private val db: Db,
) {
    suspend fun insert(records: List<Record>): IngestResult =
        db.tx {
            val inserted = records.count { RecordRepository.insert(it) }
            IngestResult(inserted, records.size - inserted)
        }

    suspend fun purgeRetention(datasets: List<Dataset>) {
        datasets.forEach { dataset ->
            dataset.retentionDays?.let { days -> db.tx { RecordRepository.deleteBefore(dataset.id, Clock.System.now() - days.days) } }
        }
    }

    suspend fun dimensionValues(
        ds: Dataset,
        dim: String,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<DimensionValue> {
        if (dim !in ds.dimensionNames()) throw badRequest("Unknown dimension '$dim'")
        return db.tx {
            RecordRepository.dimensionValues(ds.id, dim, search, limit.coerceIn(1, MAX_DIMENSION_VALUES), offset.coerceAtLeast(0))
        }
    }

    suspend fun query(
        q: Query,
        ds: Dataset,
        now: Instant,
    ): QueryResult {
        validateQuery(q, ds)
        val (from, to) = q.timeRange(now)
        val bucket = if (q.bucket == AUTO_BUCKET) autoBucket(to - from, q.maxPoints) else q.bucket
        val raw = bucket == RAW_BUCKET
        val rows =
            db.tx {
                RecordRepository.query(ds, q, from, to, raw, TIME_BUCKETS[bucket]?.inWholeSeconds, if (raw) MAX_RAW_ROWS + 1 else null)
            }
        val timeColumns = if (bucket == NO_BUCKET) 0 else 1
        val columns = listOf(TIMESTAMP_FIELD).take(timeColumns) + q.measures.map { it.alias ?: defaultAlias(it, raw) }
        val series =
            rows
                .take(MAX_RAW_ROWS)
                .groupBy { row -> groupKey(q.groupBy, row, timeColumns) }
                .map { (key, groupRows) ->
                    Series(key, columns, groupRows.map { row -> row.take(timeColumns) + row.drop(timeColumns + q.groupBy.size) })
                }
        return QueryResult(bucket, series, raw && rows.size > MAX_RAW_ROWS)
    }

    private fun groupKey(
        groupBy: List<String>,
        row: List<JsonElement>,
        offset: Int,
    ): Map<String, String> = groupBy.withIndex().associate { (i, dim) -> dim to row[offset + i].jsonPrimitive.contentOrNull.orEmpty() }

    private fun defaultAlias(
        m: QueryMeasure,
        raw: Boolean,
    ) = if (raw || m.agg == null) m.field else "${m.field}_${m.agg}"

    private fun autoBucket(
        span: Duration,
        maxPoints: Int,
    ): String = TIME_BUCKETS.entries.firstOrNull { span / it.value <= maxPoints.toDouble() }?.key ?: TIME_BUCKETS.keys.last()

    private companion object {
        const val MAX_DIMENSION_VALUES = 500
    }
}
