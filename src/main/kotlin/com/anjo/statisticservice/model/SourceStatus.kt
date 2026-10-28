package com.anjo.statisticservice.model

import kotlinx.serialization.Serializable
import java.util.TreeMap
import kotlin.time.Instant

@Serializable
data class Rejection(
    val at: String,
    val reason: String,
    val excerpt: String,
)

@Serializable
data class Counts(
    val hour: Long,
    val day: Long,
)

@Serializable
data class StatusView(
    val state: String,
    val lastRecordAt: String?,
    val accepted: Counts,
    val rejected: Counts,
    val rejections: List<Rejection>,
)

class MinuteCounts(
    var accepted: Long = 0,
    var rejected: Long = 0,
)

class SourceActivity {
    val countsByEpochMinute = TreeMap<Long, MinuteCounts>()
    var lastRecordAt: Instant? = null
    val recentAttemptsRejected = ArrayDeque<Boolean>()
    val newestRejections = ArrayDeque<Rejection>()
}
