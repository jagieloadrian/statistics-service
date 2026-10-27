package com.anjo.statisticservice.routing

import com.anjo.statisticservice.definitions.Bundle
import com.anjo.statisticservice.definitions.Dashboard
import com.anjo.statisticservice.definitions.Dataset
import com.anjo.statisticservice.definitions.DefinitionStore
import com.anjo.statisticservice.definitions.Kind
import com.anjo.statisticservice.definitions.names
import com.anjo.statisticservice.definitions.Query
import com.anjo.statisticservice.definitions.Rule
import com.anjo.statisticservice.definitions.Source
import com.anjo.statisticservice.definitions.validateSource
import com.anjo.statisticservice.ingest.SourceStatus
import com.anjo.statisticservice.mapping.Mapper
import com.anjo.statisticservice.mapping.MappingException
import com.anjo.statisticservice.records.RecordStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

@Serializable
data class MappingTestRequest(val payload: JsonElement, val mapping: List<Rule>? = null)

@Serializable
data class MappedRecord(
    val ts: String,
    val tsEstimated: Boolean,
    val dims: Map<String, String>,
    val vals: Map<String, JsonPrimitive>,
    val ident: String?,
)

@Serializable
data class MappingTestResult(val records: List<MappedRecord>, val errors: List<String>)

private val RoutingCall.id get() = parameters["id"]!!
private val RoutingCall.force get() = request.queryParameters["force"] == "true"

private fun requireSameId(pathId: String, bodyId: String) {
    if (pathId != bodyId) throw badRequest("id in path '$pathId' differs from body '$bodyId'")
}

// ponytail: no auth until US4 (LAN only, contracts/api-v2.md)
fun Route.apiRoutes(defs: DefinitionStore, records: RecordStore, status: SourceStatus) = route(API_V2) {
    for ((path, kind) in listOf("datasets" to Kind.DATASET, "sources" to Kind.SOURCE, "dashboards" to Kind.DASHBOARD)) {
        get("/$path") { call.respond(defs.summaries(kind)) }
            .describe { summary = "List $path" }
        get("/$path/{id}") {
            call.respond(defs.get(kind, call.id, JsonElement.serializer()) ?: throw notFound("Unknown ${kind.key} '${call.id}'"))
        }.describe { summary = "Get one ${kind.key}" }
        delete("/$path/{id}") {
            if (kind == Kind.DATASET && !call.force) {
                val refs = defs.references(call.id)
                if (refs.isNotEmpty()) throw conflict("Dataset '${call.id}' is still in use; add ?force=true to delete anyway", refs)
            }
            if (!defs.delete(kind, call.id)) throw notFound("Unknown ${kind.key} '${call.id}'")
            if (kind == Kind.SOURCE) status.forget(call.id)
            call.respond(mapOf("deleted" to call.id))
        }.describe { summary = "Delete a ${kind.key} (a dataset also deletes its records)" }
    }

    put("/datasets/{id}") {
        val d = call.receive<Dataset>().also { requireSameId(call.id, it.id) }
        val removed = defs.dataset(d.id)?.let { it.names().toSet() - d.names().toSet() }.orEmpty()
        if (removed.isNotEmpty() && !call.force) {
            val refs = defs.references(d.id, removed)
            if (refs.isNotEmpty()) throw conflict("Removing $removed breaks references; add ?force=true to save anyway", refs)
        }
        defs.putDataset(d)
        call.respond(d)
    }.describe { summary = "Create or replace a dataset" }
    put("/sources/{id}") {
        val s = call.receive<Source>().also { requireSameId(call.id, it.id) }
        defs.putSource(s)
        call.respond(s)
    }.describe { summary = "Create or replace a source" }
    put("/dashboards/{id}") {
        val d = call.receive<Dashboard>().also { requireSameId(call.id, it.id) }
        defs.putDashboard(d)
        call.respond(d)
    }.describe { summary = "Create or replace a dashboard" }

    get("/export") { call.respond(defs.export()) }
        .describe { summary = "Export all datasets, sources and dashboards as one bundle (no credentials)" }
    post("/import") {
        val result = defs.import(call.receive<Bundle>())
        call.respond(if (result.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest, result)
    }.describe { summary = "Import a bundle: upsert by kind and id, all or nothing" }

    post("/sources/{id}/credential") {
        defs.source(call.id) ?: throw notFound("Unknown source '${call.id}'")
        call.respond(mapOf("token" to defs.rotateCredential(call.id)))
    }.describe { summary = "Rotate the source token; returned once, the previous one stops working" }
    get("/sources/{id}/status") {
        call.respond(status.view(defs.source(call.id) ?: throw notFound("Unknown source '${call.id}'")))
    }.describe { summary = "Source state, counts and recent rejections" }
    post("/sources/{id}/mapping-test") {
        val stored = defs.source(call.id) ?: throw notFound("Unknown source '${call.id}'")
        val req = call.receive<MappingTestRequest>()
        val source = stored.copy(mapping = req.mapping ?: stored.mapping)
        val dataset = defs.dataset(source.dataset) ?: throw badRequest("Unknown dataset '${source.dataset}'")
        validateSource(source, dataset)
        val result = try {
            val mapped = Mapper.map(source, dataset, req.payload, Clock.System.now())
            MappingTestResult(mapped.map { MappedRecord(it.ts.toString(), it.tsEstimated, it.dims, it.vals, it.ident) }, emptyList())
        } catch (e: MappingException) {
            MappingTestResult(emptyList(), e.reasons)
        }
        call.respond(result)
    }.describe { summary = "Run a mapping against a sample payload; stores nothing" }

    post("/query") {
        val q = call.receive<Query>()
        val ds = defs.dataset(q.dataset) ?: throw badRequest("Unknown dataset '${q.dataset}'")
        call.respond(records.query(q, ds, Clock.System.now()))
    }.describe { summary = "Query records of a dataset (aggregations, buckets, groupBy)" }
    get("/datasets/{id}/dimensions/{dim}/values") {
        val ds = defs.dataset(call.id) ?: throw notFound("Unknown dataset '${call.id}'")
        val p = call.request.queryParameters
        call.respond(
            records.dimensionValues(
                ds, call.parameters["dim"]!!, p["search"],
                p["limit"]?.toIntOrNull() ?: 50, p["offset"]?.toIntOrNull() ?: 0,
            ),
        )
    }.describe { summary = "Distinct values of a dimension with first/last seen, newest first" }
}
