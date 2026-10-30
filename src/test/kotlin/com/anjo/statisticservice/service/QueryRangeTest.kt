package com.anjo.statisticservice.service

import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.RelativeRange
import com.anjo.statisticservice.model.TimeRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class QueryRangeTest :
    FunSpec({
        val now = Instant.parse("2026-10-27T12:00:00Z")

        test("from and to together win over a relative range") {
            Query("d", from = "2026-10-01T00:00:00Z", to = "2026-10-02T00:00:00Z", range = RelativeRange("PT1H")).timeRange(now) shouldBe
                TimeRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"))
        }

        test("a relative range ends now, and without one the last day is used even if only one bound is given") {
            Query("d", range = RelativeRange("PT6H")).timeRange(now) shouldBe TimeRange(now - 6.hours, now)
            Query("d").timeRange(now) shouldBe TimeRange(now - 1.days, now)
            Query("d", from = "2026-10-01T00:00:00Z").timeRange(now) shouldBe TimeRange(now - 1.days, now)
            Query("d", to = "2026-10-01T00:00:00Z").timeRange(now) shouldBe TimeRange(now - 1.days, now)
        }
    })
