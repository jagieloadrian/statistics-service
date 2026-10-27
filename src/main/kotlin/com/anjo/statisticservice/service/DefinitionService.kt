package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.conflict
import com.anjo.statisticservice.exception.forbidden
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Bundle
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.model.Widget
import com.anjo.statisticservice.model.dto.DefinitionSummary
import com.anjo.statisticservice.model.dto.ImportResult
import com.anjo.statisticservice.model.names
import com.anjo.statisticservice.repository.DashboardShareRepository
import com.anjo.statisticservice.repository.DefinitionRepository
import com.anjo.statisticservice.repository.RecordRepository
import com.anjo.statisticservice.repository.SourceCredentialRepository
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest

/** Datasets, sources and dashboards: validation, references, access, import and export. */
class DefinitionService(private val db: Db, private val status: SourceStatusService) {

    /** [visibleTo] limits dashboards to the ones that user may view; null = everything. */
    suspend fun <T> list(kind: Kind, ser: KSerializer<T>, visibleTo: UserPrincipal? = null): List<T> =
        db.tx { DefinitionRepository.list(kind, ser, visibleTo) }

    suspend fun summaries(kind: Kind, visibleTo: UserPrincipal? = null): List<DefinitionSummary> =
        db.tx { DefinitionRepository.summaries(kind, visibleTo) }

    /** The stored document as is. */
    suspend fun json(kind: Kind, id: String): JsonElement? = get(kind, id, JsonElement.serializer())

    suspend fun dataset(id: String) = get(Kind.DATASET, id, Dataset.serializer())
    suspend fun source(id: String) = get(Kind.SOURCE, id, Source.serializer())
    suspend fun dashboard(id: String) = get(Kind.DASHBOARD, id, Dashboard.serializer())

    private suspend fun <T> get(kind: Kind, id: String, ser: KSerializer<T>): T? = db.tx { DefinitionRepository.get(kind, id, ser) }

    /** Viewers only reach datasets used by a dashboard they can view. */
    suspend fun requireDatasetAccess(u: UserPrincipal, id: String) {
        if (u.role == VIEWER && id !in visibleDatasets(u)) throw forbidden("Dataset '$id' is not used by a dashboard you can view")
    }

    /** Without [force], removing names that widgets or sources still use is a conflict. */
    suspend fun putDataset(d: Dataset, force: Boolean) {
        val removed = dataset(d.id)?.let { it.names().toSet() - d.names().toSet() }.orEmpty()
        if (removed.isNotEmpty() && !force) {
            val refs = references(d.id, removed)
            if (refs.isNotEmpty()) throw conflict("Removing $removed breaks references; add ?force=true to save anyway", refs)
        }
        db.tx { storeDataset(d) }
    }

    suspend fun deleteDataset(id: String, force: Boolean) {
        if (!force) {
            val refs = references(id)
            if (refs.isNotEmpty()) throw conflict("Dataset '$id' is still in use; add ?force=true to delete anyway", refs)
        }
        if (!delete(Kind.DATASET, id)) throw notFound("Unknown dataset '$id'")
    }

    suspend fun putSource(s: Source) {
        db.tx { storeSource(s) }
    }

    suspend fun deleteSource(id: String) {
        if (!delete(Kind.SOURCE, id)) throw notFound("Unknown source '$id'")
        status.forget(id)
    }

    /** Returns the new token once; only its SHA-256 is stored. */
    suspend fun rotateCredential(sourceId: String): String {
        source(sourceId) ?: throw notFound("Unknown source '$sourceId'")
        val token = newToken()
        db.tx { SourceCredentialRepository.upsert(sourceId, sha256(token)) }
        return token
    }

    suspend fun verifyCredential(sourceId: String, token: String): Boolean {
        val stored = db.tx { SourceCredentialRepository.tokenHash(sourceId) } ?: return false
        return MessageDigest.isEqual(stored, sha256(token))
    }

    /** Fails unless the dashboard exists and [u] may view it. */
    suspend fun viewDashboard(u: UserPrincipal, id: String): JsonElement {
        val d = json(Kind.DASHBOARD, id) ?: throw notFound("Unknown dashboard '$id'")
        if (!db.tx { DefinitionRepository.isVisible(u, id) }) throw forbidden("Dashboard '$id' is not shared with you")
        return d
    }

    /** Creating sets [u] as owner; replacing needs [canEdit]. */
    suspend fun putDashboard(u: UserPrincipal, d: Dashboard) {
        if (dashboard(d.id) != null && !canEdit(u, d.id)) throw forbidden("Only the owner or an admin can change '${d.id}'")
        db.tx { storeDashboard(d, u.id) }
    }

    suspend fun deleteDashboard(u: UserPrincipal, id: String) {
        requireEditable(u, id, "Only the owner or an admin can delete '$id'")
        delete(Kind.DASHBOARD, id)
    }

    /** Not found for an unknown dashboard, forbidden unless [u] [canEdit] it. */
    suspend fun requireEditable(u: UserPrincipal, id: String, message: String? = null) {
        dashboard(id) ?: throw notFound("Unknown dashboard '$id'")
        if (!canEdit(u, id)) throw message?.let(::forbidden) ?: forbidden()
    }

