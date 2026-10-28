package com.anjo.statisticservice.controller

import com.anjo.statisticservice.service.IngestService
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail

val INGEST = RateLimitName("ingest")

fun Route.ingestRoutes(ingest: IngestService) {
    rateLimit(INGEST) {
        post("$API_V2/ingest/{sourceId}") {
            val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            call.respond(ingest.ingest(call.parameters.getOrFail("sourceId"), token) { call.receiveText() })
        }.describe { summary = "Push a payload to a source (source token)" }
    }
}
