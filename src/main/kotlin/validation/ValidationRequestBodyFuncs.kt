package com.anjo.validation

import com.anjo.model.EpidemicDto
import com.anjo.validation.ValidatorMessages.MUST_BE_GREATHER_THAN_ZERO

fun isEpidemicValid(epidemicDto: EpidemicDto): Pair<Boolean, List<String>> {
    val reasons = mutableListOf<String>()
    var result = true
    val (deviceId, runId, generation, timestamp) = epidemicDto.meta
    val (populationSize, infectionProb, infectionTtlMin, infectionTtlMax) = epidemicDto.params
    val (susceptible, infected, recovered)= epidemicDto.state

    if(deviceId.isEmpty() || deviceId.isBlank()) {
        result = false
        reasons.add("Device Id or device must not be blank")
    }

    validField(isGreaterThanZero(runId), "RunId $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(generation), "Generation $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(timestamp), "Timestamp $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(populationSize), "PopulationSize $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(infectionProb), "InfectionProbability $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(infectionTtlMin), "InfectionTtlMin $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(infectionTtlMax), "InfectionTtlMax $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(susceptible), "Susceptible $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(infected), "Infected $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }
    validField(isGreaterThanZero(recovered), "Recovered $MUST_BE_GREATHER_THAN_ZERO",
        reasons) { res -> result = res }

    return Pair(result, reasons)
}

private fun validField(isValid:Boolean, reason: String, mutableList: MutableList<String>, validatorFunc:(Boolean) -> Unit) {
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