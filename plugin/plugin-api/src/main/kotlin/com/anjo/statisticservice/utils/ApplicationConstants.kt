package com.anjo.statisticservice.utils

object ApplicationConstants {
    //PATHS
    const val API_BASE_PATH = "/api/v1"

    fun getKey(pluginId: String, deviceId: String, runId: String? = null): String {
        return if (runId != null) {
            "$pluginId:device:$deviceId:run:$runId"
        } else {
            "$pluginId:device:$deviceId:run"
        }
    }
}
