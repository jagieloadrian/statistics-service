package com.anjo.statisticservice.routing

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    validatorExceptionHandler()
    routing {
        swaggerEndpoint()
    }
}
