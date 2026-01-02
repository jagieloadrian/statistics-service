package com.anjo.routing

import com.anjo.model.EpidemicDto
import com.anjo.utils.PathConstants.API_BASE_PATH
import com.anjo.validation.isEpidemicValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureStatsCollectorRouting() {
    install(RequestValidation) {
        validate<EpidemicDto> { body ->
            val (isValid, response) = isEpidemicValid(body)
            if(isValid) ValidationResult.Valid else ValidationResult.Invalid(response)
        }
    }
    routing {
        get("$API_BASE_PATH/stats/epidemic") {

            call.respond(HttpStatusCode.OK)
        }
    }
}