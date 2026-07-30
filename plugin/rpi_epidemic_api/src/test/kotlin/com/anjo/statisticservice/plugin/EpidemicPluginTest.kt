package com.anjo.statisticservice.plugin

import com.anjo.statisticservice.exception.PluginValidationException
import com.anjo.statisticservice.model.dto.DetailedData
import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.model.dto.EpidemicMetaDto
import com.anjo.statisticservice.model.dto.EpidemicStateDto
import com.anjo.statisticservice.model.dto.HumanType
import com.anjo.statisticservice.service.EpidemicStatsCollectorService
import com.anjo.statisticservice.service.exposer.EpidemicStatsExposerService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class EpidemicPluginTest {

    private val collector = mockk<EpidemicStatsCollectorService>()
    private val exposer = mockk<EpidemicStatsExposerService>()
    private val plugin = EpidemicPlugin(collector, exposer)
    private val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)

    private fun validDto() = EpidemicDto(
        meta = EpidemicMetaDto(deviceId = "device-1", runId = 1, generation = 1, timestamp = now),
        state = EpidemicStateDto(
            susceptible = 10,
            infected = 5,
            recovered = 0,
            population = 100,
            mobilityMultiplier = 1.0,
            dead = 0,
            exposed = 0,
            lockdown = false,
            detailedDataByType = mapOf(HumanType.CHILD to DetailedData(0, 0, 0, 0, 0)),
        )
    )

    @Test
    fun `valid payload delegates to StatsCollectorService`() = runTest {
        coEvery { collector.saveEpidemicStats(any()) } returns Unit
        val raw = Json.encodeToJsonElement(validDto())

        plugin.collect(raw)

        coVerify(exactly = 1) { collector.saveEpidemicStats(validDto()) }
    }

    @Test
    fun `invalid payload throws PluginValidationException with the same reasons as isEpidemicValid`() = runTest {
        // isEpidemicValid's `result` is only as good as its LAST validField call (pre-existing bug,
        // out of scope here — FR-005 requires reusing validation as-is) — every field must be
        // invalid or `result` ends up true regardless of `reasons`.
        val invalid = EpidemicDto(
            meta = EpidemicMetaDto(deviceId = "", runId = 0, generation = 0, timestamp = now),
            state = EpidemicStateDto(
                susceptible = 0,
                infected = 5,
                recovered = 0,
                population = 0,
                mobilityMultiplier = 0.0,
                dead = 0,
                exposed = 0,
                lockdown = false,
                detailedDataByType = emptyMap(),
            )
        )
        val raw = Json.encodeToJsonElement(invalid)

        val exception = shouldThrow<PluginValidationException> { plugin.collect(raw) }

        exception.reasons shouldContain "Device Id or device must not be blank"
    }
}
