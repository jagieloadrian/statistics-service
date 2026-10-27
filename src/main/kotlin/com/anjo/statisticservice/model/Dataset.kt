package com.anjo.statisticservice.model

import kotlinx.serialization.Serializable

@Serializable
enum class MeasureType { number, boolean, text }

@Serializable
data class Measure(val name: String, val type: MeasureType = MeasureType.number, val unit: String? = null, val label: String? = null)

@Serializable
data class Dimension(val name: String, val label: String? = null)

@Serializable
data class Dataset(
    val version: Int = 1,
    val kind: String = "dataset",
    val id: String,
    val label: String? = null,
    val measures: List<Measure> = emptyList(),
    val dimensions: List<Dimension> = emptyList(),
    val calculated: Map<String, String> = emptyMap(),
    val retentionDays: Int? = null,
)

fun Dataset.names() = measures.map { it.name } + dimensions.map { it.name } + calculated.keys

fun Dataset.numberMeasures() = measures.filter { it.type == MeasureType.number }.map { it.name }.toSet()
