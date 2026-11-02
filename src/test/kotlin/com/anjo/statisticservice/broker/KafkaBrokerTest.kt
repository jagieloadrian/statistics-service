package com.anjo.statisticservice.broker

import com.anjo.statisticservice.model.KafkaConfig
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec

class KafkaBrokerTest :
    FunSpec({
        test("SASL settings build a consumer, quotes and backslashes in the password included") {
            listOf("PLAIN", "SCRAM-SHA-512").forEach { mechanism ->
                val config =
                    KafkaConfig(
                        bootstrapServers = "localhost:1",
                        groupId = "statistics-service",
                        topic = "stats.ingest",
                        securityProtocol = "SASL_PLAINTEXT",
                        saslMechanism = mechanism,
                        user = "statistics-service",
                        password = """pa"ss\\word""",
                    )
                shouldNotThrowAny { KafkaBroker(config).close() }
            }
        }
    })
