package com.anjo.statisticservice.model.responsedto

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.Serializable

@Serializable
data class TemperaturePoint(
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val timestamp: LocalDateTime,
    val temperature: Double,
    val humidity: Double? = null
)

@Serializable
data class TemperatureSeries(
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val from: LocalDateTime,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val to: LocalDateTime,
    val points: List<TemperaturePoint>
)

@Serializable
data class TemperatureSummary(
    val deviceId: String,
    val avgTemperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val avgHumidity: Double? = null,
    val minHumidity: Double? = null,
    val maxHumidity: Double? = null
)

@Serializable
data class TemperatureDevice(
    val deviceId: String,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime
)