package com.anjo.statisticservice.routing

import com.anjo.statisticservice.auth.ADMIN
import com.anjo.statisticservice.auth.EDITOR
import com.anjo.statisticservice.auth.VIEWER
import com.anjo.statisticservice.auth.forbidden
import com.anjo.statisticservice.auth.requireRole
import com.anjo.statisticservice.auth.user
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
data class Shares(val userIds: List<Long>)

@Serializable
data class MappingTestResult(val records: List<MappedRecord>, val errors: List<String>)

private val RoutingCall.id get() = parameters["id"]!!
private val RoutingCall.force get() = request.queryParameters["force"] == "true"

private fun requireSameId(pathId: String, bodyId: String) {
    if (pathId != bodyId) throw badRequest("id in path '$pathId' differs from body '$bodyId'")
}

/** Every route here needs a signed-in user (mounted inside `authenticate("user")`); roles per data-model.md. */
fun Route.apiRoutes(defs: DefinitionStore, records: RecordStore, status: SourceStatus) {
    suspend fun RoutingCall.requireDataset(id: String) {
        val u = user()
        if (u.role == VIEWER && id !in defs.visibleDatasets(u)) throw forbidden("Dataset '$id' is not used by a dashboard you can view")
    }

    // datasets: admin writes; editors read them to build widgets; viewers only the ones their dashboards use
    get("/datasets") {
        call.requireRole(ADMIN, EDITOR)
        call.respond(defs.summaries(Kind.DATASET))
    }.describe { summary = "List datasets (admin, editor)" }
    get("/datasets/{id}") {
        call.requireDataset(call.id)
        call.respond(defs.get(Kind.DATASET, call.id, JsonElement.serializer()) ?: throw notFound("Unknown dataset '${call.id}'"))
    }.describe { summary = "Get one dataset" }
    put("/datasets/{id}") {
        call.requireRole(ADMIN)
        val d = call.receive<Dataset>().also { requireSameId(call.id, it.id) }
        val removed = defs.dataset(d.id)?.let { it.names().toSet() - d.names().toSet() }.orEmpty()
        if (removed.isNotEmpty() && !call.force) {
            val refs = defs.references(d.id, removed)
            if (refs.isNotEmpty()) throw conflict("Removing $removed breaks references; add ?force=true to save anyway", refs)
        }
        defs.putDataset(d)
        call.respond(d)
    }.describe { summary = "Create or replace a dataset (admin)" }
    delete("/datasets/{id}") {
        call.requireRole(ADMIN)
        if (!call.force) {
            val refs = defs.references(call.id)
            if (refs.isNotEmpty()) throw conflict("Dataset '${call.id}' is still in use; add ?force=true to delete anyway", refs)
        }
        if (!defs.delete(Kind.DATASET, call.id)) throw notFound("Unknown dataset '${call.id}'")
        call.respond(mapOf("deleted" to call.id))
    }.describe { summary = "Delete a dataset and its records (admin)" }

    // sources: admin only
    route("/sources") {
        get {
            call.requireRole(ADMIN)
            call.respond(defs.summaries(Kind.SOURCE))
        }.describe { summary = "List sources (admin)" }
        get("/{id}") {
            call.requireRole(ADMIN)
            call.respond(defs.get(Kind.SOURCE, call.id, JsonElement.serializer()) ?: throw notFound("Unknown source '${call.id}'"))
        }.describe { summary = "Get one source (admin)" }
        put("/{id}") {
            call.requireRole(ADMIN)
            val s = call.receive<Source>().also { requireSameId(call.id, it.id) }
            defs.putSource(s)
            call.respond(s)
        }.describe { summary = "Create or replace a source (admin)" }
        delete("/{id}") {
            call.requireRole(ADMIN)
            if (!defs.delete(Kind.SOURCE, call.id)) throw notFound("Unknown source '${call.id}'")
            status.forget(call.id)
            call.respond(mapOf("deleted" to call.id))
        }.describe { summary = "Delete a source and its credential (admin)" }
        post("/{id}/credential") {
            call.requireRole(ADMIN)
            defs.source(call.id) ?: throw notFound("Unknown source '${call.id}'")
            call.respond(mapOf("token" to defs.rotateCredential(call.id)))
        }.describe { summary = "Rotate the source token; returned once, the previous one stops working (admin)" }
        get("/{id}/status") {
            call.requireRole(ADMIN)
            call.respond(status.view(defs.source(call.id) ?: throw notFound("Unknown source '${call.id}'")))
        }.describe { summary = "Source state, counts and recent rejections (admin)" }
        post("/{id}/mapping-test") {
            call.requireRole(ADMIN)
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
        }.describe { summary = "Run a mapping against a sample payload; stores nothing (admin)" }
    }

    // dashboards: visibility for reading, owner editor or admin for writing
    route("/dashboards") {
        get { call.respond(defs.summaries(Kind.DASHBOARD, call.user())) }
            .describe { summary = "List dashboards visible to the caller" }
        get("/{id}") {
            val d = defs.get(Kind.DASHBOARD, call.id, JsonElement.serializer()) ?: throw notFound("Unknown dashboard '${call.id}'")
            if (!defs.canView(call.user(), call.id)) throw forbidden("Dashboard '${call.id}' is not shared with you")
            call.respond(d)
        }.describe { summary = "Get one dashboard" }
        put("/{id}") {
            val u = call.requireRole(ADMIN, EDITOR)
            val d = call.receive<Dashboard>().also { requireSameId(call.id, it.id) }
            if (defs.dashboard(call.id) != null && !defs.canEdit(u, call.id)) throw forbidden("Only the owner or an admin can change '${call.id}'")
            defs.putDashboard(d, u.id)
            call.respond(d)
        }.describe { summary = "Create or replace a dashboard (editor: own only)" }
        delete("/{id}") {
            val u = call.requireRole(ADMIN, EDITOR)
            defs.dashboard(call.id) ?: throw notFound("Unknown dashboard '${call.id}'")
            if (!defs.canEdit(u, call.id)) throw forbidden("Only the owner or an admin can delete '${call.id}'")
            defs.delete(Kind.DASHBOARD, call.id)
            call.respond(mapOf("deleted" to call.id))
        }.describe { summary = "Delete a dashboard (editor: own only)" }
        get("/{id}/shares") {
            val u = call.requireRole(ADMIN, EDITOR)
            defs.dashboard(call.id) ?: throw notFound("Unknown dashboard '${call.id}'")
            if (!defs.canEdit(u, call.id)) throw forbidden()
            call.respond(Shares(defs.shares(call.id)))
        }.describe { summary = "Users a dashboard is shared with (owner editor, admin)" }
        put("/{id}/shares") {
            val u = call.requireRole(ADMIN, EDITOR)
            defs.dashboard(call.id) ?: throw notFound("Unknown dashboard '${call.id}'")
            if (!defs.canEdit(u, call.id)) throw forbidden()
            val shares = call.receive<Shares>()
            defs.setShares(call.id, shares.userIds)
            call.respond(shares)
        }.describe { summary = "Share a dashboard with users (used when visibility = users)" }
    }

    get("/export") {
        call.respond(defs.export(call.requireRole(ADMIN, EDITOR)))
    }.describe { summary = "Export a bundle (admin: everything; editor: visible dashboards + their datasets); no credentials" }
    post("/import") {
        call.requireRole(ADMIN)
        val result = defs.import(call.receive<Bundle>())
        call.respond(if (result.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest, result)
    }.describe { summary = "Import a bundle: upsert by kind and id, all or nothing (admin)" }

    post("/query") {
        val q = call.receive<Query>()
        call.requireDataset(q.dataset)
        val ds = defs.dataset(q.dataset) ?: throw badRequest("Unknown dataset '${q.dataset}'")
        call.respond(records.query(q, ds, Clock.System.now()))
    }.describe { summary = "Query records of a dataset (aggregations, buckets, groupBy)" }
    get("/datasets/{id}/dimensions/{dim}/values") {
        call.requireDataset(call.id)
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
