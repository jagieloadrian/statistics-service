package com.anjo.statisticservice.validation

import com.anjo.statisticservice.model.dto.DetailedData
import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.model.dto.HumanType
import com.anjo.statisticservice.model.dto.TemperatureDto
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration
import kotlin.time.DurationUnit.DAYS
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@ExtendWith(MockKExtension::class)
class ValidationRequestBodyFuncsTest {
    @Nested
    @DisplayName("isEpidemicValid")
    inner class IsEpidemicValid {

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
            every { dto.state.detailedDataByType } returns mapOf(HumanType.CHILD to DetailedData(0,0,0,0,0))

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

    @Nested
    @DisplayName("isTemperatureDtoValid")
    inner class IsTemperatureDtoValid {

        @OptIn(ExperimentalTime::class)
        @Test
        fun `given valid temperature dto when validating then returns true and no reasons`() {
            // given
            val dto = mockk<TemperatureDto>()
            // per the implementation, timestamp must be <= yesterday to be considered valid
            val yesterday = Clock.System.now()
                .minus(Duration.convert(1.0, DAYS, DAYS).toDuration(DAYS))
                .toLocalDateTime(TimeZone.UTC)

            every { dto.component1() } returns "OK"         // status
            every { dto.component2() } returns "device-1"   // deviceId
            every { dto.component3() } returns yesterday   // timestamp
            every { dto.component4() } returns 21.5        // temperature

            // when
            val (valid, reasons) = isTemperatureDtoValid(dto)

            // then
            valid shouldBe true
            reasons.shouldBeEmpty()
        }

        @OptIn(ExperimentalTime::class)
        @Test
        fun `given invalid temperature dto when validating then returns false and contains reasons`() {
            // given
            val dto = mockk<TemperatureDto>()
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC) // this is newer than yesterday -> treated invalid by current logic
            every { dto.component1() } returns ""            // empty status -> invalid
            every { dto.component2() } returns "   "         // blank device -> invalid
            every { dto.component3() } returns now           // timestamp newer than yesterday -> invalid per implementation
            every { dto.component4() } returns 0.0           // not greater than zero -> invalid

            // when
            val (valid, reasons) = isTemperatureDtoValid(dto)

            // then
            valid shouldBe false
            reasons shouldContain "Status cannot be empty or null"
            reasons shouldContain "Device name cannot be empty or null"
            reasons shouldContain "Timestamp cannot be older than yesterday"
            reasons.any { it.contains("Temperature") } shouldBe true
        }
    }
}