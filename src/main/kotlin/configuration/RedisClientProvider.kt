package com.anjo.configuration

import com.anjo.model.RedisConfig
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import java.time.Duration

class RedisClientProvider(private val configuration: RedisConfig) {
    val client: RedisClient = run {
        val builder = RedisURI.builder()
            .withHost(configuration.host)
            .withPort(configuration.port)
            .withTimeout(Duration.ofSeconds(configuration.timeout))
            .withClientName(configuration.clientName)
        if (configuration.password.isEmpty() || configuration.password.isBlank()) {
            builder.withPassword(configuration.password)
        }
        val uri = builder.build()
        val client1 = RedisClient.create(uri)
        client1.options = ClientOptions.builder().autoReconnect(true).build()
        client1
    }
}