package com.anjo.statisticservice.broker

import com.anjo.statisticservice.model.BrokerMessage
import com.anjo.statisticservice.model.Delivery
import com.anjo.statisticservice.service.DefinitionService
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.RecordService
import com.anjo.statisticservice.service.SourceStatusService
import com.anjo.statisticservice.testutils.Pg
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

private class HangingBroker : Broker {
    val polling = CountDownLatch(1)
    val released = CountDownLatch(1)

    override fun connect() = Unit

    override fun poll(handle: (BrokerMessage) -> Delivery): Delivery {
        polling.countDown()
        released.await()
        return Delivery.DONE
    }

    override fun isConsuming() = true

    override fun pending() = 0L

    override fun close() = Unit
}

class BrokerConsumerTest :
    FunSpec({
        val db = Pg.db()
        val status = SourceStatusService()
        val ingest = IngestService(DefinitionService(db, status), RecordService(db), status)

        afterSpec { db.close() }

        test("closing gives up after its timeout while a message is still being handled") {
            val broker = HangingBroker()
            val timeout = 300.milliseconds
            val consumer = BrokerConsumer("hanging", { broker }, ingest, status, SimpleMeterRegistry(), timeout)
            consumer.start()
            broker.polling.await(5, TimeUnit.SECONDS) shouldBe true
            val took = measureTime { consumer.close() }
            took shouldBeGreaterThanOrEqualTo timeout
            took shouldBeLessThan 2.seconds
            broker.released.countDown()
        }
    })
