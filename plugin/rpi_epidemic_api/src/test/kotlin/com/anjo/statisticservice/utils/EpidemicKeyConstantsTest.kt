package com.anjo.statisticservice.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EpidemicKeyConstantsTest {

    @Test
    fun `getKey matches getEpidemicKey for the same device and run`() {
        val deviceId = "device-1"
        val runId = "run-7"

        ApplicationConstants.getKey("epidemic", deviceId, runId) shouldBe
            EpidemicKeyConstants.getEpidemicKey(deviceId, runId)
    }
}
