package com.anjo.statisticservice.service

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.exception.MappingException
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.conflict
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.dto.IngestResult
import com.anjo.statisticservice.model.dto.MappedRecord
import com.anjo.statisticservice.model.dto.MappingTestRequest
import com.anjo.statisticservice.model.dto.MappingTestResult
import com.anjo.statisticservice.service.mapping.Mapper
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class IngestService(
    private val definitions: DefinitionService,
    private val records: RecordService,
    private val status: SourceStatusService,
) {

    /** Maps and stores one pushed payload; [body] is read only once the source accepts it. Rejections go to the source status. */
    suspend fun ingest(sourceId: String, token: String?, body: suspend () -> String): IngestResult {
        val source = definitions.source(sourceId) ?: throw notFound("Unknown source '$sourceId'")
        if (token.isNullOrEmpty() || !definitions.verifyCredential(sourceId, token)) throw unauthorized("Missing or invalid source token")
        if (source.paused) throw ApiException(HttpStatusCode.ServiceUnavailable, "paused", "Source '$sourceId' is paused")
        val dataset = definitions.dataset(source.dataset)
            ?: throw conflict("Dataset '${source.dataset}' no longer exists")
        val payload = body()
        val now = Clock.System.now()
        val mapped = try {
            Mapper.map(source, dataset, Json.parseToJsonElement(payload), now)
        } catch (e: MappingException) {
            status.rejected(sourceId, e.reasons, payload, now)
            throw badRequest("Payload rejected", e.reasons)
        } catch (e: SerializationException) {
            val reasons = listOf("malformed JSON: ${e.message}")
            status.rejected(sourceId, reasons, payload, now)
            throw badRequest("Payload rejected", reasons)
        }
        val result = records.insert(mapped)
        status.accepted(sourceId, result.accepted, now)
        return result
    }

    /** Runs [req]'s mapping, or the stored one, against a sample payload; stores nothing. */
    suspend fun mappingTest(stored: Source, req: MappingTestRequest): MappingTestResult {
        val source = stored.copy(mapping = req.mapping ?: stored.mapping)
        val dataset = definitions.dataset(source.dataset) ?: throw badRequest("Unknown dataset '${source.dataset}'")
        validateSource(source, dataset)
        return try {
            val mapped = Mapper.map(source, dataset, req.payload, Clock.System.now())
            MappingTestResult(mapped.map { MappedRecord(it.ts.toString(), it.tsEstimated, it.dims, it.vals, it.ident) }, emptyList())
        } catch (e: MappingException) {
            MappingTestResult(emptyList(), e.reasons)
        }
    }
}
