package com.anjo.statisticservice.di

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopPreparing
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val DRAIN_TIMEOUT = 15.seconds
private val DRAIN_POLL_INTERVAL = 50.milliseconds

fun Application.drainInFlightCallsOnStop(timeout: Duration = DRAIN_TIMEOUT) {
    val inFlight = AtomicInteger()
    intercept(ApplicationCallPipeline.Setup) {
        inFlight.incrementAndGet()
        try {
            proceed()
        } finally {
            inFlight.decrementAndGet()
        }
    }
    monitor.subscribe(ApplicationStopPreparing) {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (inFlight.get() > 0 && deadline.hasNotPassedNow()) {
            Thread.sleep(DRAIN_POLL_INTERVAL.inWholeMilliseconds)
        }
    }
}
