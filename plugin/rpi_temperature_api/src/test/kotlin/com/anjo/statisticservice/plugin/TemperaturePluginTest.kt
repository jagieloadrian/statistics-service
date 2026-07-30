package com.anjo.statisticservice.plugin

import com.anjo.statisticservice.exception.PluginValidationException
import com.anjo.statisticservice.model.dto.TemperatureDto
import com.anjo.statisticservice.service.TemperatureStatsCollectorService
import com.anjo.statisticservice.service.exposer.TemperatureStatsExposerService
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
import kotlin.time.Duration
import kotlin.time.DurationUnit.DAYS
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@OptIn(ExperimentalTime::class)
class TemperaturePluginTest {

    private val collector = mockk<TemperatureStatsCollectorService>()
    private val exposer = mockk<TemperatureStatsExposerService>()
    private val plugin = TemperaturePlugin(collector, exposer)

    // per isTemperatureDtoValid, timestamp must be <= yesterday to be considered valid
    private val yesterday = Clock.System.now()
        .minus(Duration.convert(1.0, DAYS, DAYS).toDuration(DAYS))
        .toLocalDateTime(TimeZone.UTC)

    private fun validDto() = TemperatureDto(
        status = "OK",
        deviceId = "device-1",
        timestamp = yesterday,
        temperature = 21.5,
        humidity = 40.0,
    )

    @Test
    fun `valid payload delegates to StatsCollectorService`() = runTest {
        coEvery { collector.saveTemperatureStats(any()) } returns Unit
        val raw = Json.encodeToJsonElement(validDto())

        plugin.collect(raw)

        coVerify(exactly = 1) { collector.saveTemperatureStats(validDto()) }
    }

    @Test
    fun `invalid payload throws PluginValidationException with the same reasons as isTemperatureDtoValid`() = runTest {
        // isTemperatureDtoValid's `result` is only as good as its LAST validField call (pre-existing
        // bug, out of scope here — FR-005 requires reusing validation as-is) — every field must be
        // invalid or `result` ends up true regardless of `reasons`.
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val invalid = validDto().copy(status = "", deviceId = "   ", timestamp = now, temperature = 0.0)
        val raw = Json.encodeToJsonElement(invalid)

        val exception = shouldThrow<PluginValidationException> { plugin.collect(raw) }

        exception.reasons shouldContain "Status cannot be empty or null"
        exception.reasons shouldContain "Device name cannot be empty or null"
    }
}
