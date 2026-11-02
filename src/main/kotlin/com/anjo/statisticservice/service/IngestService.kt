package com.anjo.statisticservice.service

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.exception.MappingException
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.conflict
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.dto.IngestResult
import com.anjo.statisticservice.model.dto.MappedRecord
import com.anjo.statisticservice.model.dto.MappingTestRequest
import com.anjo.statisticservice.model.dto.MappingTestResult
import com.anjo.statisticservice.service.mapping.Mapper
import com.anjo.statisticservice.validation.validateSource
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

class IngestService(
    private val definitions: DefinitionService,
    private val records: RecordService,
    private val status: SourceStatusService,
) {
    suspend fun ingest(
        sourceId: String,
        authorization: String?,
        channel: String,
        body: suspend () -> String,
    ): IngestResult {
        val source = definitions.requireSource(sourceId)
        requireAccepting(source, authorization, channel)
        val dataset =
            definitions.dataset(source.dataset)
                ?: throw conflict("Dataset '${source.dataset}' no longer exists")
        val payload = body()
        val now = Clock.System.now()
        val mapped =
            try {
                Mapper.map(source, dataset, Json.parseToJsonElement(payload), now)
            } catch (e: MappingException) {
                throw rejected(sourceId, e.reasons, payload, now)
            } catch (e: SerializationException) {
                throw rejected(sourceId, listOf("malformed JSON: ${e.message}"), payload, now)
            }
        val result = records.insert(mapped)
        status.accepted(sourceId, result.accepted, now)
        return result
    }

    suspend fun mappingTest(
        sourceId: String,
        req: MappingTestRequest,
    ): MappingTestResult {
        val stored = definitions.requireSource(sourceId)
        val source = stored.copy(mapping = req.mapping ?: stored.mapping)
        val dataset = definitions.referencedDataset(source.dataset)
        validateSource(source, dataset)
        return try {
            val mapped = Mapper.map(source, dataset, req.payload, Clock.System.now())
            MappingTestResult(mapped.map { MappedRecord(it.ts.toString(), it.tsEstimated, it.dims, it.vals, it.ident) }, emptyList())
        } catch (e: MappingException) {
            MappingTestResult(emptyList(), e.reasons)
        }
    }

    private suspend fun requireAccepting(
        source: Source,
        authorization: String?,
        channel: String,
    ) {
        val token = authorization?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrEmpty() || !definitions.verifyCredential(source.id, token)) throw unauthorized("Missing or invalid source token")
        if (source.channel.type != channel) throw conflict("Source '${source.id}' takes data over ${source.channel.type}, not $channel")
        if (source.paused) throw ApiException(HttpStatusCode.ServiceUnavailable, "paused", "Source '${source.id}' is paused")
    }

    private fun rejected(
        sourceId: String,
        reasons: List<String>,
        payload: String,
        now: Instant,
    ): ApiException {
        status.rejected(sourceId, reasons, payload, now)
        return badRequest("Payload rejected", reasons)
    }
}
