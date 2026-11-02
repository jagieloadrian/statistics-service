package com.anjo.statisticservice.model

import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

data class Record(
    val datasetId: String,
    val ts: Instant,
    val tsEstimated: Boolean,
    val dims: Map<String, String>,
    val vals: Map<String, JsonPrimitive>,
    val ident: String?,
)
