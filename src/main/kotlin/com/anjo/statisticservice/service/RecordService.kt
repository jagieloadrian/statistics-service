package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.Record
import com.anjo.statisticservice.model.dto.DimensionValue
import com.anjo.statisticservice.model.dto.IngestResult
import com.anjo.statisticservice.model.dto.QueryResult
import com.anjo.statisticservice.model.dto.Series
import com.anjo.statisticservice.repository.RecordRepository
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RecordService(private val db: Db) {

    suspend fun insert(records: List<Record>): IngestResult = db.tx {
        val inserted = records.count { RecordRepository.insert(it) }
        IngestResult(inserted, records.size - inserted)
    }

    suspend fun purgeRetention(datasets: List<Dataset>) {
        datasets.forEach { d -> d.retentionDays?.let { days -> db.tx { RecordRepository.deleteBefore(d.id, Clock.System.now() - days.days) } } }
    }

    suspend fun dimensionValues(ds: Dataset, dim: String, search: String?, limit: Int, offset: Int): List<DimensionValue> {
        if (ds.dimensions.none { it.name == dim }) throw badRequest("Unknown dimension '$dim'")
        return db.tx { RecordRepository.dimensionValues(ds.id, dim, search, limit.coerceIn(1, 500), offset.coerceAtLeast(0)) }
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
        // ponytail: raw cap is per query, not per series; split per series if a dashboard needs it
        val rows = db.tx {
            RecordRepository.query(ds, q, from, to, raw, BUCKETS[bucket]?.inWholeSeconds, if (raw) RAW_LIMIT + 1 else null)
        }
        val tsCols = if (bucket == "none") 0 else 1
        val columns = (if (tsCols == 1) listOf("ts") else emptyList()) + q.measures.map { it.alias ?: defaultAlias(it, raw) }
        val series = rows.take(RAW_LIMIT).groupBy { row -> q.groupBy.indices.associate { q.groupBy[it] to (row[tsCols + it].jsonPrimitive.contentOrNull ?: "") } }
            .map { (key, rs) -> Series(key, columns, rs.map { row -> row.take(tsCols) + row.drop(tsCols + q.groupBy.size) }) }
        return QueryResult(bucket, series, raw && rows.size > RAW_LIMIT)
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

    companion object {
        const val RAW_LIMIT = 10_000
        val BUCKETS = linkedMapOf(
            "1m" to 1.minutes, "5m" to 5.minutes, "10m" to 10.minutes, "30m" to 30.minutes,
            "1h" to 1.hours, "6h" to 6.hours, "1d" to 1.days,
        )
    }
}
