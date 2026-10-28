package com.anjo.statisticservice.di

import com.anjo.statisticservice.config.AppConfig
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
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

fun Application.configureFrameworks(config: AppConfig) {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    install(CORS) {
        config.corsHosts.forEach { allowHost(it, schemes = listOf("http", "https")) }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    if (config.trustProxy) {
        install(XForwardedHeaders) { useLastProxy() }
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        callIdMdc("call-id")
    }
    install(ContentNegotiation) {
        json(definitionJson)
    }
}
