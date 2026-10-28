package com.anjo.statisticservice.model.dto

import com.anjo.statisticservice.model.Rule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class IngestResult(
    val accepted: Int,
    val duplicates: Int,
)

@Serializable
data class MappingTestRequest(
    val payload: JsonElement,
    val mapping: List<Rule>? = null,
)

@Serializable
data class MappedRecord(
    val ts: String,
    val tsEstimated: Boolean,
    val dims: Map<String, String>,
    val vals: Map<String, JsonPrimitive>,
    val ident: String?,
)

@Serializable
data class MappingTestResult(
    val records: List<MappedRecord>,
    val errors: List<String>,
)
