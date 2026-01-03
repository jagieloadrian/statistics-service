package com.anjo.service

import com.anjo.model.EpidemicDto
import com.anjo.reposiotry.StatsRepository
import com.anjo.utils.ApplicationConstants.EPIDEMIC_KEYS
import com.anjo.utils.ApplicationConstants.getEpidemicKey

class StatsCollectorService(private val repository: StatsRepository) {

    suspend fun savedStats(epidemicDto: EpidemicDto) {
        val key = getEpidemicKey(epidemicDto.meta.deviceId, epidemicDto.meta.runId.toString())
        val body = prepareBody(epidemicDto)
        if (repository.saveStats(key, body)) {
            repository.addKeyStats(EPIDEMIC_KEYS, key)
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