package com.anjo.statisticservice.di

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.definitions.DefinitionStore
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.di.dependencies

fun Application.configureDI() {
    val cfg = environment.config
    val db = Db(
        url = cfg.property("ktor.db.url").getString(),
        user = cfg.property("ktor.db.user").getString(),
        password = cfg.property("ktor.db.password").getString(),
    ).also { it.init() }
    monitor.subscribe(ApplicationStopped) { db.close() }

    dependencies {
        provide { db }
        provide { DefinitionStore(db) }
    }
}
