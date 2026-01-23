@file:OptIn(ExperimentalKtorApi::class)

package com.anjo.statisticservice.routing

import com.anjo.statisticservice.model.Resolution
import com.anjo.statisticservice.model.responsedto.EpidemicRun
import com.anjo.statisticservice.model.responsedto.EpidemicShortRun
import com.anjo.statisticservice.model.responsedto.EpidemicSummary
import com.anjo.statisticservice.model.responsedto.TemperatureDevice
import com.anjo.statisticservice.model.responsedto.TemperatureSummary
import com.anjo.statisticservice.service.exposer.StatsExposerFacade
import com.anjo.statisticservice.utils.ApplicationConstants
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

fun Routing.exposeEpidemicData(statsExposerFacade: StatsExposerFacade) {
    route("${ApplicationConstants.API_BASE_PATH}/stats/expose/epidemic") {
        get("/runs") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val runs = statsExposerFacade.getEpidemicRuns()
            call.respond(HttpStatusCode.OK, message = runs)
        }.describe {
            tag("Epidemic")
            summary = "Get list of epidemics"
            description = "Get epidemic runs"

            responses {
                HttpStatusCode.OK {
                    description = "received data"
                    schema = jsonSchema<List<EpidemicShortRun>>()
                }
            }
        }

        get("/device/{deviceId}/run/{runId}") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val runId = call.parameters["runId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.application.environment.log.info("Call endpoint with id: ${runId}")
            val run = statsExposerFacade.getEpidemicRun(runId = runId, deviceId = deviceId)
            call.respond(HttpStatusCode.OK, run)
        }.describe {
            tag("Epidemic")
            summary = "Get epidemic run info"
            description = "Get specific epidemic info with time points"
            parameters {
                path("deviceId") {
                    description = "Device Id"
                    required = true
                    allowEmptyValue = false
                }
                path("runId") {
                    description = "Run Id"
                    required = true
                    allowEmptyValue = false
                }
            }

            responses {
                HttpStatusCode.OK {
                    description = "return data"
                    schema = jsonSchema<EpidemicRun>()
                }
                HttpStatusCode.BadRequest {
                    description = "lack of param"
                    schema = jsonSchema<String>()
                }
            }
        }

        get("/device/{deviceId}/run/{runId}/summary") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val runId = call.parameters["runId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.application.environment.log.info("Call endpoint with id: ${runId}")
            val run = statsExposerFacade.getEpidemicRunSummary(runId = runId, deviceId = deviceId)
            call.respond(HttpStatusCode.OK, run)
        }.describe {
            tag("Epidemic")
            summary = "Get epidemic run summary info"
            description = "Get specific epidemic summary info"
            parameters {
                path("deviceId") {
                    description = "Device Id"
                    required = true
                    allowEmptyValue = false
                }
                path("runId") {
                    description = "Run Id"
                    required = true
                    allowEmptyValue = false
                }
            }

            responses {
                HttpStatusCode.OK {
                    description = "return data"
                    schema = jsonSchema<EpidemicSummary>()
                }
                HttpStatusCode.BadRequest {
                    description = "lack of param"
                    schema = jsonSchema<String>()
                }
            }
        }
    }
}

fun Routing.exposeTemperatureData(statsExposerFacade: StatsExposerFacade) {
    route("${ApplicationConstants.API_BASE_PATH}/stats/expose/temperature") {
        get("/devices") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val devices = statsExposerFacade.getTemperatureDevices()
            call.respond(HttpStatusCode.OK, devices)
        }.describe {
            tag("Temperature")
            summary = "Get temperatures devices"
            description = "Get list of devices which provide info about temperatures"

            responses {
                HttpStatusCode.OK {
                    description = "received data"
                    schema = jsonSchema<List<TemperatureDevice>>()
                }
            }
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
        }.describe {
            tag("Temperature")
            summary = "Get temperature series info "
            description = "Get specific temperature series with information of data"
            parameters {
                path("deviceId") {
                    description = "Device Id"
                    required = true
                    allowEmptyValue = false
                }
                query("from") {
                    description = "starting time point of searching data"
                    required = true
                    allowEmptyValue = false

                }
                query("to") {
                    description = "finish time point of searching data"
                    required = true
                    allowEmptyValue = false
                }
                query("resolution") {
                    description = "resolution time of searching data"
                    required = false
                    allowEmptyValue = true
                    schema = jsonSchema<Resolution>()
                }
            }

            responses {
                HttpStatusCode.OK {
                    description = "return data"
                    schema = jsonSchema<TemperatureSummary>()
                }
                HttpStatusCode.BadRequest {
                    description = "lack of param"
                    schema = jsonSchema<String>()
                }
            }
        }

        get("/devices/{deviceId}/summary") {
            call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri}")
            val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.application.environment.log.info("Call endpoint with id: ${deviceId}")
            val summary = statsExposerFacade.getTemperatureDeviceSummary(deviceId)
            call.respond(HttpStatusCode.OK, summary)
        }.describe {
            tag("Temperature")
            summary = "Get temperature run info summary"
            description = "Get specific temperature info with summary of data"
            parameters {
                path("deviceId") {
                    description = "Device Id"
                    required = true
                    allowEmptyValue = false
                }
            }

            responses {
                HttpStatusCode.OK {
                    description = "return data"
                    schema = jsonSchema<TemperatureSummary>()
                }
                HttpStatusCode.BadRequest {
                    description = "lack of param"
                    schema = jsonSchema<String>()
                }
            }
        }
    }
}