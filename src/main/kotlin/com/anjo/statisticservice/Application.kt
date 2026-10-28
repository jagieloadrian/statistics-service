package com.anjo.statisticservice

import com.anjo.statisticservice.controller.configureRouting
import com.anjo.statisticservice.di.configureDI
import com.anjo.statisticservice.di.configureFrameworks
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.RecordService
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

fun Application.module() {
    configureFrameworks()
    configureDI()
    configureRouting()
    launchRetention()
}

private fun Application.launchRetention() {
    val defs: DefinitionService by dependencies
    val records: RecordService by dependencies
    launch {
        while (isActive) {
            runCatching { records.purgeRetention(defs.list(Kind.DATASET, Dataset.serializer())) }
                .onFailure { log.warn("Retention purge failed", it) }
            delay(1.hours)
        }
    }
}
