package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.Counts
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.FieldSpec
import com.anjo.statisticservice.model.Measure
import com.anjo.statisticservice.model.PUSH_CHANNEL
import com.anjo.statisticservice.model.Rule
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.dto.IngestResult
import com.anjo.statisticservice.model.dto.MappingTestRequest
import com.anjo.statisticservice.testutils.Pg
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class IngestServiceTest :
    FunSpec({
        val db = Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password)
        val status = SourceStatusService()
        val defs = DefinitionService(db, status)
        val ingest = IngestService(defs, RecordService(db), status)
        val dataset = Dataset(id = "climate", measures = listOf(Measure("temp")))
        val source = Source(id = "pico", dataset = "climate", mapping = listOf(Rule(vals = mapOf("temp" to FieldSpec(path = "t")))))

        beforeTest {
            Pg.truncateAll()
            defs.putDataset(dataset, force = false)
            defs.putSource(source)
        }

        afterSpec { db.close() }

        suspend fun failure(block: suspend () -> Unit) = shouldThrow<ApiException> { block() }

        suspend fun push(
            sourceId: String,
            token: String?,
            payload: String,
        ) = ingest.ingest(sourceId, token?.let { "Bearer $it" }, PUSH_CHANNEL) { payload }

        test("a source token is required and must match") {
            val token = defs.rotateCredential("pico")
            listOf(null, "", token + "x").forEach { candidate ->
                failure { push("pico", candidate, """{"t":1}""") }.status shouldBe HttpStatusCode.Unauthorized
            }
            failure { push("missing", token, "{}") }.status shouldBe HttpStatusCode.NotFound
            push("pico", token, """{"t":1}""") shouldBe IngestResult(1, 0)
            status.view(source).accepted shouldBe Counts(1, 1)
        }

        test("paused sources and sources whose dataset is gone refuse payloads") {
            val token = defs.rotateCredential("pico")
            defs.putSource(source.copy(paused = true))
            failure { push("pico", token, "{}") }.status shouldBe HttpStatusCode.ServiceUnavailable
            defs.putSource(source)
            defs.deleteDataset("climate", force = true)
            failure { push("pico", token, "{}") }.message shouldBe "Dataset 'climate' no longer exists"
            failure { ingest.mappingTest("pico", MappingTestRequest(JsonPrimitive(1))) }.message shouldBe "Unknown dataset 'climate'"
        }

        test("malformed JSON and unmappable payloads are rejected and remembered") {
            val token = defs.rotateCredential("pico")
            failure { push("pico", token, "{not json") }.details.single() shouldStartWith "malformed JSON: "
            failure { push("pico", token, """{"t":"warm"}""") }.details shouldBe
                listOf("vals.temp: expected a number, got 'warm'")
            val view = status.view(source)
            view.rejected shouldBe Counts(2, 2)
            view.rejections.map { it.excerpt } shouldBe listOf("""{"t":"warm"}""", "{not json")
        }

        test("a mapping test uses the stored mapping unless one is given and reports errors instead of throwing") {
            val payload = Json.parseToJsonElement("""{"t":3,"x":"oops"}""")
            ingest.mappingTest("pico", MappingTestRequest(payload)).let {
                it.errors shouldBe emptyList()
                it.records.single().vals shouldBe mapOf("temp" to JsonPrimitive(3.0))
            }
            ingest.mappingTest("pico", MappingTestRequest(payload, listOf(Rule(vals = mapOf("temp" to FieldSpec(path = "x")))))).let {
                it.records shouldBe emptyList()
                it.errors shouldBe listOf("vals.temp: expected a number, got 'oops'")
            }
        }
    })
