package com.anjo.statisticservice.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class DefinitionSummary(
    val id: String,
    val label: String?,
    val updatedAt: String,
)

@Serializable
data class ImportResult(
    val created: Int,
    val updated: Int,
    val errors: List<String> = emptyList(),
)

@Serializable
data class Shares(
    val userIds: List<Long>,
)
