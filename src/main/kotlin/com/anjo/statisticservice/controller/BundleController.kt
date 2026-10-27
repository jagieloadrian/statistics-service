package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Bundle
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.service.DefinitionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post

fun Route.bundleRoutes(definitions: DefinitionService) {
    get("/export") {
        call.respond(definitions.export(call.requireRole(ADMIN, EDITOR)))
    }.describe { summary = "Export a bundle (admin: everything; editor: visible dashboards + their datasets); no credentials" }
    post("/import") {
        call.requireRole(ADMIN)
        val result = definitions.import(call.receive<Bundle>())
        call.respond(if (result.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest, result)
    }.describe { summary = "Import a bundle: upsert by kind and id, all or nothing (admin)" }
}
