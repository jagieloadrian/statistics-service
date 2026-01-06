package com.anjo.service

import com.anjo.model.dto.EpidemicDto
import com.anjo.model.dto.TemperatureDto
import com.anjo.repository.StatsRepository
import com.anjo.utils.ApplicationConstants.EPIDEMIC_KEYS
import com.anjo.utils.ApplicationConstants.TEMPERATURE_KEYS
import com.anjo.utils.ApplicationConstants.getEpidemicKey
import com.anjo.utils.ApplicationConstants.getTemperatureKey
import io.github.oshai.kotlinlogging.KotlinLogging

class StatsCollectorService(private val repository: StatsRepository) {
    private val logger = KotlinLogging.logger {}

    suspend fun saveEpidemicStats(epidemicDto: EpidemicDto) {
        val key = getEpidemicKey(epidemicDto.meta.deviceId, epidemicDto.meta.runId.toString())
        logger.info { "Starting saving stats for key: $key" }
        val body = prepareEpidemicBody(epidemicDto)
        saveStatistics(EPIDEMIC_KEYS, key, body)
    }

    suspend fun saveTemperatureStats(temperatureDto: TemperatureDto) {
        val key = getTemperatureKey(temperatureDto.deviceId)
        logger.info { "Starting saving stats for key: $key" }
        val body = prepareTemperatureBody(temperatureDto)
        saveStatistics(TEMPERATURE_KEYS, key, body)
    }

    private suspend fun saveStatistics(keys:String, key: String, body: Map<String, String>) {
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
           "status" to temperatureDto.status,
           "deviceId" to temperatureDto.deviceId,
           "timestamp" to temperatureDto.timestamp.toString(),
           "temperature" to temperatureDto.temperature.toString(),
           "humidity" to temperatureDto.humidity.toString(),
       )
    }

    private fun prepareEpidemicBody(epidemicDto: EpidemicDto): Map<String, String> {
       val base =  mutableMapOf(
           "deviceId" to epidemicDto.meta.deviceId,
           "generation" to epidemicDto.meta.generation.toString(),
           "runId" to epidemicDto.meta.runId.toString(),
           "timestamp" to epidemicDto.meta.timestamp.toString(),
           "population" to epidemicDto.state.population.toString(),
           "susceptible" to epidemicDto.state.susceptible.toString(),
           "infected" to epidemicDto.state.infected.toString(),
           "recovered" to epidemicDto.state.recovered.toString(),
           "dead" to epidemicDto.state.dead.toString(),
           "exposed" to epidemicDto.state.exposed.toString(),
           "lockdown" to epidemicDto.state.lockdown.toString(),
           "mobilityMul" to epidemicDto.state.mobilityMultiplier.toString(),
       )
        epidemicDto.state.detailedDataByType.forEach { (type, data) ->
            base["byType:$type:infected"] = data.infected.toString()
            base["byType:$type:susceptible"] = data.susceptible.toString()
            base["byType:$type:exposed"] = data.exposed.toString()
            base["byType:$type:recovered"] = data.recovered.toString()
            base["byType:$type:dead"] = data.dead.toString()
        }
        return base
    }
}