package com.anjo.statisticservice.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DEFINITION_VERSION = 1

enum class Kind(
    val key: String,
) {
    DATASET("dataset"),
    SOURCE("source"),
    DASHBOARD("dashboard"),
}

@Serializable
data class Bundle(
    val version: Int = DEFINITION_VERSION,
    val datasets: List<Dataset> = emptyList(),
    val sources: List<Source> = emptyList(),
    val dashboards: List<Dashboard> = emptyList(),
)

val definitionJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
    }
