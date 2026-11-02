package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.RecordService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import io.ktor.server.util.getOrFail

private const val DEFAULT_PAGE_SIZE = 50

fun Route.datasetRoutes(
    definitions: DefinitionService,
    records: RecordService,
) {
    get("/datasets") {
        call.requireRole(ADMIN, EDITOR)
        call.respond(definitions.summaries(Kind.DATASET))
    }.describe { summary = "List datasets (admin, editor)" }
    get("/datasets/{id}") {
        call.respond(definitions.datasetDocument(call.user(), call.id))
    }.describe { summary = "Get one dataset" }
    put("/datasets/{id}") {
        call.requireRole(ADMIN)
        val dataset = call.receive<Dataset>().also { requireSameId(call.id, it.id) }
        definitions.putDataset(dataset, call.force)
        call.respond(dataset)
    }.describe { summary = "Create or replace a dataset (admin)" }
    delete("/datasets/{id}") {
        call.requireRole(ADMIN)
        definitions.deleteDataset(call.id, call.force)
        call.respond(mapOf("deleted" to call.id))
    }.describe { summary = "Delete a dataset and its records (admin)" }
    get("/datasets/{id}/dimensions/{dim}/values") {
        val dataset = definitions.accessibleDataset(call.user(), call.id)
        val query = call.request.queryParameters
        val values =
            records.dimensionValues(
                ds = dataset,
                dim = call.parameters.getOrFail("dim"),
                search = query["search"],
                limit = query["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE,
                offset = query["offset"]?.toIntOrNull() ?: 0,
            )
        call.respond(values)
    }.describe { summary = "Distinct values of a dimension with first/last seen, newest first" }
}
