package com.anjo.statisticservice.controller

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.dto.ApiError
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond

private const val RETRY_AFTER_SECONDS = "60"
private val logger = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respondError(e) }
        exception<RequestValidationException> { call, e -> call.respondError(badRequest("Invalid request", e.reasons)) }
        exception<BadRequestException> { call, e ->
            logger.debug(e) { "Bad request on ${call.request.path()}" }
            call.respondError(badRequest("Malformed request"))
        }
        status(HttpStatusCode.Unauthorized) { call, _ -> call.respondError(unauthorized("Missing or invalid token")) }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respondError(ApiException(status, "rate_limited", "Too many requests, try again later"))
        }
        exception<Throwable> { call, e ->
            logger.error(e) { "Unhandled error on ${call.request.path()}" }
            call.respondError(ApiException(HttpStatusCode.InternalServerError, "internal", "Internal error"))
        }
    }
}

private suspend fun ApplicationCall.respondError(e: ApiException) {
    if (e.status == HttpStatusCode.ServiceUnavailable) response.header(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS)
    respond(e.status, ApiError(e.code, e.message, e.details))
}
