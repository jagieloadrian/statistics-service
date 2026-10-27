package com.anjo.statisticservice.routing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: List<String> = emptyList(),
) : RuntimeException(message)

@Serializable
data class ApiError(val error: String, val message: String, val details: List<String> = emptyList())

fun badRequest(message: String, details: List<String> = emptyList()) =
    ApiException(HttpStatusCode.BadRequest, "bad_request", message, details)

fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, "not_found", message)

fun conflict(message: String, details: List<String> = emptyList()) = ApiException(HttpStatusCode.Conflict, "conflict", message, details)

fun Application.validatorExceptionHandler() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
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
