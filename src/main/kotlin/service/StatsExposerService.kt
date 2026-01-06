package com.anjo.service

import com.anjo.model.responsedto.EpidemicShortRun
import com.anjo.repository.StatsRepository
import com.anjo.utils.ApplicationConstants.EPIDEMIC_KEYS
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime

class StatsExposerService(private val repository: StatsRepository) {

    private val log = KotlinLogging.logger {}
    suspend fun getEpidemicRuns(): List<EpidemicShortRun> {
        val keys = repository.getKeyStats(EPIDEMIC_KEYS).toList()
        log.info { "Keys: $keys" }
        val databaseData = repository.getStatsByKeys(keys)
        log.info { "Database response $databaseData" }
        val mapped = databaseData
            .map { (key, value) ->
                log.info { "Before associating: $key - $value" }
                key to value.toList() }
            .associateBy({ it.first }, { it.second })
            .map {
                log.info { "Before reducing: ${it.key} - ${it.value}" }
                it.value.reduceList() }
        return mapped.map { value ->

            log.info { "Current value is: $value" }
            // refactor those methods and make them more common
            EpidemicShortRun(
                runId = value["runId"] ?: "",
                deviceId = value["deviceId"] ?: "",
                startedAt = LocalDateTime.parse(value["startedAt"] ?: ""),
                endedAt = LocalDateTime.parse(value["endedAt"] ?: ""),
                populationSize = value["population"]?.toInt() ?: 0,
                duration = value["generation"]?.toInt() ?: 0,
                peakInfected = value["infected"]?.toInt() ?: 0,
            )
        }
    }

    fun List<Map<String, String>>.reduceList(): Map<String, String> {
        if (this.isEmpty()) return emptyMap()
        val keys = this.flatMap { it.keys }.toSet()
        log.info { "Reduce list Keys: $keys" }
        log.info { "Input size: ${this.size}" }

        val timestamps = this.mapNotNull {
            it["timestamp"]
        }
            .filter { it.isNotBlank() }

        val startedAt = timestamps.minOrNull() ?: ""
        val endedAt = timestamps.maxOrNull() ?: ""
        val dates = mapOf(
            "startedAt" to startedAt,
            "endedAt" to endedAt,
        )

        val result =  keys.associateWith { key ->
            when (key) {
                "generation" -> this.mapNotNull { it["generation"]?.toInt() }
                    .maxOrNull()
                    ?.toString() ?: "0"
                "infected" -> this.mapNotNull { it["infected"]?.toInt() }
                    .maxOrNull()
                    ?.toString()?: "0"
                else -> this.firstNotNullOfOrNull { it[key] } ?: ""
            }
        }.toMutableMap()
        return result + dates
    }
}