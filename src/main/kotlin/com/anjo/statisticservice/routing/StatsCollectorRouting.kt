package com.anjo.statisticservice.routing

import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.model.dto.TemperatureDto
import com.anjo.statisticservice.service.StatsCollectorService
import com.anjo.statisticservice.utils.ApplicationConstants
import com.anjo.statisticservice.validation.isEpidemicValid
import com.anjo.statisticservice.validation.isTemperatureDtoValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Routing.collectStatisticRoutes(statsCollectorService: StatsCollectorService) {
    route("${ApplicationConstants.API_BASE_PATH}/stats/collect") {
        post("/epidemic") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val payload = call.receive<EpidemicDto>()
            statsCollectorService.saveEpidemicStats(payload)
            call.respond(HttpStatusCode.OK)
        }
        post("/temperature") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val payload = call.receive<TemperatureDto>()
            statsCollectorService.saveTemperatureStats(payload)
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun Application.validationStatsRequestBody() {
    install(RequestValidation) {
        validate<EpidemicDto> { body ->
            val (isValid, response) = isEpidemicValid(body)
            if (isValid) ValidationResult.Valid else ValidationResult.Invalid(response)
        }
        validate<TemperatureDto> { body ->
            val (isValid, response) = isTemperatureDtoValid(body)
            if (isValid) ValidationResult.Valid else ValidationResult.Invalid(response)
        }
    }
}