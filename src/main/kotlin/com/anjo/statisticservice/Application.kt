package com.anjo.statisticservice

import com.anjo.statisticservice.config.toAppConfig
import com.anjo.statisticservice.controller.configureRouting
import com.anjo.statisticservice.controller.configureStatusPages
import com.anjo.statisticservice.di.configureBrokerIngestion
import com.anjo.statisticservice.di.configureDI
import com.anjo.statisticservice.di.configureFrameworks
import com.anjo.statisticservice.di.configureMonitoring
import com.anjo.statisticservice.di.drainInFlightCallsOnStop
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.validation.configureRequestValidation
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sun.misc.Signal
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours

private val RETENTION_INTERVAL = 1.hours
private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    Signal.handle(Signal("TERM")) { exitProcess(0) }
    try {
        EngineMain.main(args)
    } catch (e: Exception) {
        logger.error(e) { e.message }
        exitProcess(1)
    }
}

fun Application.module() {
    val config = environment.config.toAppConfig()
    drainInFlightCallsOnStop()
    val registry = configureMonitoring()
    configureFrameworks(config)
    configureStatusPages()
    configureRequestValidation()
    configureDI(config)
    configureRouting()
    configureBrokerIngestion(config.broker, registry)
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
                logger.warn(e) { "Retention purge failed" }
            }
            delay(RETENTION_INTERVAL)
        }
    }
}
