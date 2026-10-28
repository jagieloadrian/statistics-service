package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.dto.MappingTestRequest
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.SourceStatusService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.sourceRoutes(
    definitions: DefinitionService,
    ingest: IngestService,
    status: SourceStatusService,
) = route("/sources") {
    get {
        call.requireRole(ADMIN)
        call.respond(definitions.summaries(Kind.SOURCE))
    }.describe { summary = "List sources (admin)" }
    get("/{id}") {
        call.requireRole(ADMIN)
        call.respond(definitions.sourceDocument(call.id))
    }.describe { summary = "Get one source (admin)" }
    put("/{id}") {
        call.requireRole(ADMIN)
        val source = call.receive<Source>().also { requireSameId(call.id, it.id) }
        definitions.putSource(source)
        call.respond(source)
    }.describe { summary = "Create or replace a source (admin)" }
    delete("/{id}") {
        call.requireRole(ADMIN)
        definitions.deleteSource(call.id)
        call.respond(mapOf("deleted" to call.id))
    }.describe { summary = "Delete a source and its credential (admin)" }
    post("/{id}/credential") {
        call.requireRole(ADMIN)
        call.respond(mapOf("token" to definitions.rotateCredential(call.id)))
    }.describe { summary = "Rotate the source token; returned once, the previous one stops working (admin)" }
    get("/{id}/status") {
        call.requireRole(ADMIN)
        call.respond(status.view(definitions.requireSource(call.id)))
    }.describe { summary = "Source state, counts and recent rejections (admin)" }
    post("/{id}/mapping-test") {
        call.requireRole(ADMIN)
        call.respond(ingest.mappingTest(call.id, call.receive<MappingTestRequest>()))
    }.describe { summary = "Run a mapping against a sample payload; stores nothing (admin)" }
}
