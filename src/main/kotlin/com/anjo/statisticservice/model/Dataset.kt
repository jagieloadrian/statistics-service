package com.anjo.statisticservice.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MeasureType {
    @SerialName("number")
    NUMBER,

    @SerialName("boolean")
    BOOLEAN,

    @SerialName("text")
    TEXT,
}

@Serializable
data class Measure(
    val name: String,
    val type: MeasureType = MeasureType.NUMBER,
    val unit: String? = null,
    val label: String? = null,
)

@Serializable
data class Dimension(
    val name: String,
    val label: String? = null,
)

@Serializable
data class Dataset(
    val version: Int = DEFINITION_VERSION,
    val kind: String = "dataset",
    val id: String,
    val label: String? = null,
    val measures: List<Measure> = emptyList(),
    val dimensions: List<Dimension> = emptyList(),
    val calculated: Map<String, String> = emptyMap(),
    val retentionDays: Int? = null,
)

fun Dataset.names() = measures.map { it.name } + dimensions.map { it.name } + calculated.keys

fun Dataset.dimensionNames() = dimensions.map { it.name }.toSet()

fun Dataset.numberMeasures() = measures.filter { it.type == MeasureType.NUMBER }.map { it.name }.toSet()

fun Dataset.measureType(name: String): MeasureType? =
    if (name in calculated) MeasureType.NUMBER else measures.firstOrNull { it.name == name }?.type
