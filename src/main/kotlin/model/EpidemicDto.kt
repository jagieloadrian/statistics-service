package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class EpidemicDto(val meta: EpidemicMetaDto, val params: EpidemicParamsDto, val state:EpidemicStateDto)

@Serializable
data class EpidemicStateDto(val susceptible:Int, val infected:Int, val recovered:Int)

@Serializable
data class EpidemicParamsDto(val populationSize: Int, val infectionProb: Double, val infectionTtlMin:Int, val infectionTtlMax:Int)

@Serializable
data class EpidemicMetaDto(val deviceId:String, val runId:Int, val generation:Int, val timestamp: Long)
