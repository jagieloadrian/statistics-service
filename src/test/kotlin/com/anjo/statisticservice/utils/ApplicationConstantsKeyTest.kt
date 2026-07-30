package com.anjo.statisticservice.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ApplicationConstantsKeyTest {

    @Test
    fun `getKey matches getEpidemicKey for the same device and run`() {
        val deviceId = "device-1"
        val runId = "run-7"

        ApplicationConstants.getKey("epidemic", deviceId, runId) shouldBe
            ApplicationConstants.getEpidemicKey(deviceId, runId)
    }

    @Test
    fun `getKey matches getTemperatureKey for the same device with no run id`() {
        val deviceId = "device-2"

        ApplicationConstants.getKey("temperature", deviceId, null) shouldBe
            ApplicationConstants.getTemperatureKey(deviceId)
    }
}
