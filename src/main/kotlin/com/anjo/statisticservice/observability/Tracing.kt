package com.anjo.statisticservice.observability

import org.slf4j.MDC
import java.util.HexFormat
import kotlin.random.Random

const val TRACE_ID_MDC_KEY = "traceId"
const val TRACEPARENT_HEADER = "traceparent"

private val TRACE_ID = Regex("^[0-9a-f]{32}$")
private val hex = HexFormat.of()

fun traceIdOf(traceparent: String?): String? =
    traceparent
        ?.split('-')
        ?.getOrNull(1)
        ?.takeIf { TRACE_ID.matches(it) && it.any { digit -> digit != '0' } }

fun newTraceId(): String = hex.formatHex(Random.nextBytes(16))

fun isTraceId(value: String): Boolean = TRACE_ID.matches(value)

fun currentTraceparent(): String? = MDC.get(TRACE_ID_MDC_KEY)?.let { "00-$it-${hex.toHexDigits(Random.nextLong())}-01" }
