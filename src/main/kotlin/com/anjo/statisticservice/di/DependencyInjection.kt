package com.anjo.statisticservice.di

import com.anjo.statisticservice.auth.AuthStore
import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.definitions.DefinitionStore
import com.anjo.statisticservice.ingest.SourceStatus
import com.anjo.statisticservice.records.RecordStore
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking

fun Application.configureDI() {
    val cfg = environment.config
    val db = Db(
        url = cfg.property("ktor.db.url").getString(),
        user = cfg.property("ktor.db.user").getString(),
        password = cfg.property("ktor.db.password").getString(),
    ).also { it.init() }
    monitor.subscribe(ApplicationStopped) { db.close() }
    val auth = AuthStore(db)
    val adminEmail = cfg.propertyOrNull("ktor.auth.adminEmail")?.getString().orEmpty()
    val adminPassword = cfg.propertyOrNull("ktor.auth.adminPassword")?.getString().orEmpty()
    // startup only: the first admin must exist before the first login
    if (runBlocking { auth.bootstrapAdmin(adminEmail, adminPassword) }) log.info("Created admin $adminEmail")

    dependencies {
        provide { db }
        provide { DefinitionStore(db) }
        provide { RecordStore(db) }
        provide { SourceStatus() }
        provide { auth }
    }
}
