package com.anjo

import com.anjo.di.configureDI
import com.anjo.di.configureFrameworks
import com.anjo.di.registerShutdownHooks
import com.anjo.routing.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain.main

fun main(args: Array<String>) {
    main(args)
}

fun Application.module() {
    configureFrameworks()
    configureDI()
    configureRouting()
    registerShutdownHooks()
}
