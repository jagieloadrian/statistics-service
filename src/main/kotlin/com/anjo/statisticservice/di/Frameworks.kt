package com.anjo.statisticservice.di

import dev.hayden.KHealth
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.configureFrameworks() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(KHealth) {
        wrap { next ->
            hide()
            next()
        }
    }
    install(CallLogging) {
        callIdMdc("call-id")
    }
    install(ContentNegotiation) {
        json()
    }
}