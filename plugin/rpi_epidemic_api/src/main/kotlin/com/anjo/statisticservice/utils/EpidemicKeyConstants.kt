package com.anjo.statisticservice.utils

object EpidemicKeyConstants {
    const val EPIDEMIC_KEYS = "epidemic:runs"

    fun getEpidemicKey(deviceId: String, runId: String): String {
        return "epidemic:device:${deviceId}:run:${runId}"
    }
}
