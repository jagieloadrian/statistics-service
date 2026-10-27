package com.anjo.statisticservice

import com.anjo.statisticservice.definitions.Dataset
import com.anjo.statisticservice.definitions.DefinitionStore
import com.anjo.statisticservice.definitions.Kind
import com.anjo.statisticservice.di.configureDI
import com.anjo.statisticservice.di.configureFrameworks
import com.anjo.statisticservice.records.RecordStore
import com.anjo.statisticservice.routing.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain.main
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

fun main(args: Array<String>) {
    main(args)
}

fun Application.module() {
    configureFrameworks()
    configureDI()
    configureRouting()
    launchRetention()
}

private fun Application.launchRetention() {
    val defs: DefinitionStore by dependencies
    val records: RecordStore by dependencies
    launch {
        while (isActive) {
            runCatching { records.purgeRetention(defs.list(Kind.DATASET, Dataset.serializer())) }
                .onFailure { log.warn("Retention purge failed", it) }
            delay(1.hours)
        }
    }
}
