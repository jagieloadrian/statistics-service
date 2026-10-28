package com.anjo.statisticservice.controller

import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.service.SourceStatusService
import com.anjo.statisticservice.service.UserService
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val API_V2 = "/api/v2"
private const val USER_AUTH = "user"
private const val INGEST_REQUESTS_PER_SECOND = 50
private const val LOGIN_ATTEMPTS_PER_MINUTE = 5

fun Application.configureRouting() {
    val auth: AuthService by dependencies
    val users: UserService by dependencies
    val definitions: DefinitionService by dependencies
    val records: RecordService by dependencies
    val ingest: IngestService by dependencies
    val status: SourceStatusService by dependencies
    install(RateLimit) {
        register(INGEST) {
            rateLimiter(limit = INGEST_REQUESTS_PER_SECOND, refillPeriod = 1.seconds)
            requestKey { call -> call.parameters["sourceId"].orEmpty() }
        }
        register(LOGIN) {
            rateLimiter(limit = LOGIN_ATTEMPTS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
    install(Authentication) {
        bearer(USER_AUTH) { authenticate { auth.principal(it.token) } }
    }
    routing {
        get("/health") { call.respondText("{}", ContentType.Application.Json) }
        get("/ready") { call.respondText("{}", ContentType.Application.Json) }
        swaggerEndpoint()
        ingestRoutes(ingest)
        route(API_V2) {
            signInRoutes(auth)
            authenticate(USER_AUTH) {
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
