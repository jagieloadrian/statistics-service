package com.anjo.statisticservice.broker

import com.anjo.statisticservice.model.BrokerMessage
import com.anjo.statisticservice.model.Delivery
import com.anjo.statisticservice.model.JetStreamConfig
import com.anjo.statisticservice.model.SERVICE_NAME
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.nats.client.Connection
import io.nats.client.ConsumerContext
import io.nats.client.ErrorListener
import io.nats.client.FetchConsumeOptions
import io.nats.client.Message
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.api.AckPolicy
import io.nats.client.api.ConsumerConfiguration
import io.nats.client.api.DeliverPolicy
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger {}
private val FETCH_OPTIONS =
    FetchConsumeOptions
        .builder()
        .maxMessages(BATCH_SIZE)
        .expiresIn(POLL_WAIT.inWholeMilliseconds)
        .build()

class JetStreamBroker(
    private val config: JetStreamConfig,
) : Broker {
    private var connection: Connection? = null
    private var consumer: ConsumerContext? = null
    private var pendingCount = 0L

    override fun connect() {
        val nats = Nats.connect(config.options())
        connection = nats
        val management = nats.jetStreamManagement()
        val stream = config.streamConfiguration()
        if (config.stream in management.streamNames) management.updateStream(stream) else management.addStream(stream)
        consumer = nats.getStreamContext(config.stream).createOrUpdateConsumer(config.consumerConfiguration())
    }

    override fun poll(handle: (BrokerMessage) -> Delivery): Delivery {
        val context = checkNotNull(consumer) { "JetStream consumer is not connected" }
        context.fetch(FETCH_OPTIONS).use { fetch ->
            var message = fetch.nextMessage()
            if (message == null) pendingCount = 0
            while (message != null) {
                pendingCount = message.metaData().pendingCount()
                if (handle(message.toBrokerMessage()) == Delivery.RETRY) {
                    message.nak()
                    return Delivery.RETRY
                }
                message.ack()
                message = fetch.nextMessage()
            }
        }
        return Delivery.DONE
    }

    override fun isConsuming() = connection?.status == Connection.Status.CONNECTED

    override fun pending() = pendingCount

    override fun close() {
        connection?.close()
    }

    private fun Message.toBrokerMessage() =
        BrokerMessage(
            sourceId = subject.removePrefix("${config.subjectPrefix}."),
            authorization = headers?.getLast(HttpHeaders.Authorization),
            payload = data?.decodeToString().orEmpty(),
        )
}

private val JetStreamConfig.subjects get() = "$subjectPrefix.>"

private fun JetStreamConfig.options(): Options =
    Options
        .builder()
        .server(url)
        .connectionName(SERVICE_NAME)
        .connectionTimeout(CONNECT_TIMEOUT.toJavaDuration())
        .maxReconnects(-1)
        .errorListener(LoggingErrorListener)
        .apply { if (user.isNotBlank()) userInfo(user, password) }
        .apply { if (token.isNotBlank()) token(token.toCharArray()) }
        .build()

private fun JetStreamConfig.streamConfiguration(): StreamConfiguration =
    StreamConfiguration
        .builder()
        .name(stream)
        .subjects(subjects)
        .storageType(StorageType.File)
        .maxAge(maxAge.toJavaDuration())
        .maxBytes(maxBytes)
        .build()

private fun JetStreamConfig.consumerConfiguration(): ConsumerConfiguration =
    ConsumerConfiguration
        .builder()
        .durable(durable)
        .filterSubject(subjects)
        .ackPolicy(AckPolicy.Explicit)
        .deliverPolicy(DeliverPolicy.All)
        .build()

private object LoggingErrorListener : ErrorListener {
    override fun errorOccurred(
        conn: Connection?,
        error: String?,
    ) = logger.warn { "NATS error: $error" }

    override fun exceptionOccurred(
        conn: Connection?,
        exp: Exception?,
    ) = logger.warn { "NATS exception: ${exp?.javaClass?.simpleName}: ${exp?.message}" }
}
