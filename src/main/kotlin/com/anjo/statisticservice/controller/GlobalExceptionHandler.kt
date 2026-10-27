package com.anjo.statisticservice.controller

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.dto.ApiError
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException

private val logger = KotlinLogging.logger {}

fun Application.validatorExceptionHandler() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            // only a paused source answers 503
            if (e.status == HttpStatusCode.ServiceUnavailable) call.response.header(HttpHeaders.RetryAfter, "60")
            call.respond(e.status, ApiError(e.code, e.message, e.details))
        }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", e.cause?.message ?: e.message ?: "Bad request"))
        }
        exception<SerializationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", e.message ?: "Malformed JSON"))
        }
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", e.message ?: "Bad request"))
        }
        exception<Throwable> { call, e ->
            logger.error(e) { "Unhandled error on ${call.request.local.uri}" }
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Internal error"))
        }
    }
}
