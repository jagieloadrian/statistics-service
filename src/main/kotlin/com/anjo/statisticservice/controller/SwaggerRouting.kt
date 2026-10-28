package com.anjo.statisticservice.controller

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routingRoot

fun Routing.swaggerEndpoint() {
    swaggerUI("$API_V2/swagger") {
        info = OpenApiInfo("StatisticsService", "1.0")
        source =
            OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
    }
}
