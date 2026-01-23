package com.anjo.statisticservice.routing

import com.anjo.statisticservice.utils.ApplicationConstants
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routingRoot

fun Routing.swaggerEndpoint() {
    swaggerUI("${ApplicationConstants.API_BASE_PATH}/swagger") {
        info = OpenApiInfo("StatisticsService", "1.0")
        source = OpenApiDocSource.Routing(ContentType.Application.Json) {
            routingRoot.descendants()
        }
    }
}