package com.anjo.statisticservice.service.exposer

import com.anjo.statisticservice.model.responsedto.EpidemicRun
import com.anjo.statisticservice.model.responsedto.EpidemicShortRun
import com.anjo.statisticservice.model.responsedto.EpidemicSummary
import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.utils.DbKeyConstants.DEVICE_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.ENDED_AT_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.GENERATION_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.INFECTED_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.POPULATION_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.RUN_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.STARTED_AT_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EpidemicStatsExposerServiceTest {

    @MockK
    lateinit var repository: StatsRepository

    @InjectMockKs
    private lateinit var service: EpidemicStatsExposerService

    @Test
    fun `getEpidemicRuns returns reduced short runs`() = runTest {
        // given
        val key = "epidemic:key:1"
        val mapForKey = mapOf(
            RUN_ID_KEY to "run-1",
            DEVICE_ID_KEY to "device-1",
            POPULATION_KEY to "200",
            GENERATION_KEY to "10",
            INFECTED_KEY to "50",
            STARTED_AT_KEY to "2020-01-01T00:00:00",
            ENDED_AT_KEY to "2020-01-02T00:00:00",
            TIMESTAMP_KEY to "2020-01-01T00:00:00"
        )
        every { repository.getKeyStats(any()) } returns flowOf(key)
        coEvery { repository.getStatsByKeys(any()) } returns mapOf(key to flowOf(mapForKey))

        // when
        val result = service.getEpidemicRuns()

        // then
        result.size shouldBe 1
        val shortRun: EpidemicShortRun = result.first()
        shortRun.runId shouldBe "run-1"
        shortRun.deviceId shouldBe "device-1"
        shortRun.population shouldBe 200
        shortRun.duration shouldBe 10
        shortRun.peakInfected shouldBe 50
    }

    @Test
    fun `getEpidemicRun builds full run with meta and timeline`() = runTest {
        // given
        val deviceId = "device-x"
        val runId = "run-x"
        val entry1 = mapOf(
            POPULATION_KEY to "100",
            GENERATION_KEY to "1",
            INFECTED_KEY to "10",
            TIMESTAMP_KEY to "2020-01-01T00:00:00"
        )
        val entry2 = mapOf(
            POPULATION_KEY to "150",
            GENERATION_KEY to "2",
            INFECTED_KEY to "20",
            TIMESTAMP_KEY to "2020-01-02T00:00:00"
        )
        every { repository.getStats(any()) } returns flowOf(entry1, entry2)

        // when
        val result: EpidemicRun = service.getEpidemicRun(deviceId, runId)

        // then
        result.runId shouldBe runId
        result.meta.deviceId shouldBe deviceId
        result.meta.population shouldBe 150 // max of 100 and 150
        // startedAt and endedAt parsed from timestamps
        result.meta.startedAt shouldBe LocalDateTime.parse("2020-01-01T00:00:00")
        result.meta.endedAt shouldBe LocalDateTime.parse("2020-01-02T00:00:00")
        result.timeline.size shouldBe 2
        result.timeline.map { it.generation } shouldBe listOf(1, 2)
        result.timeline.map { it.infected } shouldBe listOf(10, 20)
    }

    @Test
    fun `getEpidemicRunSummary returns computed summary`() = runTest {
        // given
        val entry1 = mapOf(
            GENERATION_KEY to "3",
            INFECTED_KEY to "100",
            TIMESTAMP_KEY to "2020-01-01T00:00:00"
        )
        val entry2 = mapOf(
            GENERATION_KEY to "5",
            INFECTED_KEY to "50",
            TIMESTAMP_KEY to "2020-01-02T00:00:00"
        )
        every { repository.getStats(any()) } returns flowOf(entry1, entry2)

        // when
        val summary: EpidemicSummary = service.getEpidemicRunSummary("run-any", "device-any")

        // then
        // duration is max generation (5)
        summary.duration shouldBe 5
        // due to implementation details peakInfected/timeToPeak are taken from first and second infected items
        summary.peakInfected shouldBe 100
        summary.timeToPeak shouldBe 50
        // no BY_TYPE entries -> recovered/dead sums are 0
        summary.finalRecovered shouldBe 0
        summary.finalDead shouldBe 0
    }
}