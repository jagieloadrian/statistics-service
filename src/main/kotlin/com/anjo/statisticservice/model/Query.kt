package com.anjo.statisticservice.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
data class QueryMeasure(
    val field: String,
    val agg: String? = null,
    val by: String? = null,
    @SerialName("as") val alias: String? = null,
)

@Serializable
data class RelativeRange(val last: String)

@Serializable
data class Query(
    val dataset: String,
    val measures: List<QueryMeasure> = emptyList(),
    val filter: Map<String, List<String>> = emptyMap(),
    val groupBy: List<String> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    val range: RelativeRange? = null,
    val bucket: String = AUTO_BUCKET,
    val maxPoints: Int = 1000,
)

const val TIMESTAMP_FIELD = "ts"
const val AUTO_BUCKET = "auto"
const val RAW_BUCKET = "raw"
const val NO_BUCKET = "none"
const val MAX_RAW_ROWS = 10_000

val TIME_BUCKETS: Map<String, Duration> =
    linkedMapOf(
        "1m" to 1.minutes,
        "5m" to 5.minutes,
        "10m" to 10.minutes,
        "30m" to 30.minutes,
        "1h" to 1.hours,
        "6h" to 6.hours,
        "1d" to 1.days,
    )

val BUCKETS: Set<String> = setOf(AUTO_BUCKET, RAW_BUCKET, NO_BUCKET) + TIME_BUCKETS.keys

val AGGREGATIONS = setOf("count", "sum", "avg", "min", "max", "first", "last", "at_max", "at_min")

val NUMBER_AGGREGATIONS = setOf("sum", "avg")

val TIMESTAMP_AGGREGATIONS = setOf("min", "max", "first", "last", "count")

val RANKED_AGGREGATIONS = setOf("at_max", "at_min")

private const val DEFAULT_RANGE = "P1D"

data class TimeRange(val from: Instant, val to: Instant)

fun Query.timeRange(now: Instant): TimeRange =
    if (from != null && to != null) {
        TimeRange(Instant.parse(from), Instant.parse(to))
    } else {
        TimeRange(now - Duration.parseIsoString(range?.last ?: DEFAULT_RANGE), now)
    }
