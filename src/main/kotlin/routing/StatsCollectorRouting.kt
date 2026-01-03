package com.anjo.routing

import com.anjo.model.EpidemicDto
import com.anjo.service.StatsCollectorService
import com.anjo.utils.ApplicationConstants.API_BASE_PATH
import com.anjo.validation.isEpidemicValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Routing.collectStatisticRoutes(statsCollectorService: StatsCollectorService) {
    route("$API_BASE_PATH/stats") {
        post("/epidemic") {
            val payload = call.receive<EpidemicDto>()
            statsCollectorService.savedStats(payload)
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
    }
}