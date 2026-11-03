package com.anjo.statisticservice.config

import com.anjo.statisticservice.model.DbConfig
import io.ktor.server.config.ApplicationConfig

internal fun ApplicationConfig.dbConfig(problems: MutableList<String>): DbConfig {
    val config = DbConfig(setting("ktor.db.url"), setting("ktor.db.user"), setting("ktor.db.password"))
    if (!config.url.startsWith("jdbc:postgresql://")) problems += "DB_URL must be a jdbc:postgresql:// URL"
    if (config.user.isBlank()) problems += "DB_USER is required"
    return config
}
