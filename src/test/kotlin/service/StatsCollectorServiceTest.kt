package com.anjo.service

import com.anjo.model.DetailedData
import com.anjo.model.EpidemicDto
import com.anjo.model.EpidemicMetaDto
import com.anjo.model.EpidemicStateDto
import com.anjo.model.HumanType
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class StatsCollectorServiceTest {

    @RelaxedMockK
    private lateinit var statsRepository: StatsRepository

    @InjectMockKs
    private lateinit var statsCollectorService: StatsCollectorService

    @Test
    fun `given epidemic body when saved stats then check interaction with repository`() = runTest {
        //given
        val epidemicDto = getInputDto()
        val fetchedStats = slot<Map<String, String>>()
        val fetchedKeys = slot<String>()
        val expectedKey = "epidemic:device:testId:run:1"
        val expectedStats = mapOf(
            "generation" to "1",
            "runId" to "1",
            "timestamp" to "1234",
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
        statsCollectorService.savedStats(epidemicDto)

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
        val epidemicDto = getInputDto()

        coEvery { statsRepository.saveStats(any(), any()) } returns false

        //when
        statsCollectorService.savedStats(epidemicDto)

        //then
        coVerify(exactly = 1) { statsRepository.saveStats(any(), any()) }
        coVerify(exactly = 0) { statsRepository.addKeyStats(any(), any()) }
    }

    private fun getInputDto(): EpidemicDto = EpidemicDto(
        EpidemicMetaDto(
            deviceId = "testId",
            runId = 1,
            timestamp = 1234L,
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
}