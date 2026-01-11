package com.anjo.statisticservice.service.exposer

import com.anjo.statisticservice.exception.EmptyParamException
import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.model.responsedto.EpidemicRun
import com.anjo.statisticservice.model.responsedto.EpidemicShortRun
import com.anjo.statisticservice.model.responsedto.EpidemicSummary
import com.anjo.statisticservice.model.responsedto.TemperatureDevice
import com.anjo.statisticservice.model.responsedto.TemperatureSeries
import com.anjo.statisticservice.model.responsedto.TemperatureSummary

class StatsExposerFacade(private val epidemicStatsExposer: EpidemicStatsExposerService,
    private val temperatureStatsExposer: TemperatureStatsExposerService) {

    suspend fun getEpidemicRuns(): List<EpidemicShortRun> {
        return epidemicStatsExposer.getEpidemicRuns()
    }

    suspend fun getEpidemicRun(runId: String, deviceId: String): EpidemicRun {
        validateStringField(runId, "Run id")
        validateStringField(deviceId, "Device id")
        return epidemicStatsExposer.getEpidemicRun(deviceId, runId)
    }

    suspend fun getEpidemicRunSummary(runId: String, deviceId: String): EpidemicSummary {
        validateStringField(runId, "Run id")
        validateStringField(deviceId, "Device id")
        return epidemicStatsExposer.getEpidemicRunSummary(runId, deviceId)
    }

    suspend fun getTemperatureDevices(): List<TemperatureDevice> {
        return temperatureStatsExposer.getTemperatureDevices()
    }

    suspend fun getTemperatureDevice(deviceId: String, from: String, to: String, resolution: Resolution): TemperatureSeries {
        validateStringField(deviceId, "Device id")
        validateStringField(from, "From date")
        validateStringField(to, "To date")
        return temperatureStatsExposer.getTemperatureSeries(deviceId, from, to, resolution)
    }

    suspend fun getTemperatureDeviceSummary(deviceId: String): TemperatureSummary {
        validateStringField(deviceId, "Device id")
        return temperatureStatsExposer.getTemperatureDeviceSummary(deviceId)
    }

    private fun validateStringField(field:String, fieldName:String) {
        if (field.isEmpty() || field.isBlank()) {
            throw EmptyParamException("$fieldName cannot be empty or blank")
        }
    }
}