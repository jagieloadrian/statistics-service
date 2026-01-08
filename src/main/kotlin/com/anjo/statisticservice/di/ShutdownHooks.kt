package com.anjo.statisticservice.di

import com.anjo.statisticservice.repository.StatsRepository
import com.anjo.statisticservice.repository.StatsRepositoryRedisImpl
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking

fun Application.registerShutdownHooks() {
    embeddedServer(Netty).monitor.subscribe(ApplicationStopped) { appStopped ->
        val repo =
            appStopped.dependencies.getBlocking<StatsRepository>(DependencyKey<StatsRepositoryRedisImpl>()) as? StatsRepositoryRedisImpl
        repo?.close()
    }
}