package com.anjo.service

import com.anjo.model.EpidemicDto
import com.anjo.repository.StatsRepository
import com.anjo.utils.ApplicationConstants.EPIDEMIC_KEYS
import com.anjo.utils.ApplicationConstants.getEpidemicKey
import io.github.oshai.kotlinlogging.KotlinLogging

class StatsCollectorService(private val repository: StatsRepository) {
    private val logger = KotlinLogging.logger {}

    suspend fun savedStats(epidemicDto: EpidemicDto) {
        val key = getEpidemicKey(epidemicDto.meta.deviceId, epidemicDto.meta.runId.toString())
        logger.info { "Starting saving stats for key: $key" }
        val body = prepareBody(epidemicDto)
        if (repository.saveStats(key, body)) {
            logger.info { "Successfully saved stats for key: $key" }
            repository.addKeyStats(EPIDEMIC_KEYS, key)
            logger.info { "Successfully saved key stats" }
        } else {
            logger.error { "Failed to save stats for key: $key" }
        }
    }

    private fun prepareBody(epidemicDto: EpidemicDto): Map<String, String> {
       val base =  mutableMapOf(
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