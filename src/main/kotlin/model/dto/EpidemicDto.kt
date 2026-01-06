@file:OptIn(ExperimentalSerializationApi::class)

package com.anjo.model.dto

import com.anjo.serializer.HumanTypeSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class EpidemicDto(val meta: EpidemicMetaDto, val state:EpidemicStateDto)

@Serializable
data class EpidemicStateDto(
    val susceptible: Int,
    val infected: Int,
    val recovered: Int,
    val population: Int,
    @JsonNames("mobilityMul") val mobilityMultiplier: Double,
    val dead: Int,
    val exposed: Int,
    val lockdown: Boolean,
    @JsonNames("byType")
    @Serializable(with = HumanTypeSerializer::class)
    val detailedDataByType: Map<HumanType, DetailedData>
)


@Serializable
data class DetailedData(val susceptible:Int, val infected:Int, val recovered:Int, val dead:Int, val exposed:Int) {

}

@Serializable
enum class HumanType(val numberType: Int) {
    CHILD(0), ADULT(1),SENIOR(2);

    companion object {
        fun fromCode(code:Int) : HumanType {
            return entries.first { it.numberType == code }
        }
    }
}

@Serializable
data class EpidemicMetaDto(
    val deviceId: String,
    val runId: Int,
    val generation: Int,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val timestamp: LocalDateTime
)
