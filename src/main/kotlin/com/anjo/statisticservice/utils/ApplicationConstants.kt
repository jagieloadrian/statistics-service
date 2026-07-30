package com.anjo.statisticservice.utils

object ApplicationConstants {
    //PATHS
    const val API_BASE_PATH = "/api/v1"

    const val EPIDEMIC_KEYS = "epidemic:runs"
    const val TEMPERATURE_KEYS = "temperature:runs"
    fun getEpidemicKey(deviceId:String, runId:String): String {
        return "epidemic:device:${deviceId}:run:${runId}"
    }

    fun getTemperatureKey(deviceId:String): String {
        return "temperature:device:${deviceId}:run"
    }

    fun getKey(pluginId: String, deviceId: String, runId: String? = null): String {
        return if (runId != null) {
            "$pluginId:device:$deviceId:run:$runId"
        } else {
            "$pluginId:device:$deviceId:run"
        }
    }
}