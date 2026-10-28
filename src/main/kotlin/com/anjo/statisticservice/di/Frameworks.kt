package com.anjo.statisticservice.di

import com.anjo.statisticservice.model.definitionJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.configureFrameworks() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    val corsHosts = environment.config.property("ktor.cors.hosts").getString()
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }
    install(CORS) {
        corsHosts.forEach { allowHost(it, schemes = listOf("http", "https")) }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(CallLogging) {
        callIdMdc("call-id")
    }
    install(ContentNegotiation) {
        json(definitionJson)
    }
}