package com.anjo.statisticservice.service.exposer

import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.model.responsedto.TemperatureDevice
import com.anjo.statisticservice.model.responsedto.TemperaturePoint
import com.anjo.statisticservice.model.responsedto.TemperatureSeries
import com.anjo.statisticservice.model.responsedto.TemperatureSummary
import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.utils.ApplicationConstants.TEMPERATURE_KEYS
import com.anjo.statisticservice.utils.DbKeyConstants.HUMIDITY_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TEMPERATURE_KEY
import com.anjo.statisticservice.utils.DbKeyConstants.TIMESTAMP_KEY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class TemperatureStatsExposerService(private val repository: StatsRepository) {

    suspend fun getTemperatureDevices(): List<TemperatureDevice> {
        val data = getData()
        return data.map { (key, value) ->
            TemperatureDevice(
                deviceId = key,
                firstSeen = getTimestamp(value).min(),
                lastSeen = getTimestamp(value).max()
            )
        }
    }

    suspend fun getTemperatureSeries(deviceId: String, from: String, to: String, resolution: Resolution): TemperatureSeries {
        val stats = repository.getStats(deviceId, getInstantFromString(from), getInstantFromString(to))
        val points = stats.map { value ->
            TemperaturePoint(
                deviceId = deviceId,
                timestamp = value[TIMESTAMP_KEY]?.let { LocalDateTime.parse(it) } ?: Clock.System.now()
                    .toLocalDateTime(TimeZone.UTC),
                temperature = value[TEMPERATURE_KEY]?.toDouble() ?: 0.0,
                humidity = value[HUMIDITY_KEY]?.toDouble()
            )
        }.toList()
        return if(resolution == Resolution.RAW) {
            TemperatureSeries(
                deviceId = deviceId,
                from = LocalDateTime.parse(from),
                to = LocalDateTime.parse(to),
                points = points
            )
        } else {
            val grouped = points.groupBy { entry ->
                val tsMs = entry.timestamp.toInstant(TimeZone.UTC).toEpochMilliseconds()
                (tsMs/resolution.long) * resolution.long
            }.mapNotNull { (bucketStart, entries) ->
                val temperatures = entries.map {it.temperature }
                val humidities = entries.mapNotNull { it.humidity }
                TemperaturePoint(
                    deviceId = deviceId,
                    timestamp = Instant.fromEpochMilliseconds(bucketStart).toLocalDateTime(TimeZone.UTC),
                    temperature = temperatures.average(),
                    humidity = humidities.average()
                )
            }
                .sortedBy { it.timestamp }
            TemperatureSeries(
                deviceId = deviceId,
                from = LocalDateTime.parse(from),
                to = LocalDateTime.parse(to),
                points = grouped
            )
        }

    }

    suspend fun getTemperatureDeviceSummary(deviceId: String): TemperatureSummary {
        val stats = repository.getStats(deviceId)
        val temperatures = getDoubleField(TEMPERATURE_KEY, stats)
        val humidity = getDoubleField(HUMIDITY_KEY, stats)

        return TemperatureSummary(
            deviceId = deviceId,
            avgTemperature = temperatures.average(),
            minTemperature = temperatures.minOrNull() ?: 0.0,
            maxTemperature = temperatures.maxOrNull() ?: 0.0,
            avgHumidity = humidity.average(),
            minHumidity = humidity.minOrNull() ?: 0.0,
            maxHumidity = humidity.maxOrNull() ?: 0.0,
        )
    }

    private fun getInstantFromString(from: String): Instant {
        return LocalDateTime.parse(from).toInstant(TimeZone.UTC)
    }

    private suspend fun getData(): Map<String, Flow<Map<String, String>>> {
        val keys = repository.getKeyStats(TEMPERATURE_KEYS).toList()
        val data = repository.getStatsByKeys(keys)
        return data
    }

    private suspend fun getTimestamp(value: Flow<Map<String, String>>): List<LocalDateTime> =
        value.map { it[TIMESTAMP_KEY] }.filterNotNull().map { LocalDateTime.parse(it) }
            .toList()

    private suspend fun getDoubleField(fieldName: String, value: Flow<Map<String, String>>): List<Double> =
        value.map { it[fieldName] }
            .filterNotNull()
            .map { it.toDouble() }
            .toList()
}