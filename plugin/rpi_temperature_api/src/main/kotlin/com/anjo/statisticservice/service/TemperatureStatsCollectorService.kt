package com.anjo.statisticservice.service

import com.anjo.statisticservice.model.dto.TemperatureDto
import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.utils.DbKeyConstants.DEVICE_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import com.anjo.statisticservice.utils.TemperatureDbKeyConstants.HUMIDITY_KEY
import com.anjo.statisticservice.utils.TemperatureDbKeyConstants.STATUS_KEY
import com.anjo.statisticservice.utils.TemperatureDbKeyConstants.TEMPERATURE_KEY
import com.anjo.statisticservice.utils.TemperatureKeyConstants
import io.github.oshai.kotlinlogging.KotlinLogging

class TemperatureStatsCollectorService(private val repository: StatsRepository) {
    private val logger = KotlinLogging.logger {}

    suspend fun saveTemperatureStats(temperatureDto: TemperatureDto) {
        val key = TemperatureKeyConstants.getTemperatureKey(temperatureDto.deviceId)
        logger.info { "Starting saving stats for key: $key" }
        val body = prepareTemperatureBody(temperatureDto)
        saveStatistics(TemperatureKeyConstants.TEMPERATURE_KEYS, key, body)
    }

    private suspend fun saveStatistics(keys: String, key: String, body: Map<String, String>) {
        if (repository.saveStats(key, body)) {
            logger.info { "Successfully saved stats for key: $key" }
            repository.addKeyStats(keys, key)
            logger.info { "Successfully saved key stats" }
        } else {
            logger.error { "Failed to save stats for key: $key" }
        }
    }

    private fun prepareTemperatureBody(temperatureDto: TemperatureDto): Map<String, String> {
        return mapOf(
            STATUS_KEY to temperatureDto.status,
            DEVICE_ID_KEY to temperatureDto.deviceId,
            TIMESTAMP_KEY to temperatureDto.timestamp.toString(),
            TEMPERATURE_KEY to temperatureDto.temperature.toString(),
            HUMIDITY_KEY to temperatureDto.humidity.toString(),
        )
    }
}
