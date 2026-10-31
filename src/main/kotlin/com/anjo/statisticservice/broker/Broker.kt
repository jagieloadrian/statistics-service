package com.anjo.statisticservice.broker

import com.anjo.statisticservice.model.BrokerMessage
import com.anjo.statisticservice.model.Delivery
import kotlin.time.Duration.Companion.seconds

internal const val BATCH_SIZE = 100
internal val POLL_WAIT = 1.seconds
internal val CONNECT_TIMEOUT = 2.seconds

interface Broker : AutoCloseable {
    fun connect()

    fun poll(handle: (BrokerMessage) -> Delivery): Delivery

    fun isConsuming(): Boolean

    fun pending(): Long
}
