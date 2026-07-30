package com.anjo.statisticservice.validation

import com.anjo.statisticservice.model.dto.TemperatureDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit.DAYS
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

private val logger = KotlinLogging.logger {}

private const val MUST_BE_GREATER_THAN_ZERO = "must be greater than 0"

@OptIn(ExperimentalTime::class)
fun isTemperatureDtoValid(dto: TemperatureDto): Pair<Boolean, List<String>> {
    val yesterday = Clock.System.now()
        .minus(Duration
            .convert(1.0, DAYS, DAYS)
            .toDuration(DAYS))
        .toLocalDateTime(TimeZone.UTC)
    logger.info { "Checking temperature validation" }
    val reasons = mutableListOf<String>()
    var result = true

    val (status, deviceId, timestamp, temperature) = dto

    validField(status.trim().isNotEmpty() || status.isNotBlank(), "Status cannot be empty or null", reasons)
    { res -> result = res }

    validField(deviceId.trim().isNotEmpty() || deviceId.isNotBlank(), "Device name cannot be empty or null", reasons)
    { res -> result = res }

    validField(timestamp <= yesterday, "Timestamp cannot be older than yesterday", reasons)
    { res -> result = res }

    validField(isGreaterThanZero(temperature), "Temperature $MUST_BE_GREATER_THAN_ZERO", reasons)
    { res -> result = res }

    return Pair(result, reasons)
}

private fun validField(
    isValid: Boolean,
    reason: String,
    mutableList: MutableList<String>,
    validatorFunc: (Boolean) -> Unit
) {
    if (!isValid) {
        mutableList.add(reason)
    }
    validatorFunc(isValid)
}

private inline fun <reified T> isGreaterThanZero(value: T): Boolean where T : Number, T : Comparable<T> {
    return when (T::class) {
        Int::class -> (value as Int) > 0
        Long::class -> (value as Long) > 0
        Double::class -> (value as Double) > 0
        else -> value.toDouble() > 0
    }
}
