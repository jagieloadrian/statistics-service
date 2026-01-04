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
       return mapOf(
           "generation" to epidemicDto.meta.generation.toString(),
           "runId" to epidemicDto.meta.runId.toString(),
           "timestamp" to epidemicDto.meta.timestamp.toString(),
           "populationSize" to epidemicDto.params.populationSize.toString(),
           "infectionProbability" to epidemicDto.params.infectionProb.toString(),
           "susceptible" to epidemicDto.state.susceptible.toString(),
           "infected" to epidemicDto.state.infected.toString(),
           "recovered" to epidemicDto.state.recovered.toString(),
       )
    }
}