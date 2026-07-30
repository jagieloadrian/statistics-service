package com.anjo.statisticservice.routing

import com.anjo.statisticservice.plugin.PluginRegistry
import com.anjo.statisticservice.utils.ApplicationConstants
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun Routing.pluginCollectRoute(registry: PluginRegistry) {
    post("${ApplicationConstants.API_BASE_PATH}/stats/collect/{pluginId}") {
        val pluginId = call.parameters["pluginId"]!!
        val plugin = registry.resolve(pluginId)
        val raw = Json.parseToJsonElement(call.receiveText())
        plugin.collect(raw)
        call.respond(HttpStatusCode.OK)
    }
}

fun Routing.pluginExposeRoutes(registry: PluginRegistry) {
    registry.all().forEach { plugin ->
        route("${ApplicationConstants.API_BASE_PATH}/stats/expose/${plugin.id}") {
            runBlocking { plugin.expose() }.configure(this)
        }
    }
    // fallback: only reached when no known plugin's literal prefix above matched
    route("${ApplicationConstants.API_BASE_PATH}/stats/expose/{pluginId}") {
        get("{...}") {
            registry.resolve(call.parameters["pluginId"]!!)
        }
    }
}
