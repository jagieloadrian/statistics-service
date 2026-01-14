package com.anjo.statisticservice.di

import com.anjo.statisticservice.configuration.RedisClientProvider
import com.anjo.statisticservice.model.RedisConfig
import com.anjo.statisticservice.repository.StatsRepositoryRedisImpl
import com.anjo.statisticservice.service.StatsCollectorService
import com.anjo.statisticservice.service.exposer.EpidemicStatsExposerService
import com.anjo.statisticservice.service.exposer.StatsExposerFacade
import com.anjo.statisticservice.service.exposer.TemperatureStatsExposerService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies

fun Application.configureDI() {
    val appEnv = environment
    dependencies {
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
        provide { EpidemicStatsExposerService(get(DependencyKey<StatsRepositoryRedisImpl>())) }
        provide { TemperatureStatsExposerService(get(DependencyKey<StatsRepositoryRedisImpl>())) }
        provide { StatsExposerFacade(get(DependencyKey<EpidemicStatsExposerService>()),
            get(DependencyKey<TemperatureStatsExposerService>())) }
    }
}
