package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.model.dto.Shares
import com.anjo.statisticservice.service.DefinitionService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.dashboardRoutes(definitions: DefinitionService) =
    route("/dashboards") {
        get { call.respond(definitions.summaries(Kind.DASHBOARD, call.user())) }
            .describe { summary = "List dashboards visible to the caller" }
        get("/{id}") { call.respond(definitions.dashboardDocument(call.user(), call.id)) }
            .describe { summary = "Get one dashboard" }
        put("/{id}") {
            val user = call.requireRole(ADMIN, EDITOR)
            val dashboard = call.receive<Dashboard>().also { requireSameId(call.id, it.id) }
            definitions.putDashboard(user, dashboard)
            call.respond(dashboard)
        }.describe { summary = "Create or replace a dashboard (editor: own only)" }
        delete("/{id}") {
            definitions.deleteDashboard(call.requireRole(ADMIN, EDITOR), call.id)
            call.respond(mapOf("deleted" to call.id))
        }.describe { summary = "Delete a dashboard (editor: own only)" }
        get("/{id}/shares") {
            call.respond(Shares(definitions.shares(call.requireRole(ADMIN, EDITOR), call.id)))
        }.describe { summary = "Users a dashboard is shared with (owner editor, admin)" }
        put("/{id}/shares") {
            val user = call.requireRole(ADMIN, EDITOR)
            val shares = call.receive<Shares>()
            definitions.setShares(user, call.id, shares.userIds)
            call.respond(shares)
        }.describe { summary = "Share a dashboard with users (used when visibility = users)" }
    }
