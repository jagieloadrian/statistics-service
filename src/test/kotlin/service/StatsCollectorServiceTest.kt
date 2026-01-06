package com.anjo.service

import com.anjo.model.dto.DetailedData
import com.anjo.model.dto.EpidemicDto
import com.anjo.model.dto.EpidemicMetaDto
import com.anjo.model.dto.EpidemicStateDto
import com.anjo.model.dto.HumanType
import com.anjo.model.dto.TemperatureDto
import com.anjo.repository.StatsRepository
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class StatsCollectorServiceTest {

    @RelaxedMockK
    private lateinit var statsRepository: StatsRepository

    @InjectMockKs
    private lateinit var statsCollectorService: StatsCollectorService

    private val instant = Instant.fromEpochSeconds(1767694524)

    @Test
    fun `given epidemic body when saved stats then check interaction with repository`() = runTest {
        //given
        val epidemicDto = getEpidemicInputDto()
        val fetchedStats = slot<Map<String, String>>()
        val fetchedKeys = slot<String>()
        val expectedKey = "epidemic:device:testId:run:1"
        val expectedStats = mapOf(
            "generation" to "1",
            "runId" to "1",
            "timestamp" to "2026-01-06T10:15:24",
            "susceptible" to "25",
            "infected" to "25",
            "recovered" to "50",
            "population" to "100",
            "mobilityMul" to "0.3",
            "dead" to "0",
            "exposed" to "0",
            "lockdown" to "false",
            "byType:CHILD:infected" to "1",
            "byType:CHILD:susceptible" to "1",
            "byType:CHILD:exposed" to "1",
            "byType:CHILD:recovered" to "1",
            "byType:CHILD:dead" to "1",
            )

        coEvery { statsRepository.saveStats(any(), capture(fetchedStats)) } returns true
        coEvery { statsRepository.addKeyStats(any(), capture(fetchedKeys)) } returns true

        //when
        statsCollectorService.saveEpidemicStats(epidemicDto)

        //then
        println(fetchedStats.captured)
        fetchedKeys.captured shouldBe expectedKey
        fetchedStats.captured shouldContainAll expectedStats
        coVerify(exactly = 1) { statsRepository.saveStats(any(), any()) }
        coVerify(exactly = 1) { statsRepository.addKeyStats(any(), any()) }
    }

    @Test
    fun `given epidemic body when not saved stats then check no interactions`() = runTest {
        //given
        val epidemicDto = getEpidemicInputDto()

        coEvery { statsRepository.saveStats(any(), any()) } returns false

        //when
        statsCollectorService.saveEpidemicStats(epidemicDto)

        //then
        coVerify(exactly = 1) { statsRepository.saveStats(any(), any()) }
        coVerify(exactly = 0) { statsRepository.addKeyStats(any(), any()) }
    }

    @Test
    fun `given temperature body when saved stats then check interaction with repository`() = runTest {
        //given
        val temperatureDto = getTemperatureInputDto()
        val fetchedStats = slot<Map<String, String>>()
        val fetchedKeys = slot<String>()
        val expectedKey = "temperature:device:testId:run"
        val expectedStats = mapOf(
            "status" to "up",
            "deviceId" to "testId",
            "timestamp" to "2026-01-06T10:15:24",
            "temperature" to "15.0",
            "humidity" to "15.0",
        )

        coEvery { statsRepository.saveStats(any(), capture(fetchedStats)) } returns true
        coEvery { statsRepository.addKeyStats(any(), capture(fetchedKeys)) } returns true

        //when
        statsCollectorService.saveTemperatureStats(temperatureDto)

        //then
        println(fetchedStats.captured)
        fetchedKeys.captured shouldBe expectedKey
        fetchedStats.captured shouldContainAll expectedStats
        coVerify(exactly = 1) { statsRepository.saveStats(any(), any()) }
        coVerify(exactly = 1) { statsRepository.addKeyStats(any(), any()) }
    }

    @Test
    fun `given temperature body when not saved stats then check no interactions`() = runTest {
        //given
        val temperatureDto = getTemperatureInputDto()

        coEvery { statsRepository.saveStats(any(), any()) } returns false

        //when
        statsCollectorService.saveTemperatureStats(temperatureDto)

        //then
        coVerify(exactly = 1) { statsRepository.saveStats(any(), any()) }
        coVerify(exactly = 0) { statsRepository.addKeyStats(any(), any()) }
    }

    private fun getEpidemicInputDto(): EpidemicDto = EpidemicDto(
        EpidemicMetaDto(
            deviceId = "testId",
            runId = 1,
            timestamp = instant.toLocalDateTime(TimeZone.UTC),
            generation = 1
        ),
        state = EpidemicStateDto(
            susceptible = 25,
            infected = 25,
            recovered = 50,
            population = 100,
            mobilityMultiplier = 0.3,
            dead = 0,
            exposed = 0,
            lockdown = false,
            detailedDataByType = mapOf(
                HumanType.CHILD to DetailedData(
                    susceptible = 1,
                    infected = 1,
                    recovered = 1,
                    dead = 1,
                    exposed = 1
                )
            )
        )
    )

    private fun getTemperatureInputDto() : TemperatureDto = TemperatureDto(
        status = "up",
        deviceId = "testId",
        timestamp = instant.toLocalDateTime(TimeZone.UTC),
        temperature = 15.0,
        humidity = 15.0
    )
}