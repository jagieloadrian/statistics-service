rootProject.name = "StatisticsService"

include(
    ":plugin:plugin-api",
    ":plugin:rpi_epidemic_api",
    ":plugin:rpi_temperature_api",
    ":plugin:home_assistant_api",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
    pluginManagement {
        val kotlin_version: String by settings
        val ktor_version: String by settings

        plugins {
            kotlin("jvm") version kotlin_version
            kotlin("plugin.serialization") version kotlin_version
            id("io.ktor.plugin") version ktor_version
        }
    }
}
