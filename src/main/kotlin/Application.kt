package com.anjo

import com.anjo.cache.configureHTTP
import com.anjo.configuration.configureMonitoring
import com.anjo.di.configureFrameworks
import com.anjo.di.configureSerialization
import com.anjo.routing.configureStatsCollectorRouting
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureFrameworks()
    configureStatsCollectorRouting()
}
