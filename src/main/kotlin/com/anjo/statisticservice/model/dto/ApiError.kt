package com.anjo.statisticservice.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val error: String,
    val message: String,
    val details: List<String> = emptyList(),
)
