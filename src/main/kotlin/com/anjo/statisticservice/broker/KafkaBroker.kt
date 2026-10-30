package com.anjo.statisticservice.broker

import com.anjo.statisticservice.model.BrokerMessage
import com.anjo.statisticservice.model.Delivery
import com.anjo.statisticservice.model.KafkaConfig
import io.ktor.http.HttpHeaders
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties

private val POLL_TIMEOUT = Duration.ofSeconds(1)
private val CLOSE_TIMEOUT = Duration.ofSeconds(3)
private const val MAX_POLL_RECORDS = 100
private const val CONNECT_TIMEOUT_MS = 2_000
private const val REQUEST_TIMEOUT_MS = 10_000
private const val RECONNECT_BACKOFF_MAX_MS = 10_000

class KafkaBroker(
    private val config: KafkaConfig,
) : Broker {
    private val consumer = KafkaConsumer<String?, String?>(config.properties())

    override fun connect() = consumer.subscribe(listOf(config.topic))

    override fun poll(handle: (BrokerMessage) -> Delivery): Delivery {
        val records = consumer.poll(POLL_TIMEOUT)
        val handled = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        var delivery = Delivery.DONE
        for (record in records) {
            delivery = handle(record.toMessage())
            if (delivery == Delivery.RETRY) break
            handled[TopicPartition(record.topic(), record.partition())] = OffsetAndMetadata(record.offset() + 1)
        }
        if (handled.isNotEmpty()) consumer.commitSync(handled)
        if (delivery == Delivery.RETRY) {
            records.partitions().forEach { partition ->
                consumer.seek(partition, handled[partition]?.offset() ?: records.records(partition).first().offset())
            }
        }
        return delivery
    }

    override fun isConsuming() = consumer.assignment().isNotEmpty()

    override fun pending() = consumer.assignment().sumOf { consumer.currentLag(it).orElse(0) }

    override fun close() = consumer.close(CloseOptions.timeout(CLOSE_TIMEOUT))

    private fun ConsumerRecord<String?, String?>.toMessage() =
        BrokerMessage(
            sourceId = key().orEmpty(),
            authorization = headers().lastHeader(HttpHeaders.Authorization)?.value()?.decodeToString(),
            payload = value().orEmpty(),
        )
}

private fun KafkaConfig.properties() =
    Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLL_RECORDS)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG, CONNECT_TIMEOUT_MS)
        put(CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG, CONNECT_TIMEOUT_MS)
        put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS)
        put(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS)
        put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, RECONNECT_BACKOFF_MAX_MS)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol)
        if (usesSasl) {
            put(SaslConfigs.SASL_MECHANISM, saslMechanism)
            put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig())
        }
    }

private fun KafkaConfig.jaasConfig(): String {
    val loginModule =
        if (saslMechanism == "PLAIN") {
            "org.apache.kafka.common.security.plain.PlainLoginModule"
        } else {
            "org.apache.kafka.common.security.scram.ScramLoginModule"
        }
    return "$loginModule required username=\"${user.jaasQuoted()}\" password=\"${password.jaasQuoted()}\";"
}

private fun String.jaasQuoted() = replace("\\", "\\\\").replace("\"", "\\\"")
