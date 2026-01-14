package com.anjo.statisticservice.routing

import com.anjo.statisticservice.service.StatsCollectorService
import com.anjo.statisticservice.service.exposer.StatsExposerFacade
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val statsCollectorService: StatsCollectorService by dependencies
    val statsExposerFacade: StatsExposerFacade by dependencies
    validationStatsRequestBody()
    validatorExceptionHandler()
    routing {
        collectStatisticRoutes(statsCollectorService)
        exposeEpidemicData(statsExposerFacade)
        exposeTemperatureData(statsExposerFacade)
        swaggerEndpoint()
    }
}
