package com.anjo.model.responsedto

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.Serializable

@Serializable
data class EpidemicPoint(
    val gen: Int,
    val infected: Int,
    val recovered: Int,
    val susceptible: Int,
    val exposed: Int,
    val dead: Int,
    val lockdown: Boolean,
    val mobilityMultiplier: Double,
    val byType: Map<String, EpidemicPointByType> = emptyMap()
)

@Serializable
data class EpidemicPointByType(
    val infected: Int,
    val susceptible: Int,
    val exposed: Int,
    val recovered: Int,
    val dead: Int
)

@Serializable
data class RunMeta(
    val deviceId: String,
    val populationSize: Int,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val startedAt: LocalDateTime,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val endedAt: LocalDateTime
)

@Serializable
data class EpidemicRun(
    val runId: String,
    val meta: RunMeta,
    val timeline: List<EpidemicPoint>)

@Serializable
data class EpidemicShortRun(
    val runId: String,
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val startedAt: LocalDateTime,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val endedAt: LocalDateTime,
    val populationSize: Int,
    val duration: Int,
    val peakInfected: Int
)

@Serializable
data class EpidemicSummary(
    val duration: Int,
    val peakInfected: Int,
    val timeToPeak: Int,
    val finalRecovered: Int,
    val finalDead: Int

)