package com.anjo.statisticservice.di

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.Oidc
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.service.SourceStatusService
import com.anjo.statisticservice.service.UserService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking

fun Application.configureDI() {
    val cfg = environment.config
    fun setting(key: String) = cfg.propertyOrNull("ktor.auth.$key")?.getString().orEmpty()
    val local = setting("local").ifBlank { "true" }.toBoolean()
    val oidc = if (setting("oidc.issuer").isNotBlank() && setting("oidc.clientId").isNotBlank()) {
        Oidc(setting("oidc.issuer"), setting("oidc.clientId"), setting("oidc.clientSecret"), setting("oidc.name").ifBlank { "SSO" })
    } else null
    require(local || oidc != null) { "Enable local sign-in or configure OIDC_ISSUER + OIDC_CLIENT_ID" }

    val db = Db(
        url = cfg.property("ktor.db.url").getString(),
        user = cfg.property("ktor.db.user").getString(),
        password = cfg.property("ktor.db.password").getString(),
    )
    monitor.subscribe(ApplicationStopped) { db.close() }
    val adminEmail = setting("adminEmail")
    val auth = AuthService(db, local, adminEmail, oidc)
    if (runBlocking { auth.bootstrapAdmin(adminEmail, setting("adminPassword")) }) log.info("Created admin $adminEmail")

    val status = SourceStatusService()
    val definitions = DefinitionService(db, status)
    val records = RecordService(db)
    dependencies {
        provide { auth }
        provide { UserService(db) }
        provide { definitions }
        provide { records }
        provide { status }
        provide { IngestService(definitions, records, status) }
    }
}
