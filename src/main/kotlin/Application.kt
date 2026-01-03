package com.anjo

import com.anjo.di.configureDI
import com.anjo.di.configureFrameworks
import com.anjo.routing.configureRouting
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureFrameworks()
    configureDI()
    configureRouting()
}
