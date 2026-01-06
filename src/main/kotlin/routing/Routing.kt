package com.anjo.routing

import com.anjo.service.StatsCollectorService
import com.anjo.service.StatsExposerService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val statsCollectorService: StatsCollectorService by dependencies
    val statsExposerService: StatsExposerService by dependencies
    validationStatsRequestBody()
    validatorExceptionHandler()
    routing {
        collectStatisticRoutes(statsCollectorService)
        exposeEpidemicData(statsExposerService)
        exposeTemperatureData()
        swaggerEndpoint()
    }
}
