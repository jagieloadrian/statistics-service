package com.anjo.statisticservice.plugin

import io.ktor.server.routing.Route

class PluginRouteSet(val configure: Route.() -> Unit)
