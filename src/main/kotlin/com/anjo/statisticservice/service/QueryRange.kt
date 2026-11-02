package com.anjo.statisticservice.service

import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.TimeRange
import kotlin.time.Duration
import kotlin.time.Instant

private const val DEFAULT_RANGE = "P1D"

fun Query.timeRange(now: Instant): TimeRange =
    if (from != null && to != null) {
        TimeRange(Instant.parse(from), Instant.parse(to))
    } else {
        TimeRange(now - Duration.parseIsoString(range?.last ?: DEFAULT_RANGE), now)
    }
