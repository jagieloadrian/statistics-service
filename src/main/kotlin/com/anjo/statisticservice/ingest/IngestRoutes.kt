package com.anjo.statisticservice.ingest

import com.anjo.statisticservice.definitions.DefinitionStore
import com.anjo.statisticservice.mapping.Mapper
import com.anjo.statisticservice.mapping.MappingException
import com.anjo.statisticservice.records.RecordStore
import com.anjo.statisticservice.routing.API_V2
import com.anjo.statisticservice.routing.ApiException
import com.anjo.statisticservice.routing.badRequest
import com.anjo.statisticservice.routing.conflict
import com.anjo.statisticservice.routing.notFound
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
data class IngestResult(val accepted: Int, val duplicates: Int)

val INGEST = RateLimitName("ingest")

fun Route.ingestRoutes(defs: DefinitionStore, records: RecordStore, status: SourceStatus) {
    rateLimit(INGEST) {
        post("$API_V2/ingest/{sourceId}") {
            val id = call.parameters["sourceId"]!!
            val source = defs.source(id) ?: throw notFound("Unknown source '$id'")
            val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            if (token.isNullOrEmpty() || !defs.verifyCredential(id, token)) {
                throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Missing or invalid source token")
            }
            if (source.paused) {
                call.response.header(HttpHeaders.RetryAfter, "60")
                throw ApiException(HttpStatusCode.ServiceUnavailable, "paused", "Source '$id' is paused")
            }
            val dataset = defs.dataset(source.dataset)
                ?: throw conflict("Dataset '${source.dataset}' no longer exists")
            val body = call.receiveText()
            val now = Clock.System.now()
            val mapped = try {
                Mapper.map(source, dataset, Json.parseToJsonElement(body), now)
            } catch (e: MappingException) {
                status.rejected(id, e.reasons, body, now)
                throw badRequest("Payload rejected", e.reasons)
            } catch (e: SerializationException) {
                val reasons = listOf("malformed JSON: ${e.message}")
                status.rejected(id, reasons, body, now)
                throw badRequest("Payload rejected", reasons)
            }
            val result = records.insert(mapped)
            status.accepted(id, result.accepted, now)
            call.respond(IngestResult(result.accepted, result.duplicates))
        }.describe { summary = "Push a payload to a source (source token)" }
    }
}
