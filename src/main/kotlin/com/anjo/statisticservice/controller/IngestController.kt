package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.PUSH_CHANNEL
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
            val sourceId = call.parameters.getOrFail("sourceId")
            call.respond(ingest.ingest(sourceId, call.request.headers[HttpHeaders.Authorization], PUSH_CHANNEL) { call.receiveText() })
        }.describe { summary = "Push a payload to a source (source token)" }
    }
}
