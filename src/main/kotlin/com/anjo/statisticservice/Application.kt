package com.anjo.statisticservice

import com.anjo.statisticservice.config.toAppConfig
import com.anjo.statisticservice.controller.configureRouting
import com.anjo.statisticservice.controller.configureStatusPages
import com.anjo.statisticservice.di.configureDI
import com.anjo.statisticservice.di.configureFrameworks
import com.anjo.statisticservice.di.configureMonitoring
import com.anjo.statisticservice.di.drainInFlightCallsOnStop
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.validation.configureRequestValidation
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import sun.misc.Signal
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours

private val RETENTION_INTERVAL = 1.hours

fun main(args: Array<String>) {
    Signal.handle(Signal("TERM")) { exitProcess(0) }
    try {
        EngineMain.main(args)
    } catch (e: Exception) {
        LoggerFactory.getLogger("com.anjo.statisticservice.Application").error(e.message, e)
        exitProcess(1)
    }
}

fun Application.module() {
    val config = environment.config.toAppConfig()
    drainInFlightCallsOnStop()
    configureMonitoring()
    configureFrameworks(config)
    configureStatusPages()
    configureRequestValidation()
    configureDI(config)
    configureRouting()
    launchRetention()
}

private fun Application.launchRetention() {
    val definitions: DefinitionService by dependencies
    val records: RecordService by dependencies
    launch {
        while (isActive) {
            try {
                records.purgeRetention(definitions.datasets())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Retention purge failed", e)
            }
            delay(RETENTION_INTERVAL)
        }
    }
}
