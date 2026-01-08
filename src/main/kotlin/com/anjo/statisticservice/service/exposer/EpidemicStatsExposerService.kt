@file:OptIn(ExperimentalCoroutinesApi::class)

package com.anjo.statisticservice.service.exposer

import com.anjo.statisticservice.model.dto.HumanType
import com.anjo.statisticservice.model.responsedto.EpidemicPoint
import com.anjo.statisticservice.model.responsedto.EpidemicPointByType
import com.anjo.statisticservice.model.responsedto.EpidemicRun
import com.anjo.statisticservice.model.responsedto.EpidemicShortRun
import com.anjo.statisticservice.model.responsedto.EpidemicSummary
import com.anjo.statisticservice.model.responsedto.RunMeta
import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.utils.ApplicationConstants.EPIDEMIC_KEYS
import com.anjo.statisticservice.utils.ApplicationConstants.getEpidemicKey
import com.anjo.statisticservice.utils.DbKeyConstants.BY_TYPE_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.DEAD_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.DEVICE_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.ENDED_AT_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.EXPOSED_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.GENERATION_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.INFECTED_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.LOCKDOWN_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.MOBILITY_MULTIPLICATION_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.POPULATION_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.RECOVERED_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.RUN_ID_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.STARTED_AT_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.SUSCEPTIBLE_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import com.anjo.statisticservice.utils.StringConstants
import com.anjo.statisticservice.utils.StringConstants.ZERO
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class EpidemicStatsExposerService(private val repository: StatsRepository) {

    private val log = KotlinLogging.logger {}
    suspend fun getEpidemicRuns(): List<EpidemicShortRun> {
        val keys = repository.getKeyStats(EPIDEMIC_KEYS).toList()
        log.info { "Obtained: ${keys.size} keys" }
        val databaseData = repository.getStatsByKeys(keys)
        val mapped = databaseData
            .map { (key, value) ->
                key to value.toList()
            }
            .associateBy({ it.first }, { it.second })
            .map {
                it.value.reduceList()
            }
        return mapped.map { value ->
            EpidemicShortRun(
                runId = value[RUN_ID_KEY] ?: StringConstants.EMPTY_STRING,
                deviceId = value[DEVICE_ID_KEY] ?: StringConstants.EMPTY_STRING,
                startedAt = value[STARTED_AT_KEY]?.let { LocalDateTime.parse(it) } ?: Clock.System.now()
                    .toLocalDateTime(TimeZone.UTC),
                endedAt = value[ENDED_AT_KEY]?.let { LocalDateTime.parse(it) },
                population = value[POPULATION_KEY]?.toInt() ?: 0,
                duration = value[GENERATION_KEY]?.toInt() ?: 0,
                peakInfected = value[INFECTED_KEY]?.toInt() ?: 0,
            )
        }
    }

    suspend fun getEpidemicRun(deviceId: String, runId: String): EpidemicRun {
        val data = getData(deviceId, runId)
        val population = data
            .mapNotNull { value ->
                (value[POPULATION_KEY] ?: ZERO).toInt()
            }
            .toList()
            .max()

        val timestamps = data.toList().getStartedAndEndedDates()
        val meta = RunMeta(
            deviceId = deviceId,
            population = population,
            startedAt = timestamps[STARTED_AT_KEY]?.let { LocalDateTime.parse(it) } ?: Clock.System.now()
                .toLocalDateTime(TimeZone.UTC),
            endedAt = timestamps[ENDED_AT_KEY]?.let { LocalDateTime.parse(it) },
        )
        val timeline = data.map { value ->
            EpidemicPoint(
                generation = value[GENERATION_KEY]?.toInt() ?: 0,
                infected = value[INFECTED_KEY]?.toInt() ?: 0,
                recovered = value[RECOVERED_KEY]?.toInt() ?: 0,
                susceptible = value[SUSCEPTIBLE_KEY]?.toInt() ?: 0,
                exposed = value[EXPOSED_KEY]?.toInt() ?: 0,
                dead = value[DEAD_KEY]?.toInt() ?: 0,
                lockdown = value[LOCKDOWN_KEY]?.toBoolean() ?: false,
                mobilityMultiplier = value[MOBILITY_MULTIPLICATION_KEY]?.toDouble() ?: 0.0,
                byType = unparseByTypeKeys(value)
            )
        }
            .toList()
        return EpidemicRun(runId = runId, meta = meta, timeline = timeline)
    }

    suspend fun getEpidemicRunSummary(runId: String, deviceId: String): EpidemicSummary {
        val data = getData(deviceId, runId)
        val (peakInfected, peakTime) = data.toList()
            .mapIndexed { index, map ->
                index to map[INFECTED_KEY]?.toInt()
            }
            .map { it.second }

        val (totalRecovered, totaDead) = data.map { value -> unparseByTypeKeys(value) }
            .map { it.values.map { (_, _, _, recovered, dead) -> recovered to dead } }
            .flatMapConcat { it.asFlow() }
            .fold(0 to 0) { (lAcc, rAcc), (left, right) ->
                (lAcc + left) to (rAcc + right)
            }

        val duration = data.map { value ->
            value[GENERATION_KEY]?.toInt()
        }.filterNotNull()
            .toList()
            .max()

        return EpidemicSummary(
            duration = duration,
            peakInfected = peakInfected ?: 0,
            timeToPeak = peakTime ?: 0,
            finalRecovered = totalRecovered,
            finalDead = totaDead
        )

    }

    private fun getData(deviceId: String, runId: String): Flow<Map<String, String>> {
        val key = getEpidemicKey(deviceId, runId)
        val data = repository.getStats(key)
        return data
    }

    private fun unparseByTypeKeys(value: Map<String, String>): Map<String, EpidemicPointByType> {
        return HumanType.entries.associate { entry ->
            entry.name to EpidemicPointByType(
                infected = value["$BY_TYPE_KEY:${entry.name}:$INFECTED_KEY"]?.toInt() ?: 0,
                susceptible = value["$BY_TYPE_KEY:${entry.name}:$SUSCEPTIBLE_KEY"]?.toInt() ?: 0,
                exposed = value["$BY_TYPE_KEY:${entry.name}:$EXPOSED_KEY"]?.toInt() ?: 0,
                recovered = value["$BY_TYPE_KEY:${entry.name}:$RECOVERED_KEY"]?.toInt() ?: 0,
                dead = value["$BY_TYPE_KEY:${entry.name}:$DEAD_KEY"]?.toInt() ?: 0,
            )
        }
    }

    private fun List<Map<String, String>>.reduceList(): Map<String, String> {
        if (this.isEmpty()) return emptyMap()
        val keys = this.flatMap { it.keys }.toSet()
        val dates = getStartedAndEndedDates()
        val result = keys.associateWith { key ->
            when (key) {
                GENERATION_KEY -> this.mapNotNull { it[GENERATION_KEY]?.toInt() }
                    .maxOrNull()
                    ?.toString() ?: ZERO

                INFECTED_KEY -> this.mapNotNull { it[INFECTED_KEY]?.toInt() }
                    .maxOrNull()
                    ?.toString() ?: ZERO

                else -> this.firstNotNullOfOrNull { it[key] } ?: ""
            }
        }.toMutableMap()
        return result + dates
    }

    private fun List<Map<String, String>>.getStartedAndEndedDates(): Map<String, String> {
        val timestamps = this.mapNotNull {
            it[TIMESTAMP_KEY]
        }
            .filter { it.isNotBlank() }
        val startedAt = timestamps.minOrNull() ?: StringConstants.EMPTY_STRING
        val endedAt = timestamps.maxOrNull() ?: StringConstants.EMPTY_STRING
        return mapOf(
            STARTED_AT_KEY to startedAt,
            ENDED_AT_KEY to endedAt,
        )
    }
}