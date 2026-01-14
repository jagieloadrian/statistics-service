package com.anjo.statisticservice.utils

object DbKeyConstants {
    //common
    const val DEVICE_ID_KEY = "deviceId"
    const val RUN_ID_KEY = "runId"
    const val TIMESTAMP_KEY = "timestamp"

    //epidemic
    const val STARTED_AT_KEY = "startedAt"
    const val ENDED_AT_KEY = "endedAt"
    const val POPULATION_KEY = "population"
    const val GENERATION_KEY = "generation"
    const val INFECTED_KEY = "infected"
    const val SUSCEPTIBLE_KEY = "susceptible"
    const val RECOVERED_KEY = "recovered"
    const val DEAD_KEY = "dead"
    const val EXPOSED_KEY = "exposed"
    const val LOCKDOWN_KEY = "lockdown"
    const val MOBILITY_MULTIPLICATION_KEY = "mobilityMul"
    const val BY_TYPE_KEY = "byType"

    //temperatures
    const val STATUS_KEY = "status"
    const val TEMPERATURE_KEY = "temperature"
    const val HUMIDITY_KEY = "humidity"

}