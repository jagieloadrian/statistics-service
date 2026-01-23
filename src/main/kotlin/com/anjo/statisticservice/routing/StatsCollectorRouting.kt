@file:OptIn(ExperimentalKtorApi::class)

package com.anjo.statisticservice.routing

import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.model.dto.TemperatureDto
import com.anjo.statisticservice.service.StatsCollectorService
import com.anjo.statisticservice.utils.ApplicationConstants
import com.anjo.statisticservice.validation.isEpidemicValid
import com.anjo.statisticservice.validation.isTemperatureDtoValid
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

fun Routing.collectStatisticRoutes(statsCollectorService: StatsCollectorService) {
    route("${ApplicationConstants.API_BASE_PATH}/stats/collect") {
        post("/epidemic") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val payload = call.receive<EpidemicDto>()
            statsCollectorService.saveEpidemicStats(payload)
            call.respond(HttpStatusCode.OK)
        }.describe {
            tag("Epidemic")
            summary = "Post epidemic data"
            description = "Post Epidemic data by microcontroller"

            requestBody {
                description = "input data"
                schema = jsonSchema<EpidemicDto>()
            }

            responses {
                HttpStatusCode.OK {
                    description = "received data"
                }
                HttpStatusCode.BadRequest {
                    description = "invalid request body"
                    schema = jsonSchema<List<String>>()
                }
            }
        }
        post("/temperature") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val payload = call.receive<TemperatureDto>()
            statsCollectorService.saveTemperatureStats(payload)
            call.respond(HttpStatusCode.OK)
        }.describe {
            tag("Temperature")
            summary = "Post temperature data"
            description = "Post temperature data by microcontroller"

            requestBody {
                description = "input data"
                schema = jsonSchema<TemperatureDto>()
            }

            responses {
                HttpStatusCode.OK {
                    description = "received data"
                }
                HttpStatusCode.BadRequest {
                    description = "invalid request body"
                    schema = jsonSchema<List<String>>()
                }
            }
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