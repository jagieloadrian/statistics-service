package com.anjo.statisticservice

import com.anjo.statisticservice.di.configureDI
import com.anjo.statisticservice.di.configureFrameworks
import com.anjo.statisticservice.di.registerShutdownHooks
import com.anjo.statisticservice.routing.configureRouting
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
