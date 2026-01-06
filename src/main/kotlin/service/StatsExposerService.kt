package com.anjo.service

import com.anjo.model.responsedto.EpidemicShortRun
import com.anjo.repository.StatsRepository
import com.anjo.utils.ApplicationConstants.EPIDEMIC_KEYS
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime

class StatsExposerService(private val repository: StatsRepository) {

    suspend fun getEpidemicRuns(): List<EpidemicShortRun> {
        val keys = repository.getKeyStats(EPIDEMIC_KEYS).toList()
        val databaseData = repository.getStatsByKeys(keys)
            .map { (_, value) -> value.toList() }
            .flatten()
        return databaseData.map { value ->
            // can take multiple instances fur run, so I need to implement some
            // reduce method for started, ended, duration, peakInfected fields but collect by key fields:
            // runId and deviceId
            EpidemicShortRun(
                runId = value["runId"] ?: "",
                deviceId = value["deviceId"] ?: "",
                startedAt = LocalDateTime.parse(value["timestamp"] ?: ""),
                endedAt = LocalDateTime.parse(value["timestamp"] ?: ""),
                populationSize = value["population"]?.toInt() ?: 0,
                duration = value["generation"]?.toInt() ?: 0,
                peakInfected = value["infected"]?.toInt() ?: 0,
            )
        }
    }
}