package com.anjo.statisticservice.validation

import com.anjo.statisticservice.model.dto.TemperatureDto
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Clock.System
import kotlin.time.Duration
import kotlin.time.DurationUnit.DAYS
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@ExtendWith(MockKExtension::class)
class TemperatureValidationTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun `given valid temperature dto when validating then returns true and no reasons`() {
        // given
        val dto = mockk<TemperatureDto>()
        // per the implementation, timestamp must be <= yesterday to be considered valid
        val yesterday = System.now()
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
        val now = System.now().toLocalDateTime(TimeZone.UTC) // this is newer than yesterday -> treated invalid by current logic
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
