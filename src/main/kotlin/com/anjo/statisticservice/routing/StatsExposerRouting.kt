package com.anjo.statisticservice.routing

import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.service.exposer.StatsExposerFacade
import com.anjo.statisticservice.utils.ApplicationConstants
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Routing.exposeEpidemicData(statsExposerFacade: StatsExposerFacade) {
    route("${ApplicationConstants.API_BASE_PATH}/stats/expose/epidemic") {
        get("/runs") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val runs = statsExposerFacade.getEpidemicRuns()
            call.respond(HttpStatusCode.OK, message = runs)
        }

        get("/device/{deviceId}/run/{runId}") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val runId = call.parameters["runId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.application.environment.log.info("Call endpoint with id: ${runId}")
            val run = statsExposerFacade.getEpidemicRun(runId = runId, deviceId = deviceId)
            call.respond(HttpStatusCode.OK, run)
        }

        get("/device/{deviceId}/run/{runId}/summary") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val runId = call.parameters["runId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.application.environment.log.info("Call endpoint with id: ${runId}")
            val run = statsExposerFacade.getEpidemicRunSummary(runId = runId, deviceId = deviceId)
            call.respond(HttpStatusCode.OK, run)
        }
    }
}

fun Routing.exposeTemperatureData(statsExposerFacade: StatsExposerFacade) {
    route("${ApplicationConstants.API_BASE_PATH}/stats/expose/temperature") {
        get("/devices") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val devices = statsExposerFacade.getTemperatureDevices()
            call.respond(HttpStatusCode.OK, devices)
        }

        get("/devices/{deviceId}") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val from = call.queryParameters["from"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val to = call.queryParameters["to"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val resolution = Resolution.fromQuery(call.parameters["resolution"])
            call.application.environment.log.info("Call endpoint with id: ${deviceId}, from: $from, to: $to, resolution: $resolution")
            val temps = statsExposerFacade.getTemperatureDevice(deviceId, from, to, resolution)
            call.respond(HttpStatusCode.OK, temps)
        }

        get("/devices/{deviceId}/summary") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.application.environment.log.info("Call endpoint with id: ${deviceId}")
            val summary = statsExposerFacade.getTemperatureDeviceSummary(deviceId)
            call.respond(HttpStatusCode.OK, summary)
        }
    }
}