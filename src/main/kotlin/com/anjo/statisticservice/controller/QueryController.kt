package com.anjo.statisticservice.controller

import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.RecordService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import kotlin.time.Clock

fun Route.queryRoutes(definitions: DefinitionService, records: RecordService) {
    post("/query") {
        val q = call.receive<Query>()
        definitions.requireDatasetAccess(call.user(), q.dataset)
        val ds = definitions.dataset(q.dataset) ?: throw badRequest("Unknown dataset '${q.dataset}'")
        call.respond(records.query(q, ds, Clock.System.now()))
    }.describe { summary = "Query records of a dataset (aggregations, buckets, groupBy)" }
}
