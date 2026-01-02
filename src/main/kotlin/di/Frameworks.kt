package com.anjo.di

import com.anjo.configuration.RedisClientProvider
import com.anjo.model.RedisConfig
import com.anjo.reposiotry.StatsRepositoryRedisImpl
import com.anjo.service.GreetingService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies

fun Application.configureFrameworks() {
    val appEnv = environment
    dependencies {
        provide { GreetingService { "Hello, World!" } }
        provide<RedisConfig> {
            return@provide RedisConfig(
                host = appEnv.config.property("redis.host").getString(),
                port = appEnv.config.property("redis.port").getString().toInt(),
                password = appEnv.config.property("redis.password").getString(),
                timeout = appEnv.config.property("redis.timeout").getString().toLong(),
                clientName = appEnv.config.property("redis.clientName").getString(),
                ssl = appEnv.config.property("redis.ssl").getString().toBoolean(),
            )
        }
        provide { RedisClientProvider(get(DependencyKey<RedisConfig>())) }
        provide { StatsRepositoryRedisImpl(get(DependencyKey<RedisClientProvider>())) }
    }
}
