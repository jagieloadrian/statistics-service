package com.anjo.statisticservice.service.exposer


import com.anjo.statisticservice.exception.EmptyParamException
import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.model.responsedto.EpidemicRun
import com.anjo.statisticservice.model.responsedto.EpidemicShortRun
import com.anjo.statisticservice.model.responsedto.EpidemicSummary
import com.anjo.statisticservice.model.responsedto.TemperatureDevice
import com.anjo.statisticservice.model.responsedto.TemperatureSeries
import com.anjo.statisticservice.model.responsedto.TemperatureSummary
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class StatsExposerFacadeTest {

    @MockK
    lateinit var epidemicStats: EpidemicStatsExposerService

    @MockK
    lateinit var temperatureStats: TemperatureStatsExposerService

    @InjectMockKs
    lateinit var facade: StatsExposerFacade

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getEpidemicRuns delegates to epidemicStatsExposer`() = runTest {
        //given
        val runs = listOf(mockk<EpidemicShortRun>())
        coEvery { epidemicStats.getEpidemicRuns() } returns runs

        //when
        val result = facade.getEpidemicRuns()
        //then
        result shouldBe runs
    }

    @Test
    fun `getEpidemicRun delegates and validates inputs`() = runTest {
        //given
        val run = mockk<EpidemicRun>()
        coEvery { epidemicStats.getEpidemicRun("run1", "dev1") } returns run

        //when
        val result = facade.getEpidemicRun("run1", "dev1")

        //then
        result shouldBe run

        shouldThrow<EmptyParamException> {
            facade.getEpidemicRun("", "dev1")
        }

        shouldThrow<EmptyParamException> {
            facade.getEpidemicRun("run1", "   ")
        }
    }

    @Test
    fun `getEpidemicRunSummary delegates and validates inputs`() = runTest {
        //given
        val summary = mockk<EpidemicSummary>()
        coEvery { epidemicStats.getEpidemicRunSummary("run2", "dev2") } returns summary

        //when
        val result = facade.getEpidemicRunSummary("run2", "dev2")

        //then
        result shouldBe summary

        shouldThrow<EmptyParamException> {
            facade.getEpidemicRunSummary("", "dev2")
        }
    }

    @Test
    fun `getTemperatureDevices delegates to temperatureStatsExposer`() = runTest {
        //given
        val devices = listOf(mockk<TemperatureDevice>())
        coEvery { temperatureStats.getTemperatureDevices() } returns devices

        //when
        val result = facade.getTemperatureDevices()
        //then
        result shouldBe devices
    }

    @Test
    fun `getTemperatureDevice delegates and validates inputs`() = runTest {
        //given
        val series = mockk<TemperatureSeries>()
        val resolution = Resolution.valueOf(
            try {
                Resolution::class.java.enumConstants.first().name
            } catch (_: Exception) {
                throw IllegalStateException("Resolution enum required for tests")
            }
        )
        coEvery { temperatureStats.getTemperatureSeries("dev3", "2020-01-01", "2020-01-02", resolution) } returns series

        //when
        val result = facade.getTemperatureDevice("dev3", "2020-01-01", "2020-01-02", resolution)
        //then
        result shouldBe series

        shouldThrow<EmptyParamException> {
            facade.getTemperatureDevice("", "2020-01-01", "2020-01-02", resolution)
        }

        shouldThrow<EmptyParamException> {
            facade.getTemperatureDevice("dev3", "", "2020-01-02", resolution)
        }
        shouldThrow<EmptyParamException> {
            facade.getTemperatureDevice("dev3", "2020-01-01", "   ", resolution)
        }
    }

    @Test
    fun `getTemperatureDeviceSummary delegates and validates inputs`() = runTest {
        //given
        val summary = mockk<TemperatureSummary>()
        coEvery { temperatureStats.getTemperatureDeviceSummary("dev4") } returns summary
        //when
        val result = facade.getTemperatureDeviceSummary("dev4")

        //then
        result shouldBe summary

        shouldThrow<EmptyParamException> {
            facade.getTemperatureDeviceSummary("")
        }
    }
}