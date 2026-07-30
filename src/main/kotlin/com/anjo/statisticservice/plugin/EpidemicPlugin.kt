package com.anjo.statisticservice.plugin

import com.anjo.statisticservice.exception.PluginValidationException
import com.anjo.statisticservice.model.dto.EpidemicDto
import com.anjo.statisticservice.service.StatsCollectorService
import com.anjo.statisticservice.service.exposer.EpidemicStatsExposerService
import com.anjo.statisticservice.validation.isEpidemicValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class EpidemicPlugin(
    private val collector: StatsCollectorService,
    private val exposer: EpidemicStatsExposerService,
) : StatPlugin {
    override val id = "epidemic"

    override suspend fun collect(raw: JsonElement) {
        val dto = Json.decodeFromJsonElement<EpidemicDto>(raw)
        val (valid, reasons) = isEpidemicValid(dto)
        if (!valid) throw PluginValidationException(reasons)
        collector.saveEpidemicStats(dto)
    }

    override suspend fun expose(): PluginRouteSet = PluginRouteSet {
        get("/runs") {
            call.respond(HttpStatusCode.OK, exposer.getEpidemicRuns())
        }
        get("/device/{deviceId}/run/{runId}") {
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val runId = call.parameters["runId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(HttpStatusCode.OK, exposer.getEpidemicRun(runId = runId, deviceId = deviceId))
        }
        get("/device/{deviceId}/run/{runId}/summary") {
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val runId = call.parameters["runId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(HttpStatusCode.OK, exposer.getEpidemicRunSummary(runId = runId, deviceId = deviceId))
        }
    }
}
