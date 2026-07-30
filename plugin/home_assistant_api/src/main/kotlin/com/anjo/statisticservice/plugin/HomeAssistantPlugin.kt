package com.anjo.statisticservice.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get

class HomeAssistantPlugin : StatPlugin {
    override val id = "home-assistant"

    override suspend fun expose(): PluginRouteSet = PluginRouteSet {
        get("/") {
            call.respond(HttpStatusCode.OK, emptyList<String>())
        }
    }
}
