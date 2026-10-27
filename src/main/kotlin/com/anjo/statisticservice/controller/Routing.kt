package com.anjo.statisticservice.controller

import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.service.SourceStatusService
import com.anjo.statisticservice.service.UserService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val API_V2 = "/api/v2"

fun Application.configureRouting() {
    val auth: AuthService by dependencies
    val users: UserService by dependencies
    val definitions: DefinitionService by dependencies
    val records: RecordService by dependencies
    val ingest: IngestService by dependencies
    val status: SourceStatusService by dependencies
    validatorExceptionHandler()
    install(RateLimit) {
        register(INGEST) {
            // ponytail: fixed 50/s per source; Source.rateLimitPerSecond needs a suspend lookup the provider can't do
            rateLimiter(limit = 50, refillPeriod = 1.seconds)
            requestKey { call -> call.parameters["sourceId"] ?: "" }
        }
        register(LOGIN) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
    install(Authentication) {
        bearer("user") { authenticate { auth.principal(it.token) } }
    }
    routing {
        swaggerEndpoint()
        ingestRoutes(ingest)
        route(API_V2) {
            signInRoutes(auth)
            authenticate("user") {
                accountRoutes(auth, users)
                userRoutes(users)
                datasetRoutes(definitions, records)
                sourceRoutes(definitions, ingest, status)
                dashboardRoutes(definitions)
                bundleRoutes(definitions)
                queryRoutes(definitions, records)
            }
        }
    }
}
