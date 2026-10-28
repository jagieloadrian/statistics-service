package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.RecordService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import kotlin.time.Clock

fun Route.queryRoutes(
    definitions: DefinitionService,
    records: RecordService,
) {
    post("/query") {
        val query = call.receive<Query>()
        val dataset = definitions.queryableDataset(call.user(), query.dataset)
        call.respond(records.query(query, dataset, Clock.System.now()))
    }.describe { summary = "Query records of a dataset (aggregations, buckets, groupBy)" }
}
