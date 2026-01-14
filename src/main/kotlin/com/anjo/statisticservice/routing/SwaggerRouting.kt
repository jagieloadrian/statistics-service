package com.anjo.statisticservice.routing

import com.anjo.statisticservice.utils.ApplicationConstants
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing

fun Routing.swaggerEndpoint() {
    swaggerUI("${ApplicationConstants.API_BASE_PATH}/swagger", swaggerFile = "openapi/document.yaml")
}