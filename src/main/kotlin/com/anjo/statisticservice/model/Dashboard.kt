package com.anjo.statisticservice.model

import kotlinx.serialization.Serializable

@Serializable
data class Threshold(val value: Double, val color: String)

@Serializable
data class WidgetOptions(
    val x: String? = null,
    val unit: String? = null,
    val colors: Map<String, String> = emptyMap(),
    val min: Double? = null,
    val max: Double? = null,
    val thresholds: List<Threshold> = emptyList(),
    val decimals: Int? = null,
)

@Serializable
data class Widget(
    val id: String,
    val title: String = "",
    val type: String,
    val w: Int = 6,
    val h: Int = 3,
    val query: Query,
    val options: WidgetOptions = WidgetOptions(),
)

@Serializable
data class Variable(
    val name: String,
    val type: String,
    val dataset: String? = null,
    val dimension: String? = null,
    val multi: Boolean = false,
    val default: String? = null,
    val options: List<String> = emptyList(),
)

@Serializable
data class Dashboard(
    val version: Int = 1,
    val kind: String = "dashboard",
    val id: String,
    val title: String = "",
    val visibility: String = "private",
    val refreshSeconds: Int? = null,
    val defaultRange: RelativeRange? = null,
    val variables: List<Variable> = emptyList(),
    val widgets: List<Widget> = emptyList(),
)
