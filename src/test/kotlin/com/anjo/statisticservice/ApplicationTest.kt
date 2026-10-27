package com.anjo.statisticservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import com.anjo.statisticservice.testutils.testApp

class ApplicationTest : FunSpec({

    test("shouldReturnOkWhenTestHealthEndpoint") {
        testApp {
            client.get("/health").apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    test("shouldReturnOkWhenTestReadyEndpoint") {
        testApp {
            client.get("/ready").apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
})
