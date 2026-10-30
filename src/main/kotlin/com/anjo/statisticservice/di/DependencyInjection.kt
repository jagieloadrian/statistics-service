package com.anjo.statisticservice.di

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.model.AppConfig
import com.anjo.statisticservice.model.JetStreamConfig
import com.anjo.statisticservice.model.KafkaConfig
import com.anjo.statisticservice.model.dto.PubSubInfo
import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.Oidc
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.service.SourceStatusService
import com.anjo.statisticservice.service.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

fun Application.configureDI(config: AppConfig) {
    val db = Db(config.db.url, config.db.user, config.db.password)
    monitor.subscribe(ApplicationStopped) { db.close() }
    val pubsub =
        when (val broker = config.broker) {
            is KafkaConfig -> PubSubInfo("kafka", broker.topic)
            is JetStreamConfig -> PubSubInfo("jetstream", broker.subjectPrefix)
            null -> null
        }
    val auth = AuthService(db, config.localSignIn, config.adminEmail, config.oidc?.let(::Oidc), pubsub)
    if (runBlocking { auth.bootstrapAdmin(config.adminEmail, config.adminPassword) }) logger.info { "Created admin ${config.adminEmail}" }
    val status = SourceStatusService()
    val definitions = DefinitionService(db, status)
    val records = RecordService(db)
    dependencies {
        provide { db }
        provide { auth }
        provide { UserService(db) }
        provide { definitions }
        provide { records }
        provide { status }
        provide { IngestService(definitions, records, status) }
    }
}
