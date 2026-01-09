package com.anjo.statisticservice.service.exposer

import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.utils.ApplicationConstants.TEMPERATURE_KEYS
import com.anjo.statisticservice.utils.DbKeyConstants.HUMIDITY_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TEMPERATURE_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import io.kotest.matchers.doubles.shouldBeExactly
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
class TemperatureStatsExposerServiceTest {

    @MockK
    lateinit var repository: StatsRepository

    @InjectMockKs
    private lateinit var service: TemperatureStatsExposerService

    @Test
    fun `getTemperatureDevices returns device list with first and last seen`() = runTest {
        // given
        val devA = "devA"
        val devB = "devB"
        val aTs1 = "2020-01-01T00:00:00"
        val aTs2 = "2020-01-02T00:00:00"
        val bTs1 = "2021-06-01T10:00:00"
        val bTs2 = "2021-06-01T12:00:00"

        every { repository.getKeyStats(TEMPERATURE_KEYS) } returns flowOf(devA, devB)
        coEvery {
            repository.getStatsByKeys(listOf(devA, devB))
        } returns mapOf(
            devA to flowOf(mapOf(TIMESTAMP_KEY to aTs1), mapOf(TIMESTAMP_KEY to aTs2)),
            devB to flowOf(mapOf(TIMESTAMP_KEY to bTs1), mapOf(TIMESTAMP_KEY to bTs2))
        )

        // when
        val result = service.getTemperatureDevices()

        // then
        result.size shouldBe 2
        val first = result[0]
        first.deviceId shouldBe devA
        first.firstSeen shouldBe LocalDateTime.parse(aTs1)
        first.lastSeen shouldBe LocalDateTime.parse(aTs2)

        val second = result[1]
        second.deviceId shouldBe devB
        second.firstSeen shouldBe LocalDateTime.parse(bTs1)
        second.lastSeen shouldBe LocalDateTime.parse(bTs2)
    }

    @Test
    fun `getTemperatureSeries returns raw series`() = runTest {
        // given
        val deviceId = "devX"
        val from = "2020-01-01T00:00:00"
        val to = "2020-01-02T00:00:00"
        val entry1 = mapOf(
            TIMESTAMP_KEY to "2020-01-01T01:00:00",
            TEMPERATURE_KEY to "20.5",
            HUMIDITY_KEY to "40.0"
        )
        val entry2 = mapOf(
            TIMESTAMP_KEY to "2020-01-01T02:00:00",
            TEMPERATURE_KEY to "22.0"
        )

        coEvery {
            repository.getStats(deviceId, any(), any())
        } returns flowOf(entry1, entry2)

        // when
        val series = service.getTemperatureSeries(deviceId, from, to, Resolution.RAW)

        // then
        series.deviceId shouldBe deviceId
        series.from shouldBe LocalDateTime.parse(from)
        series.to shouldBe LocalDateTime.parse(to)
        series.points.size shouldBe 2
        series.points.map { it.temperature } shouldBe listOf(20.5, 22.0)
        series.points[0].humidity shouldBe 40.0
        series.points[1].humidity shouldBe null
    }

    @Test
    fun `getTemperatureSeries aggregates by resolution`() = runTest {
        // given
        val deviceId = "devAgg"
        val from = "2022-01-01T00:00:00"
        val to = "2022-01-01T01:00:00"
        // pick a non-RAW resolution from enum
        val resolution = Resolution::class.java.enumConstants.first { it.name != "RAW" }
        // use two entries that fall into the same bucket (same timestamp here)
        val ts = "2022-01-01T00:15:00"
        val entry1 = mapOf(TIMESTAMP_KEY to ts, TEMPERATURE_KEY to "20.0", HUMIDITY_KEY to "30.0")
        val entry2 = mapOf(TIMESTAMP_KEY to ts, TEMPERATURE_KEY to "22.0", HUMIDITY_KEY to "50.0")

        coEvery {
            repository.getStats(deviceId, any(), any())
        } returns flowOf(entry1, entry2)

        // when
        val series = service.getTemperatureSeries(deviceId, from, to, resolution)

        // then
        series.deviceId shouldBe deviceId
        series.points.size shouldBe 1
        // average temperature (20 + 22) / 2 = 21.0
        series.points[0].temperature shouldBeExactly 21.0
        // average humidity (30 + 50) / 2 = 40.0
        series.points[0].humidity?.shouldBeExactly(40.0)
    }

    @Test
    fun `getTemperatureDeviceSummary computes averages min and max`() = runTest {
        // given
        val deviceId = "devSummary"
        val entry1 = mapOf(TEMPERATURE_KEY to "10.0", HUMIDITY_KEY to "20.0")
        val entry2 = mapOf(TEMPERATURE_KEY to "14.0", HUMIDITY_KEY to "24.0")
        coEvery { repository.getStats(deviceId) } returns flowOf(entry1, entry2)

        // when
        val summary = service.getTemperatureDeviceSummary(deviceId)

        // then
        summary.deviceId shouldBe deviceId
        summary.avgTemperature shouldBeExactly 12.0
        summary.minTemperature shouldBeExactly 10.0
        summary.maxTemperature shouldBeExactly 14.0
        summary.avgHumidity?.shouldBeExactly(22.0)
        summary.minHumidity?.shouldBeExactly(20.0)
        summary.maxHumidity?.shouldBeExactly(24.0)
    }
}