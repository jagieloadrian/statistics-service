package com.anjo.statisticservice.observability

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.slf4j.MDC

class TracingTest :
    FunSpec({
        val id = "4bf92f3577b34da6a3ce929d0e0e4736"

        test("the trace id is the second field of a W3C traceparent when it is 32 lowercase hex digits, not all zero") {
            traceIdOf("00-$id-00f067aa0ba902b7-01") shouldBe id
            traceIdOf(null) shouldBe null
            traceIdOf("garbage") shouldBe null
            traceIdOf("00-${id.uppercase()}-00f067aa0ba902b7-01") shouldBe null
            traceIdOf("00-${id.drop(1)}-00f067aa0ba902b7-01") shouldBe null
            traceIdOf("00-${"0".repeat(32)}-00f067aa0ba902b7-01") shouldBe null
        }

        test("new trace ids are valid trace ids") {
            isTraceId(newTraceId()) shouldBe true
            isTraceId("xyz") shouldBe false
        }

        test("the outgoing traceparent carries the trace id from the MDC") {
            MDC.remove(TRACE_ID_MDC_KEY)
            currentTraceparent() shouldBe null
            MDC.putCloseable(TRACE_ID_MDC_KEY, id).use {
                currentTraceparent().orEmpty() shouldMatch Regex("^00-$id-[0-9a-f]{16}-01$")
                traceIdOf(currentTraceparent()) shouldBe id
            }
        }
    })
