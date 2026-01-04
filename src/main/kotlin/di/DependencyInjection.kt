package com.anjo.di

import com.anjo.configuration.RedisClientProvider
import com.anjo.model.RedisConfig
import com.anjo.repository.StatsRepositoryRedisImpl
import com.anjo.service.GreetingService
import com.anjo.service.StatsCollectorService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies

fun Application.configureDI() {
    val appEnv = environment
    dependencies {
        provide { GreetingService { "Hello, World!" } }
        provide<RedisConfig> {
            return@provide RedisConfig(
                host = appEnv.config.property("ktor.redis.host").getString(),
                port = appEnv.config.property("ktor.redis.port").getString().toInt(),
                password = appEnv.config.property("ktor.redis.password").getString(),
                timeout = appEnv.config.property("ktor.redis.timeout").getString().toLong(),
                clientName = appEnv.config.property("ktor.redis.client-name").getString(),
                ssl = appEnv.config.property("ktor.redis.ssl").getString().toBoolean(),
            )
        }
        provide { RedisClientProvider(get(DependencyKey<RedisConfig>())) }
        provide { StatsRepositoryRedisImpl(get(DependencyKey<RedisClientProvider>())) }
        provide { StatsCollectorService(get(DependencyKey<StatsRepositoryRedisImpl>())) }
    }
}
