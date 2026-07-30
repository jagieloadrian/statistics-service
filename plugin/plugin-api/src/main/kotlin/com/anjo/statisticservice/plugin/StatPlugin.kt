package com.anjo.statisticservice.plugin

import kotlinx.serialization.json.JsonElement

interface StatPlugin {
    val id: String

    suspend fun collect(raw: JsonElement) {}

    suspend fun expose(): PluginRouteSet
}
