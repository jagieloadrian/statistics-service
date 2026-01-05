package com.anjo.validation

import com.anjo.model.EpidemicDto
import com.anjo.validation.ValidatorMessages.MUST_BE_GREATHER_THAN_ZERO
import io.github.oshai.kotlinlogging.KotlinLogging


private val logger = KotlinLogging.logger {}
fun isEpidemicValid(epidemicDto: EpidemicDto): Pair<Boolean, List<String>> {

    logger.info { "Checking epidemic validation" }

    val reasons = mutableListOf<String>()
    var result = true
    val (deviceId, runId, generation, timestamp) = epidemicDto.meta
    val population = epidemicDto.state.population
    val susceptible = epidemicDto.state.susceptible
    val mobilityMul = epidemicDto.state.mobilityMultiplier
    val detailedData = epidemicDto.state.detailedDataByType

    if (deviceId.isEmpty() || deviceId.isBlank()) {
        result = false
        reasons.add("Device Id or device must not be blank")
    }

    validField(
        isGreaterThanZero(runId), "RunId $MUST_BE_GREATHER_THAN_ZERO",
        reasons
    ) { res -> result = res }
    validField(
        isGreaterThanZero(generation), "Generation $MUST_BE_GREATHER_THAN_ZERO",
        reasons
    ) { res -> result = res }
    validField(
        isGreaterThanZero(timestamp), "Timestamp $MUST_BE_GREATHER_THAN_ZERO",
        reasons
    ) { res -> result = res }
    validField(
        isGreaterThanZero(population), "PopulationSize $MUST_BE_GREATHER_THAN_ZERO",
        reasons
    ) { res -> result = res }
    validField(
        isGreaterThanZero(mobilityMul), "Mobility multiplier $MUST_BE_GREATHER_THAN_ZERO",
        reasons
    ) { res -> result = res }
    validField(
        detailedData.isNotEmpty(), "Detailed Data by Type cannot be empty",
        reasons
    ) { res -> result = res }
    validField(
        isGreaterThanZero(susceptible), "Susceptible $MUST_BE_GREATHER_THAN_ZERO",
        reasons
    ) { res -> result = res }

    if (reasons.isEmpty()) {
        logger.info { "No reasons found" }
    } else {
        logger.info { "Found ${reasons.size} reasons" }
    }

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

inline fun <reified T> isGreaterThanZero(value: T): Boolean where T : Number, T : Comparable<T> {
    return when (T::class) {
        Int::class -> (value as Int) > 0
        Long::class -> (value as Long) > 0
        Double::class -> (value as Double) > 0
        else -> value.toDouble() > 0
    }
}