package com.anjo.statisticservice.plugin

import com.anjo.statisticservice.exception.EmptyDataException

class PluginRegistry(plugins: List<StatPlugin>) {
    private val byId: Map<String, StatPlugin> = plugins.associateBy { it.id }

    init {
        require(byId.size == plugins.size) { "Duplicate plugin id in registration list" }
    }

    fun resolve(pluginId: String): StatPlugin =
        byId[pluginId] ?: throw EmptyDataException("Unknown plugin id: $pluginId")

    fun all(): Collection<StatPlugin> = byId.values
}
