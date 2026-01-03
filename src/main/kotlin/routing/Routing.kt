package com.anjo.routing

import com.anjo.service.StatsCollectorService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val statsCollectorService: StatsCollectorService by dependencies
    validationStatsRequestBody()
    validatorExceptionHandler()
    routing {
        collectStatisticRoutes(statsCollectorService)
        swaggerEndpoint()
    }
}
