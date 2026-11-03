package com.anjo.statisticservice.model

data class KafkaConfig(
    val bootstrapServers: String,
    val groupId: String,
    val topic: String,
    val securityProtocol: String,
    val saslMechanism: String,
    val user: String,
    val password: String,
) : BrokerConfig {
    val usesSasl get() = securityProtocol.startsWith("SASL_")
}
