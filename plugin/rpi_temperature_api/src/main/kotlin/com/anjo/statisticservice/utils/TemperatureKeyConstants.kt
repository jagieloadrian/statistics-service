package com.anjo.statisticservice.utils

object TemperatureKeyConstants {
    const val TEMPERATURE_KEYS = "temperature:runs"

    fun getTemperatureKey(deviceId: String): String {
        return "temperature:device:${deviceId}:run"
    }
}
