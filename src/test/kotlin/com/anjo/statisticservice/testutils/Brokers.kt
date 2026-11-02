package com.anjo.statisticservice.testutils

import io.ktor.http.HttpHeaders
import io.nats.client.Nats
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

private const val NATS_PORT = 4222

interface BrokerChannel {
    val config: Map<String, String>

    fun ready(): Boolean

    fun publish(
        sourceId: String,
        authorization: String?,
        payload: String,
    )

    fun settled(): Boolean
}

private fun uniqueId() = UUID.randomUUID().toString().take(8)

object TestKafka {
    private val container = KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0")).apply { start() }
    private val clientConfig = mapOf<String, Any>(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to container.bootstrapServers)
    private val producer =
        KafkaProducer<String, String>(
            clientConfig +
                mapOf(
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ),
        )
    private val admin = Admin.create(clientConfig)

    val unreachable = mapOf("ktor.broker.type" to "kafka", "ktor.broker.kafka.bootstrapServers" to "localhost:1")

    fun channel(): BrokerChannel {
        val id = uniqueId()
        val topic = "test.$id"
        val group = "test-$id"
        return object : BrokerChannel {
            override val config =
                mapOf(
                    "ktor.broker.type" to "kafka",
                    "ktor.broker.kafka.bootstrapServers" to container.bootstrapServers,
                    "ktor.broker.kafka.topic" to topic,
                    "ktor.broker.kafka.groupId" to group,
                )

            override fun ready() = true

            override fun publish(
                sourceId: String,
                authorization: String?,
                payload: String,
            ) {
                val headers = listOfNotNull(authorization?.let { RecordHeader(HttpHeaders.Authorization, it.toByteArray()) })
                producer.send(ProducerRecord(topic, null, sourceId, payload, headers)).get()
            }

            override fun settled(): Boolean {
                val committed = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get()
                val partitions =
                    admin
                        .describeTopics(listOf(topic))
                        .allTopicNames()
                        .get()
                        .getValue(topic)
                        .partitions()
                        .map { TopicPartition(topic, it.partition()) }
                val ends = admin.listOffsets(partitions.associateWith { OffsetSpec.latest() }).all().get()
                return partitions.all { committed[it]?.offset() == ends.getValue(it).offset() }
            }
        }
    }
}

class NatsContainer : GenericContainer<NatsContainer>(DockerImageName.parse("nats:2.15.2"))

object TestNats {
    private val container =
        NatsContainer()
            .withCommand("-js")
            .withExposedPorts(NATS_PORT)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .apply { start() }
    private val url = "nats://${container.host}:${container.getMappedPort(NATS_PORT)}"
    private val connection = Nats.connect(url)

    val unreachable = mapOf("ktor.broker.type" to "jetstream", "ktor.broker.jetstream.url" to "nats://localhost:1")

    fun channel(): BrokerChannel {
        val id = uniqueId()
        val stream = "TEST_$id"
        val prefix = "test.$id"
        val durable = "test-$id"
        return object : BrokerChannel {
            override val config =
                mapOf(
                    "ktor.broker.type" to "jetstream",
                    "ktor.broker.jetstream.url" to url,
                    "ktor.broker.jetstream.stream" to stream,
                    "ktor.broker.jetstream.subjectPrefix" to prefix,
                    "ktor.broker.jetstream.durable" to durable,
                )

            override fun ready() = durable in connection.jetStreamManagement().getConsumerNames(stream)

            override fun publish(
                sourceId: String,
                authorization: String?,
                payload: String,
            ) {
                val headers = Headers().apply { authorization?.let { add(HttpHeaders.Authorization, it) } }
                connection.jetStream().publish(
                    NatsMessage
                        .builder()
                        .subject("$prefix.$sourceId")
                        .headers(headers)
                        .data(payload.toByteArray())
                        .build(),
                )
            }

            override fun settled() =
                connection.jetStreamManagement().getConsumerInfo(stream, durable).let { it.numPending == 0L && it.numAckPending == 0L }
        }
    }
}
