package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.JetStreamConfig
import com.anjo.statisticservice.model.KafkaConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.MapApplicationConfig
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private const val TYPE = "ktor.broker.type"
private const val BOOTSTRAP = "ktor.broker.kafka.bootstrapServers"
private const val NATS_URL = "ktor.broker.jetstream.url"

private fun load(vararg settings: Pair<String, String>) =
    MapApplicationConfig("ktor.db.url" to "jdbc:postgresql://localhost:5432/stats", "ktor.db.user" to "stats", *settings).toAppConfig()

private fun problems(vararg settings: Pair<String, String>): String =
    shouldThrow<IllegalStateException> { load(*settings) }.message.orEmpty()

class BrokerConfigTest :
    FunSpec({
        test("no broker unless one is chosen") {
            load().broker shouldBe null
            load(TYPE to "none").broker shouldBe null
        }

        test("an unknown broker fails fast") {
            problems(TYPE to "rabbitmq") shouldContain "BROKER must be none, kafka or jetstream, not 'rabbitmq'"
        }

        test("kafka needs bootstrap servers and fills in defaults") {
            problems(TYPE to "kafka") shouldContain "KAFKA_BOOTSTRAP_SERVERS is required when BROKER=kafka"
            load(TYPE to "kafka", BOOTSTRAP to "kafka:9092").broker shouldBe
                KafkaConfig("kafka:9092", "statistics-service", "stats.ingest", "PLAINTEXT", "PLAIN", "", "")
        }

        test("kafka SASL needs a known mechanism and credentials") {
            val kafka = arrayOf(TYPE to "kafka", BOOTSTRAP to "kafka:9092")
            val sasl = arrayOf(*kafka, "ktor.broker.kafka.securityProtocol" to "SASL_SSL")
            problems(*sasl) shouldContain "KAFKA_USER and KAFKA_PASSWORD are required with SASL"
            problems(*sasl, "ktor.broker.kafka.saslMechanism" to "GSSAPI") shouldContain "KAFKA_SASL_MECHANISM must be one of"
            problems(*kafka, "ktor.broker.kafka.user" to "stats") shouldContain "need KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT or SASL_SSL"
            problems(*kafka, "ktor.broker.kafka.topic" to "stats ingest") shouldContain "KAFKA_TOPIC must match"
            load(
                *sasl,
                "ktor.broker.kafka.saslMechanism" to "SCRAM-SHA-512",
                "ktor.broker.kafka.user" to "stats",
                "ktor.broker.kafka.password" to "secret",
            ).broker shouldBe
                KafkaConfig("kafka:9092", "statistics-service", "stats.ingest", "SASL_SSL", "SCRAM-SHA-512", "stats", "secret")
        }

        test("jetstream needs a nats url and fills in defaults") {
            problems(TYPE to "jetstream") shouldContain "NATS_URL must be a nats:// or tls:// URL when BROKER=jetstream"
            load(TYPE to "jetstream", NATS_URL to "nats://nats:4222", "ktor.broker.jetstream.maxAge" to "12h").broker shouldBe
                JetStreamConfig("nats://nats:4222", "STATS_INGEST", "stats.ingest", "statistics-service", 12.hours, 268_435_456, "", "", "")
            (load(TYPE to "jetstream", NATS_URL to "nats://nats:4222").broker as JetStreamConfig).maxAge shouldBe 7.days
        }

        test("jetstream rejects wildcards, bad limits and mixed credentials") {
            val nats = arrayOf(TYPE to "jetstream", NATS_URL to "nats://nats:4222")
            problems(*nats, "ktor.broker.jetstream.subjectPrefix" to "stats.>") shouldContain "NATS_SUBJECT_PREFIX"
            problems(*nats, "ktor.broker.jetstream.maxAge" to "soon") shouldContain "NATS_STREAM_MAX_AGE"
            problems(*nats, "ktor.broker.jetstream.maxBytes" to "-1") shouldContain "NATS_STREAM_MAX_BYTES"
            problems(*nats, "ktor.broker.jetstream.user" to "stats") shouldContain "NATS_USER and NATS_PASSWORD must be set together"
            problems(
                *nats,
                "ktor.broker.jetstream.user" to "stats",
                "ktor.broker.jetstream.password" to "secret",
                "ktor.broker.jetstream.token" to "t",
            ) shouldContain "NATS_TOKEN or NATS_USER/NATS_PASSWORD"
        }
    })
