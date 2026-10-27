package com.anjo.statisticservice.model

import kotlinx.serialization.Serializable

@Serializable
data class Rejection(val at: String, val reason: String, val excerpt: String)

@Serializable
data class Counts(val hour: Long, val day: Long)

@Serializable
data class StatusView(
    val state: String,
    val lastRecordAt: String?,
    val accepted: Counts,
    val rejected: Counts,
    val rejections: List<Rejection>,
)
