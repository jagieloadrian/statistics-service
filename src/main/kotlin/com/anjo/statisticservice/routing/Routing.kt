package com.anjo.statisticservice.routing

import com.anjo.statisticservice.definitions.DefinitionStore
import com.anjo.statisticservice.ingest.SourceStatus
import com.anjo.statisticservice.ingest.configureIngestRateLimit
import com.anjo.statisticservice.ingest.ingestRoutes
import com.anjo.statisticservice.records.RecordStore
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val defs: DefinitionStore by dependencies
    val records: RecordStore by dependencies
    val status: SourceStatus by dependencies
    validatorExceptionHandler()
    configureIngestRateLimit()
    routing {
        swaggerEndpoint()
        ingestRoutes(defs, records, status)
        apiRoutes(defs, records, status)
    }
}
