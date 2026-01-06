package com.anjo.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.Serializable

@Serializable
data class TemperatureDto(
    val status: String,
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val timestamp: LocalDateTime,
    val temperature: Float,
    val humidity: Float?
)