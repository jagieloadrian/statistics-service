package com.anjo.statisticservice.controller

import com.anjo.statisticservice.exception.notFound
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

// admin writes; editors read them to build widgets; viewers only the ones their dashboards use
fun Route.datasetRoutes(definitions: DefinitionService, records: RecordService) {
    get("/datasets") {
        call.requireRole(ADMIN, EDITOR)
        call.respond(definitions.summaries(Kind.DATASET))
    }.describe { summary = "List datasets (admin, editor)" }
    get("/datasets/{id}") {
        definitions.requireDatasetAccess(call.user(), call.id)
        call.respond(definitions.json(Kind.DATASET, call.id) ?: throw notFound("Unknown dataset '${call.id}'"))
    }.describe { summary = "Get one dataset" }
    put("/datasets/{id}") {
        call.requireRole(ADMIN)
        val d = call.receive<Dataset>().also { requireSameId(call.id, it.id) }
        definitions.putDataset(d, call.force)
        call.respond(d)
    }.describe { summary = "Create or replace a dataset (admin)" }
    delete("/datasets/{id}") {
        call.requireRole(ADMIN)
        definitions.deleteDataset(call.id, call.force)
        call.respond(mapOf("deleted" to call.id))
    }.describe { summary = "Delete a dataset and its records (admin)" }
    get("/datasets/{id}/dimensions/{dim}/values") {
        definitions.requireDatasetAccess(call.user(), call.id)
        val ds = definitions.dataset(call.id) ?: throw notFound("Unknown dataset '${call.id}'")
        val p = call.request.queryParameters
        call.respond(
            records.dimensionValues(
                ds, call.parameters["dim"]!!, p["search"],
                p["limit"]?.toIntOrNull() ?: 50, p["offset"]?.toIntOrNull() ?: 0,
            ),
        )
    }.describe { summary = "Distinct values of a dimension with first/last seen, newest first" }
}
