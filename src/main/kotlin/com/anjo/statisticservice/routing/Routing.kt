package com.anjo.statisticservice.routing

import com.anjo.statisticservice.plugin.PluginRegistry
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val pluginRegistry: PluginRegistry by dependencies
    validatorExceptionHandler()
    routing {
        pluginCollectRoute(pluginRegistry)
        pluginExposeRoutes(pluginRegistry)
        swaggerEndpoint()
    }
}
