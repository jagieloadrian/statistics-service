package com.anjo.statisticservice.broker

import com.anjo.statisticservice.controller.CLIMATE_SOURCE
import com.anjo.statisticservice.controller.PUBSUB_CLIMATE_SOURCE
import com.anjo.statisticservice.controller.climateSource
import com.anjo.statisticservice.testutils.BrokerChannel
import com.anjo.statisticservice.testutils.Pg
import com.anjo.statisticservice.testutils.TestKafka
import com.anjo.statisticservice.testutils.TestNats
import com.anjo.statisticservice.testutils.json
import com.anjo.statisticservice.testutils.postJson
import com.anjo.statisticservice.testutils.putJson
import com.anjo.statisticservice.testutils.testApp
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT = 60.seconds
private const val PAYLOAD = """{"deviceId":"pico-1","timestamp":"2099-01-01T00:00:00Z","temperature":21.5}"""
private const val RAW_QUERY =
    """{"dataset":"climate","measures":[{"field":"temperature"}],"bucket":"raw",""" +
        """"from":"2098-12-31T00:00:00Z","to":"2099-01-02T00:00:00Z"}"""

private val DATABASE_OUTAGE = 4.seconds

private class BrokerUnderTest(
    val name: String,
    val unreachable: () -> Map<String, String>,
    val newChannel: () -> BrokerChannel,
)

private suspend fun whileRecordsCannotBeWritten(block: suspend () -> Unit) {
    Pg.sql("ALTER TABLE records RENAME TO records_offline")
    try {
        block()
    } finally {
        Pg.sql("ALTER TABLE records_offline RENAME TO records")
    }
}

private suspend fun HttpClient.storedRows(): Int =
    postJson("/api/v2/query", RAW_QUERY)
        .json()
        .jsonObject
        .getValue("series")
        .jsonArray
        .sumOf {
            it.jsonObject
                .getValue("rows")
                .jsonArray.size
        }

private suspend fun HttpClient.status(sourceId: String): JsonObject = get("/api/v2/sources/$sourceId/status").json().jsonObject

private fun JsonObject.rejectionReasons() =
    getValue("rejections").jsonArray.map {
        it.jsonObject
            .getValue("reason")
            .jsonPrimitive.content
    }

class BrokerIngestTest :
    FunSpec({
        beforeTest { Pg.truncateAll() }

        listOf(
            BrokerUnderTest("kafka", { TestKafka.unreachable }) { TestKafka.channel() },
            BrokerUnderTest("jetstream", { TestNats.unreachable }) { TestNats.channel() },
        ).forEach { broker ->
            context(broker.name) {
                test("stores a message once even when it is delivered twice") {
                    val channel = broker.newChannel()
                    testApp(channel.config) {
                        val token = client.climateSource(PUBSUB_CLIMATE_SOURCE)
                        eventually(TIMEOUT) { channel.ready() shouldBe true }
                        repeat(2) { channel.publish("climate", "Bearer $token", PAYLOAD) }
                        eventually(TIMEOUT) { channel.settled() shouldBe true }
                        client.storedRows() shouldBe 1
                        client
                            .status("climate")
                            .getValue("state")
                            .jsonPrimitive.content shouldBe "receiving"
                        anonymous.get("/metrics").bodyAsText() shouldContain
                            """statistics_broker_messages_total{broker="${broker.name}",outcome="stored"} 2.0"""
                    }
                }

                test("acks messages it cannot accept and records why") {
                    val channel = broker.newChannel()
                    testApp(channel.config) {
                        val token = client.climateSource(PUBSUB_CLIMATE_SOURCE)
                        val pushSource = CLIMATE_SOURCE.replace("\"climate\",\"dataset\"", "\"climate-push\",\"dataset\"")
                        client.putJson("/api/v2/sources/climate-push", pushSource).status shouldBe HttpStatusCode.OK
                        val pushToken =
                            client
                                .postJson("/api/v2/sources/climate-push/credential", "")
                                .json()
                                .jsonObject
                                .getValue("token")
                                .jsonPrimitive.content
                        eventually(TIMEOUT) { channel.ready() shouldBe true }
                        channel.publish("climate", "Bearer wrong", PAYLOAD)
                        channel.publish("climate", null, PAYLOAD)
                        channel.publish("climate", "Bearer $token", "{")
                        channel.publish("climate-push", "Bearer $pushToken", PAYLOAD)
                        channel.publish("nope", "Bearer $token", PAYLOAD)
                        eventually(TIMEOUT) { channel.settled() shouldBe true }
                        client.storedRows() shouldBe 0
                        val reasons = client.status("climate").rejectionReasons()
                        reasons.count { it == "Missing or invalid source token" } shouldBe 2
                        reasons.single { it.startsWith("malformed JSON") }
                        client.status("climate-push").rejectionReasons() shouldBe
                            listOf("Source 'climate-push' takes data over push, not pubsub")
                        anonymous.get("/metrics").bodyAsText() shouldContain
                            """statistics_broker_messages_total{broker="${broker.name}",outcome="rejected"} 5.0"""
                    }
                }

                test("keeps a message until the database can store it") {
                    val channel = broker.newChannel()
                    testApp(channel.config) {
                        val token = client.climateSource(PUBSUB_CLIMATE_SOURCE)
                        eventually(TIMEOUT) { channel.ready() shouldBe true }
                        whileRecordsCannotBeWritten {
                            channel.publish("climate", "Bearer $token", PAYLOAD)
                            delay(DATABASE_OUTAGE)
                        }
                        eventually(TIMEOUT) { channel.settled() shouldBe true }
                        client.storedRows() shouldBe 1
                        anonymous.get("/metrics").bodyAsText() shouldNotContain
                            """statistics_broker_messages_total{broker="${broker.name}",outcome="retried"} 0.0"""
                    }
                }

                test("starts and stays ready while the broker is unreachable") {
                    testApp(broker.unreachable()) {
                        anonymous.get("/health/ready").status shouldBe HttpStatusCode.OK
                        anonymous.get("/metrics").bodyAsText() shouldContain """statistics_broker_consuming{broker="${broker.name}"} 0.0"""
                    }
                }
            }
        }
    })
