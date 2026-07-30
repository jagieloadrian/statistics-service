package com.anjo.statisticservice.validation

import com.anjo.statisticservice.model.dto.DetailedData
import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.model.dto.HumanType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EpidemicValidationTest {

    @Test
    fun `given valid epidemic dto when validating then returns true and no reasons`() {
        // given
        val dto = mockk<EpidemicDto>()
        every { dto.meta.deviceId } returns "device-1"
        every { dto.meta.runId } returns 1
        every { dto.meta.generation } returns 1
        every { dto.state.population } returns 100
        every { dto.state.susceptible } returns 10
        every { dto.state.mobilityMultiplier } returns 1.0
        every { dto.state.detailedDataByType } returns mapOf(HumanType.CHILD to DetailedData(0, 0, 0, 0, 0))

        // when
        val (valid, reasons) = isEpidemicValid(dto)

        // then
        valid shouldBe true
        reasons.shouldBeEmpty()
    }

    @Test
    fun `given epidemic dto with blank device and invalid numeric fields when validating then returns false and contains reasons`() {
        // given
        val dto = mockk<EpidemicDto>()
        every { dto.meta.deviceId } returns "" // blank device id
        every { dto.meta.runId } returns 0 // invalid
        every { dto.meta.generation } returns 0 // invalid
        every { dto.state.population } returns 0 // invalid
        every { dto.state.susceptible } returns 0 // invalid
        every { dto.state.mobilityMultiplier } returns 0.0 // invalid
        every { dto.state.detailedDataByType } returns emptyMap() // invalid

        // when
        val (valid, reasons) = isEpidemicValid(dto)

        // then
        valid shouldBe false
        reasons shouldContain "Device Id or device must not be blank"
        reasons shouldContain "Detailed Data by Type cannot be empty"
        // numeric fields include field name + MUST_BE_GREATER_THAN_ZERO - check presence of their field names
        reasons.any { it.contains("RunId") } shouldBe true
        reasons.any { it.contains("Generation") } shouldBe true
        reasons.any { it.contains("PopulationSize") } shouldBe true
        reasons.any { it.contains("Mobility multiplier") } shouldBe true
        reasons.any { it.contains("Susceptible") } shouldBe true
    }
}