    suspend fun shares(dashboardId: String): List<Long> = db.tx { DashboardShareRepository.userIds(dashboardId) }

    suspend fun setShares(dashboardId: String, userIds: List<Long>) = db.tx { DashboardShareRepository.replace(dashboardId, userIds) }

    /** Admins and the owning editor may change a dashboard; imported ones (no owner) are admin-only. */
    private suspend fun canEdit(u: UserPrincipal, dashboardId: String): Boolean =
        u.role == ADMIN || u.role == EDITOR && db.tx { DefinitionRepository.ownerId(dashboardId) } == u.id

    private suspend fun visibleDatasets(u: UserPrincipal): Set<String> =
        list(Kind.DASHBOARD, Dashboard.serializer(), u).flatMap { d -> d.widgets.map { it.query.dataset } + d.variables.mapNotNull { it.dataset } }.toSet()

    /** Everything for admins; for others their visible dashboards plus the datasets those use, no sources. */
    suspend fun export(u: UserPrincipal? = null): Bundle {
        if (u == null || u.role == ADMIN) {
            return Bundle(
                datasets = list(Kind.DATASET, Dataset.serializer()),
                sources = list(Kind.SOURCE, Source.serializer()),
                dashboards = list(Kind.DASHBOARD, Dashboard.serializer()),
            )
        }
        val dashboards = list(Kind.DASHBOARD, Dashboard.serializer(), u)
        val used = visibleDatasets(u)
        return Bundle(datasets = list(Kind.DATASET, Dataset.serializer()).filter { it.id in used }, dashboards = dashboards)
    }

    /** All-or-nothing upsert; on any invalid document nothing is stored and every error is returned. */
    // ponytail: no reference guard on import, it restores whole bundles
    suspend fun import(b: Bundle): ImportResult = db.tx {
        val errors = mutableListOf<String>()
        val created = mutableListOf<Boolean>()
        fun attempt(what: String, put: () -> Boolean) = try {
            created += put()
        } catch (e: ApiException) {
            errors += (listOf("$what: ${e.message}") + e.details).joinToString("; ")
        }
        if (b.version != 1) errors += "version: must be 1"
        b.datasets.forEach { attempt("dataset ${it.id}") { storeDataset(it) } }
        b.sources.forEach { attempt("source ${it.id}") { storeSource(it) } }
        b.dashboards.forEach { attempt("dashboard ${it.id}") { storeDashboard(it, null) } }
        if (errors.isEmpty()) ImportResult(created.count { it }, created.count { !it })
        else ImportResult(0, 0, errors).also { rollback() }
    }

    /**
     * Widgets (`dashboard/widget`), dashboard variables (`dashboard/$var`) and sources using a dataset;
     * with [names], only those using one of these names.
     */
    private suspend fun references(datasetId: String, names: Set<String>? = null): List<String> {
        fun hit(used: Collection<String>) = names == null || used.any { it in names }
        val dashboards = list(Kind.DASHBOARD, Dashboard.serializer()).flatMap { d ->
            d.widgets.filter { it.query.dataset == datasetId && hit(it.uses()) }.map { "${d.id}/${it.id}" } +
                d.variables.filter { it.dataset == datasetId && hit(listOfNotNull(it.dimension)) }.map { "${d.id}/$${it.name}" }
        }
        val sources = list(Kind.SOURCE, Source.serializer())
            .filter { s -> s.dataset == datasetId && hit(s.mapping.flatMap { it.dims.keys + it.vals.keys + it.constDims.keys }) }
            .map { "source ${it.id}" }
        return dashboards + sources
    }

    private fun Widget.uses() = query.measures.flatMap { listOfNotNull(it.field, it.by) } + query.filter.keys + query.groupBy + listOfNotNull(options.x)

    /** Deletes the document and what hangs off it: records, the source credential or dashboard shares. */
    private suspend fun delete(kind: Kind, id: String): Boolean = db.tx {
        when (kind) {
            Kind.DATASET -> RecordRepository.deleteDataset(id)
            Kind.SOURCE -> SourceCredentialRepository.delete(id)
            Kind.DASHBOARD -> DashboardShareRepository.delete(id)
        }
        DefinitionRepository.delete(kind, id)
    }

    private fun storeDataset(d: Dataset): Boolean {
        validateDataset(d)
        return DefinitionRepository.upsert(Kind.DATASET, d.id, d, Dataset.serializer(), null)
    }

    private fun storeSource(s: Source): Boolean {
        val dataset = DefinitionRepository.get(Kind.DATASET, s.dataset, Dataset.serializer())
            ?: throw badRequest("Unknown dataset '${s.dataset}'")
        validateSource(s, dataset)
        return DefinitionRepository.upsert(Kind.SOURCE, s.id, s, Source.serializer(), null)
    }

    private fun storeDashboard(d: Dashboard, ownerId: Long?): Boolean {
        validateDashboard(d)
        val missing = d.widgets.map { it.query.dataset }.distinct().filter { DefinitionRepository.get(Kind.DATASET, it, Dataset.serializer()) == null }
        if (missing.isNotEmpty()) throw badRequest("Unknown dataset(s) in widgets", missing)
        return DefinitionRepository.upsert(Kind.DASHBOARD, d.id, d, Dashboard.serializer(), ownerId)
    }
}
