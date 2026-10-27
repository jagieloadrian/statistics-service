package com.anjo.statisticservice.exception

import io.ktor.http.HttpStatusCode

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: List<String> = emptyList(),
) : RuntimeException(message)

fun badRequest(message: String, details: List<String> = emptyList()) =
    ApiException(HttpStatusCode.BadRequest, "bad_request", message, details)

fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, "not_found", message)

fun conflict(message: String, details: List<String> = emptyList()) = ApiException(HttpStatusCode.Conflict, "conflict", message, details)

fun forbidden(message: String = "Not allowed for your role") = ApiException(HttpStatusCode.Forbidden, "forbidden", message)

fun unauthorized(message: String) = ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)
