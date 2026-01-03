package com.anjo.routing

import com.anjo.utils.ApplicationConstants.API_BASE_PATH
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing

fun Routing.swaggerEndpoint() {
    swaggerUI("$API_BASE_PATH/swagger", swaggerFile = "openapi/document.yaml")
}