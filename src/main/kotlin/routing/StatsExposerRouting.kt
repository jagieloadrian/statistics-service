package com.anjo.routing

import com.anjo.service.StatsExposerService
import com.anjo.utils.ApplicationConstants.API_BASE_PATH
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Routing.exposeEpidemicData(statsExposerService: StatsExposerService) {
    route("$API_BASE_PATH/stats/expose/epidemic") {
        get("/runs") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val runs = statsExposerService.getEpidemicRuns()
            call.respond(HttpStatusCode.OK, message = runs)
        }

        get("/runs/{runId}") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            call.application.environment.log.info("Call endpoint with id: ${call.pathParameters["runId"]}")
            call.respond(HttpStatusCode.OK)
        }

        get("/runs/{runId}/summary"){
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            call.application.environment.log.info("Call endpoint with id: ${call.pathParameters["runId"]}")
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun Routing.exposeTemperatureData() {
    route("$API_BASE_PATH/stats/expose/temperature") {
        get("/devices") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            call.respond(HttpStatusCode.OK)
        }

        get("/devices/{deviceId}") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            call.application.environment.log.info("Call endpoint with id: ${call.pathParameters["deviceId"]}")
            call.respond(HttpStatusCode.OK)
        }

        get("/devices/{deviceId}/summary") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            call.application.environment.log.info("Call endpoint with id: ${call.pathParameters["deviceId"]}")
            call.respond(HttpStatusCode.OK)
        }
    }
}