package com.anjo.statisticservice.controller

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.dto.ApiError
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond

private val logger = KotlinLogging.logger {}
private const val RETRY_AFTER_SECONDS = "60"

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            if (e.status == HttpStatusCode.ServiceUnavailable) call.response.header(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS)
            call.respond(e.status, ApiError(e.code, e.message, e.details))
        }
        exception<RequestValidationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "Invalid request", e.reasons))
        }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", e.cause?.message ?: e.message ?: "Bad request"))
        }
        exception<Throwable> { call, e ->
            logger.error(e) { "Unhandled error on ${call.request.path()}" }
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Internal error"))
        }
    }
}
