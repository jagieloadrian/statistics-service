package com.anjo.statisticservice.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

const val PUSH_CHANNEL = "push"
const val PUBSUB_CHANNEL = "pubsub"
val CHANNEL_TYPES = setOf(PUSH_CHANNEL, PUBSUB_CHANNEL)

@Serializable
data class Channel(
    val type: String = PUSH_CHANNEL,
)

@Serializable
data class TimestampSpec(
    val path: String,
    val format: String = "iso",
    val maxAgeHours: Int? = null,
)

@Serializable
data class FieldSpec(
    val path: String? = null,
    @SerialName("const") val constValue: JsonPrimitive? = null,
    val required: Boolean = true,
    val notBlank: Boolean = true,
    val min: Double? = null,
    val max: Double? = null,
    val scale: Double? = null,
)

@Serializable
data class Rule(
    val each: String? = null,
    val timestamp: TimestampSpec? = null,
    val identity: List<String> = emptyList(),
    val dims: Map<String, FieldSpec> = emptyMap(),
    val vals: Map<String, FieldSpec> = emptyMap(),
    @SerialName("const") val constDims: Map<String, String> = emptyMap(),
)

@Serializable
data class Source(
    val version: Int = DEFINITION_VERSION,
    val kind: String = "source",
    val id: String,
    val label: String? = null,
    val channel: Channel = Channel(),
    val paused: Boolean = false,
    val rateLimitPerSecond: Int = 50,
    val silentAfterMinutes: Int = 10,
    val dataset: String,
    val mapping: List<Rule> = emptyList(),
)
