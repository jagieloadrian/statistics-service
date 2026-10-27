package com.anjo.statisticservice.routing

import com.anjo.statisticservice.auth.AuthStore
import com.anjo.statisticservice.auth.LOGIN
import com.anjo.statisticservice.auth.accountRoutes
import com.anjo.statisticservice.auth.loginRoute
import com.anjo.statisticservice.definitions.DefinitionStore
import com.anjo.statisticservice.ingest.INGEST
import com.anjo.statisticservice.ingest.SourceStatus
import com.anjo.statisticservice.ingest.ingestRoutes
import com.anjo.statisticservice.records.RecordStore
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

fun Application.configureRouting() {
    val defs: DefinitionStore by dependencies
    val records: RecordStore by dependencies
    val status: SourceStatus by dependencies
    val auth: AuthStore by dependencies
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
        ingestRoutes(defs, records, status)
        route(API_V2) {
            loginRoute(auth)
            authenticate("user") {
                accountRoutes(auth)
                apiRoutes(defs, records, status)
            }
        }
    }
}
