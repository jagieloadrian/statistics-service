package com.anjo.statisticservice.model.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Series(
    val dims: Map<String, String>,
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
)

@Serializable
data class QueryResult(
    val bucket: String,
    val series: List<Series>,
    val truncated: Boolean = false,
)

@Serializable
data class DimensionValue(
    val value: String,
    val firstSeen: String,
    val lastSeen: String,
    val count: Long,
)
