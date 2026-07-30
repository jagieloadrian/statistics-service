package com.anjo.statisticservice.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TemperatureKeyConstantsTest {

    @Test
    fun `getKey matches getTemperatureKey for the same device with no run id`() {
        val deviceId = "device-2"

        ApplicationConstants.getKey("temperature", deviceId, null) shouldBe
            TemperatureKeyConstants.getTemperatureKey(deviceId)
    }
}
