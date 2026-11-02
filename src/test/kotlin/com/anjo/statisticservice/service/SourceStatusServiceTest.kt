package com.anjo.statisticservice.service

import com.anjo.statisticservice.model.Counts
import com.anjo.statisticservice.model.Source
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SourceStatusServiceTest :
    FunSpec({
        val now = Instant.parse("2026-10-27T12:00:00Z")
        val source = Source(id = "pico", dataset = "climate", silentAfterMinutes = 10)

        test("a source never heard from waits for data with zero counts") {
            val view = SourceStatusService().view(source, now)
            view.state shouldBe "waiting-for-data"
            view.lastRecordAt shouldBe null
            view.accepted shouldBe Counts(0, 0)
            view.rejected shouldBe Counts(0, 0)
            view.rejections shouldBe emptyList()
        }

        test("accepted records count per hour and day, and the state follows the last record") {
            val status = SourceStatusService()
            status.accepted("pico", 3, now - 2.hours)
            status.accepted("pico", 2, now - 1.minutes)
            val view = status.view(source, now)
            view.state shouldBe "receiving"
            view.lastRecordAt shouldBe (now - 1.minutes).toString()
            view.accepted shouldBe Counts(2, 5)
            status.view(source, now + 12.minutes).state shouldBe "silent"
            status.view(source.copy(paused = true), now).state shouldBe "paused"
            status.view(source, now + 25.hours).accepted shouldBe Counts(0, 0)
        }

        test("five rejected attempts in a row mean erroring until one is accepted") {
            val status = SourceStatusService()
            repeat(4) { status.rejected("pico", listOf("bad"), "{}", now) }
            status.view(source, now).state shouldBe "waiting-for-data"
            status.rejected("pico", listOf("bad"), "{}", now)
            status.view(source, now).state shouldBe "erroring"
            status.accepted("pico", 1, now)
            status.view(source, now).state shouldBe "receiving"
            status.view(source, now).rejected shouldBe Counts(5, 5)
        }

        test("only the newest 50 rejections are kept, newest first, with a trimmed excerpt") {
            val status = SourceStatusService()
            repeat(51) { i -> status.rejected("pico", listOf("r$i", "again"), "x".repeat(600), now) }
            val rejections = status.view(source, now).rejections
            rejections.size shouldBe 50
            rejections.first().reason shouldBe "r50; again"
            rejections.last().reason shouldBe "r1; again"
            rejections.first().excerpt.length shouldBe 500
            rejections.first().at shouldBe now.toString()
        }

        test("forgetting a source drops its activity") {
            val status = SourceStatusService()
            status.accepted("pico", 1, now)
            status.forget("pico")
            status.view(source, now).state shouldBe "waiting-for-data"
        }
    })
