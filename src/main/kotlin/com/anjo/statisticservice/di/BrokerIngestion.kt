package com.anjo.statisticservice.di

import com.anjo.statisticservice.broker.BrokerConsumer
import com.anjo.statisticservice.broker.JetStreamBroker
import com.anjo.statisticservice.broker.KafkaBroker
import com.anjo.statisticservice.model.BrokerConfig
import com.anjo.statisticservice.model.JetStreamConfig
import com.anjo.statisticservice.model.KafkaConfig
import com.anjo.statisticservice.service.IngestService
import com.anjo.statisticservice.service.SourceStatusService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.plugins.di.dependencies
import io.micrometer.core.instrument.MeterRegistry

fun Application.configureBrokerIngestion(
    config: BrokerConfig?,
    registry: MeterRegistry,
) {
    if (config == null) return
    val ingest: IngestService by dependencies
    val status: SourceStatusService by dependencies
    dependencies {
        provide {
            when (config) {
                is KafkaConfig -> BrokerConsumer("kafka", { KafkaBroker(config) }, ingest, status, registry)
                is JetStreamConfig -> BrokerConsumer("jetstream", { JetStreamBroker(config) }, ingest, status, registry)
            }
        }
    }
    val consumer: BrokerConsumer by dependencies
    monitor.subscribe(ApplicationStarted) { consumer.start() }
}
