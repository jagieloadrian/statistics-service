package com.anjo.statisticservice.controller

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.model.dto.Health
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes(db: Db) {
    get("/health/live") { call.respond(Health.UP) }
    get("/health/ready") {
        if (db.isReachable()) call.respond(Health.UP) else call.respond(HttpStatusCode.ServiceUnavailable, Health.DOWN)
    }
}
