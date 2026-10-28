package com.anjo.statisticservice.di

import com.anjo.statisticservice.observability.TRACEPARENT_HEADER
import com.anjo.statisticservice.observability.TRACE_ID_MDC_KEY
import com.anjo.statisticservice.observability.isTraceId
import com.anjo.statisticservice.observability.newTraceId
import com.anjo.statisticservice.observability.traceIdOf
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

private const val METRICS_PATH = "/metrics"
private const val HEALTH_PATH_PREFIX = "/health/"
private val PROMETHEUS_TEXT = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")

fun Application.configureMonitoring() {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        this.registry = registry
    }
    install(CallId) {
        retrieve { call -> traceIdOf(call.request.header(TRACEPARENT_HEADER)) }
        generate { newTraceId() }
        verify(::isTraceId)
    }
    install(CallLogging) {
        callIdMdc(TRACE_ID_MDC_KEY)
        disableDefaultColors()
        filter { call -> call.request.path() != METRICS_PATH && !call.request.path().startsWith(HEALTH_PATH_PREFIX) }
    }
    routing {
        get(METRICS_PATH) { call.respondText(registry.scrape(), PROMETHEUS_TEXT) }
    }
}
