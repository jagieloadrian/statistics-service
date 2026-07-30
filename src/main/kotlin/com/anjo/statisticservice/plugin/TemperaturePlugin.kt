package com.anjo.statisticservice.plugin

import com.anjo.statisticservice.exception.PluginValidationException
import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.model.dto.TemperatureDto
import com.anjo.statisticservice.service.StatsCollectorService
import com.anjo.statisticservice.service.exposer.TemperatureStatsExposerService
import com.anjo.statisticservice.validation.isTemperatureDtoValid
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class TemperaturePlugin(
    private val collector: StatsCollectorService,
    private val exposer: TemperatureStatsExposerService,
) : StatPlugin {
    override val id = "temperature"

    override suspend fun collect(raw: JsonElement) {
        val dto = Json.decodeFromJsonElement<TemperatureDto>(raw)
        val (valid, reasons) = isTemperatureDtoValid(dto)
        if (!valid) throw PluginValidationException(reasons)
        collector.saveTemperatureStats(dto)
    }

    override suspend fun expose(): PluginRouteSet = PluginRouteSet {
        get("/devices") {
            call.respond(HttpStatusCode.OK, exposer.getTemperatureDevices())
        }
        get("/devices/{deviceId}") {
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val from = call.queryParameters["from"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val to = call.queryParameters["to"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val resolution = Resolution.fromQuery(call.parameters["resolution"])
            call.respond(HttpStatusCode.OK, exposer.getTemperatureSeries(deviceId, from, to, resolution))
        }
        get("/devices/{deviceId}/summary") {
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(HttpStatusCode.OK, exposer.getTemperatureDeviceSummary(deviceId))
        }
    }
}
