package com.anjo.statisticservice.broker

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.BrokerMessage
import com.anjo.statisticservice.model.Delivery
import com.anjo.statisticservice.model.PUBSUB_CHANNEL
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.SourceStatusService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.runBlocking
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
private val MIN_BACKOFF = 1.seconds
private val MAX_BACKOFF = 30.seconds
private val UNSTORABLE_SQL_STATE_CLASSES = setOf("22", "23")
private val STATUS_UNTRACKED = setOf(HttpStatusCode.NotFound, HttpStatusCode.BadRequest)

interface Broker : AutoCloseable {
    fun connect()

    fun poll(handle: (BrokerMessage) -> Delivery): Delivery

    fun isConsuming(): Boolean

    fun pending(): Long
}

class BrokerConsumer(
    private val name: String,
    private val newBroker: () -> Broker,
    private val ingest: IngestService,
    private val status: SourceStatusService,
    registry: MeterRegistry,
) {
    private val stopped = CountDownLatch(1)
    private val worker = thread(start = false, isDaemon = true, name = "$name-consumer") { consume() }
    private val consuming = AtomicInteger()
    private val pending = AtomicLong()
    private val stored = registry.counter(MESSAGES_METRIC, BROKER_TAG, name, OUTCOME_TAG, "stored")
    private val rejected = registry.counter(MESSAGES_METRIC, BROKER_TAG, name, OUTCOME_TAG, "rejected")
    private val retried = registry.counter(MESSAGES_METRIC, BROKER_TAG, name, OUTCOME_TAG, "retried")
    private val running get() = stopped.count > 0

    init {
        registry.gauge("statistics.broker.consuming", Tags.of(BROKER_TAG, name), consuming)
        registry.gauge("statistics.broker.pending.messages", Tags.of(BROKER_TAG, name), pending)
    }

    fun start() = worker.start()

    fun stop(timeout: Duration) {
        stopped.countDown()
        worker.join(timeout.inWholeMilliseconds)
    }

    private fun consume() {
        var backoff = MIN_BACKOFF
        while (running) {
            try {
                newBroker().use { broker ->
                    broker.connect()
                    while (running) {
                        val delivery = broker.poll(::handle)
                        track(broker)
                        backoff = if (delivery == Delivery.RETRY) pause(backoff) else MIN_BACKOFF
                    }
                }
            } catch (e: Exception) {
                consuming.set(0)
                logger.warn { "$name broker unavailable (${e.javaClass.simpleName}: ${e.message}), retrying in $backoff" }
                backoff = pause(backoff)
            }
        }
        consuming.set(0)
        logger.info { "$name consumer stopped" }
    }

    private fun pause(backoff: Duration): Duration {
        stopped.await(backoff.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        return (backoff * 2).coerceAtMost(MAX_BACKOFF)
    }

    private fun track(broker: Broker) {
        val now = if (broker.isConsuming()) 1 else 0
        if (consuming.getAndSet(now) != now) {
            if (now == 1) logger.info { "$name consumer connected" } else logger.warn { "$name consumer lost its broker" }
        }
        pending.set(broker.pending())
    }

    private fun handle(message: BrokerMessage): Delivery =
        try {
            runBlocking { ingest.ingest(message.sourceId, message.authorization, PUBSUB_CHANNEL) { message.payload } }
            stored.increment()
            Delivery.DONE
        } catch (e: ApiException) {
            reject(message.sourceId, e.message, record = e.status !in STATUS_UNTRACKED)
            Delivery.DONE
        } catch (e: Exception) {
            if (e.isTransient()) {
                retried.increment()
                logger.warn { "$name message for source '${message.sourceId}' not stored (${e.javaClass.simpleName}), will retry" }
                Delivery.RETRY
            } else {
                reject(message.sourceId, "Payload cannot be stored (${e.javaClass.simpleName})", record = true)
                Delivery.DONE
            }
        }

    private fun reject(
        sourceId: String,
        reason: String,
        record: Boolean,
    ) {
        rejected.increment()
        logger.warn { "Rejected $name message for source '$sourceId': $reason" }
        if (record) status.rejected(sourceId, listOf(reason), "", Clock.System.now())
    }

    private fun Throwable.isTransient(): Boolean {
        val sqlErrors = generateSequence(this) { it.cause }.filterIsInstance<SQLException>().toList()
        val state = sqlErrors.lastOrNull { it.sqlState != null }?.sqlState
        return sqlErrors.isNotEmpty() && state?.take(2) !in UNSTORABLE_SQL_STATE_CLASSES
    }

    private companion object {
        const val MESSAGES_METRIC = "statistics.broker.messages"
        const val BROKER_TAG = "broker"
        const val OUTCOME_TAG = "outcome"
    }
}
