package com.anjo.statisticservice.service

import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.BY_TYPE_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.DEAD_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.EXPOSED_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.GENERATION_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.INFECTED_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.LOCKDOWN_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.MOBILITY_MULTIPLICATION_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.POPULATION_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.RECOVERED_KEY
import com.anjo.statisticservice.utils.EpidemicDbKeyConstants.SUSCEPTIBLE_KEY
import com.anjo.statisticservice.utils.EpidemicKeyConstants
import com.anjo.statisticservice.utils.DbKeyConstants.DEVICE_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.RUN_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import io.github.oshai.kotlinlogging.KotlinLogging

class EpidemicStatsCollectorService(private val repository: StatsRepository) {
    private val logger = KotlinLogging.logger {}

    suspend fun saveEpidemicStats(epidemicDto: EpidemicDto) {
        val key = EpidemicKeyConstants.getEpidemicKey(epidemicDto.meta.deviceId, epidemicDto.meta.runId.toString())
        logger.info { "Starting saving stats for key: $key" }
        val body = prepareEpidemicBody(epidemicDto)
        saveStatistics(EpidemicKeyConstants.EPIDEMIC_KEYS, key, body)
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

    private fun prepareEpidemicBody(epidemicDto: EpidemicDto): Map<String, String> {
        val base = mutableMapOf(
            DEVICE_ID_KEY to epidemicDto.meta.deviceId,
            GENERATION_KEY to epidemicDto.meta.generation.toString(),
            RUN_ID_KEY to epidemicDto.meta.runId.toString(),
            TIMESTAMP_KEY to epidemicDto.meta.timestamp.toString(),
            POPULATION_KEY to epidemicDto.state.population.toString(),
            SUSCEPTIBLE_KEY to epidemicDto.state.susceptible.toString(),
            INFECTED_KEY to epidemicDto.state.infected.toString(),
            RECOVERED_KEY to epidemicDto.state.recovered.toString(),
            DEAD_KEY to epidemicDto.state.dead.toString(),
            EXPOSED_KEY to epidemicDto.state.exposed.toString(),
            LOCKDOWN_KEY to epidemicDto.state.lockdown.toString(),
            MOBILITY_MULTIPLICATION_KEY to epidemicDto.state.mobilityMultiplier.toString(),
        )
        epidemicDto.state.detailedDataByType.forEach { (type, data) ->
            base["$BY_TYPE_KEY:$type:$INFECTED_KEY"] = data.infected.toString()
            base["$BY_TYPE_KEY:$type:$SUSCEPTIBLE_KEY"] = data.susceptible.toString()
            base["$BY_TYPE_KEY:$type:$EXPOSED_KEY"] = data.exposed.toString()
            base["$BY_TYPE_KEY:$type:$RECOVERED_KEY"] = data.recovered.toString()
            base["$BY_TYPE_KEY:$type:$DEAD_KEY"] = data.dead.toString()
        }
        return base
    }
}
