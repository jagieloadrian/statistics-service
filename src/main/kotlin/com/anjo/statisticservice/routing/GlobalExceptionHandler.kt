package com.anjo.statisticservice.routing

import com.anjo.statisticservice.exception.EmptyDataException
import com.anjo.statisticservice.exception.EmptyParamException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.validatorExceptionHandler() {
    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons)
        }
        exception<EmptyParamException> { call, cause ->
            call.respond(status = HttpStatusCode.BadRequest, message = cause.message ?: cause.localizedMessage)
        }
        exception<EmptyDataException> { call, cause ->
            call.respond(status = HttpStatusCode.NotFound, message = cause.message ?: cause.localizedMessage)
        }
    }
}