package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.conflict
import com.anjo.statisticservice.exception.forbidden
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Bundle
import com.anjo.statisticservice.model.DEFINITION_VERSION
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
import com.anjo.statisticservice.validation.validateDashboard
import com.anjo.statisticservice.validation.validateDataset
import com.anjo.statisticservice.validation.validateSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest

class DefinitionService(
    private val db: Db,
    private val status: SourceStatusService,
) {
    suspend fun summaries(
        kind: Kind,
        visibleTo: UserPrincipal? = null,
    ): List<DefinitionSummary> = db.tx { DefinitionRepository.summaries(kind, visibleTo) }

    suspend fun datasets(): List<Dataset> = list(Kind.DATASET, Dataset.serializer())

    suspend fun dataset(id: String): Dataset? = find(Kind.DATASET, id, Dataset.serializer())

    suspend fun source(id: String): Source? = find(Kind.SOURCE, id, Source.serializer())

    suspend fun requireSource(id: String): Source = source(id) ?: throw unknown(Kind.SOURCE, id)

    suspend fun sourceDocument(id: String): JsonElement = document(Kind.SOURCE, id)

    suspend fun datasetDocument(
        user: UserPrincipal,
        id: String,
    ): JsonElement {
        requireDatasetAccess(user, id)
        return document(Kind.DATASET, id)
    }

    suspend fun accessibleDataset(
        user: UserPrincipal,
        id: String,
    ): Dataset {
        requireDatasetAccess(user, id)
        return dataset(id) ?: throw unknown(Kind.DATASET, id)
    }

    suspend fun queryableDataset(
        user: UserPrincipal,
        id: String,
    ): Dataset {
        requireDatasetAccess(user, id)
        return referencedDataset(id)
    }

    suspend fun referencedDataset(id: String): Dataset = dataset(id) ?: throw unknownReference(Kind.DATASET, id)

    suspend fun putDataset(
        dataset: Dataset,
        force: Boolean,
    ) {
        val removed = dataset(dataset.id)?.let { it.names().toSet() - dataset.names().toSet() }.orEmpty()
        if (removed.isNotEmpty() && !force) {
            val refs = references(dataset.id, removed)
            if (refs.isNotEmpty()) throw conflict("Removing $removed breaks references; add ?force=true to save anyway", refs)
        }
        db.tx { storeDataset(dataset) }
    }

    suspend fun deleteDataset(
        id: String,
        force: Boolean,
    ) {
        if (!force) {
            val refs = references(id)
            if (refs.isNotEmpty()) throw conflict("Dataset '$id' is still in use; add ?force=true to delete anyway", refs)
        }
        if (!delete(Kind.DATASET, id)) throw unknown(Kind.DATASET, id)
    }

    suspend fun putSource(source: Source) {
        db.tx { storeSource(source) }
    }

    suspend fun deleteSource(id: String) {
        if (!delete(Kind.SOURCE, id)) throw unknown(Kind.SOURCE, id)
        status.forget(id)
    }

    suspend fun rotateCredential(sourceId: String): String {
        requireSource(sourceId)
        val token = newToken()
        db.tx { SourceCredentialRepository.upsert(sourceId, sha256(token)) }
        return token
    }

    suspend fun verifyCredential(
        sourceId: String,
        token: String,
    ): Boolean {
        val stored = db.tx { SourceCredentialRepository.tokenHash(sourceId) } ?: return false
        return MessageDigest.isEqual(stored, sha256(token))
    }

    suspend fun dashboardDocument(
        user: UserPrincipal,
        id: String,
    ): JsonElement {
        val dashboard = document(Kind.DASHBOARD, id)
        if (!db.tx { DefinitionRepository.isVisible(user, id) }) throw forbidden("Dashboard '$id' is not shared with you")
        return dashboard
    }

    suspend fun putDashboard(
        user: UserPrincipal,
        dashboard: Dashboard,
    ) {
        val exists = find(Kind.DASHBOARD, dashboard.id, Dashboard.serializer()) != null
        if (exists && !canEdit(user, dashboard.id)) throw forbidden("Only the owner or an admin can change '${dashboard.id}'")
        db.tx { storeDashboard(dashboard, user.id) }
    }

    suspend fun deleteDashboard(
        user: UserPrincipal,
        id: String,
    ) {
        requireEditable(user, id, "Only the owner or an admin can delete '$id'")
        delete(Kind.DASHBOARD, id)
    }

    suspend fun shares(
        user: UserPrincipal,
        dashboardId: String,
    ): List<Long> {
        requireEditable(user, dashboardId)
        return db.tx { DashboardShareRepository.userIds(dashboardId) }
    }

    suspend fun setShares(
        user: UserPrincipal,
        dashboardId: String,
        userIds: List<Long>,
    ) {
        requireEditable(user, dashboardId)
        db.tx { DashboardShareRepository.replace(dashboardId, userIds) }
    }

    suspend fun export(user: UserPrincipal): Bundle {
        if (user.role == ADMIN) {
            return Bundle(
                datasets = datasets(),
                sources = list(Kind.SOURCE, Source.serializer()),
                dashboards = list(Kind.DASHBOARD, Dashboard.serializer()),
            )
        }
        val used = visibleDatasets(user)
        return Bundle(datasets = datasets().filter { it.id in used }, dashboards = list(Kind.DASHBOARD, Dashboard.serializer(), user))
    }

    suspend fun import(bundle: Bundle): ImportResult =
        db.tx {
            val errors = mutableListOf<String>()
            val created = mutableListOf<Boolean>()

            fun attempt(
                what: String,
                store: () -> Boolean,
            ) {
                try {
                    created += store()
                } catch (e: ApiException) {
                    errors += (listOf("$what: ${e.message}") + e.details).joinToString("; ")
                }
            }
            if (bundle.version != DEFINITION_VERSION) errors += "version: must be $DEFINITION_VERSION"
            bundle.datasets.forEach { attempt("dataset ${it.id}") { storeDataset(it) } }
            bundle.sources.forEach { attempt("source ${it.id}") { storeSource(it) } }
            bundle.dashboards.forEach { attempt("dashboard ${it.id}") { storeDashboard(it, ownerId = null) } }
            if (errors.isEmpty()) {
                ImportResult(created = created.count { it }, updated = created.count { !it })
            } else {
                rollback()
                ImportResult(created = 0, updated = 0, errors = errors)
            }
        }

    private suspend fun requireDatasetAccess(
        user: UserPrincipal,
        id: String,
    ) {
        if (user.role == VIEWER && id !in visibleDatasets(user)) throw forbidden("Dataset '$id' is not used by a dashboard you can view")
    }

    private suspend fun requireEditable(
        user: UserPrincipal,
        id: String,
        message: String = "Not allowed for your role",
    ) {
        find(Kind.DASHBOARD, id, Dashboard.serializer()) ?: throw unknown(Kind.DASHBOARD, id)
        if (!canEdit(user, id)) throw forbidden(message)
    }

    private suspend fun canEdit(
        user: UserPrincipal,
        dashboardId: String,
    ): Boolean = user.role == ADMIN || (user.role == EDITOR && db.tx { DefinitionRepository.ownerId(dashboardId) } == user.id)

    private suspend fun visibleDatasets(user: UserPrincipal): Set<String> =
        list(Kind.DASHBOARD, Dashboard.serializer(), user)
            .flatMap { d -> d.widgets.map { it.query.dataset } + d.variables.mapNotNull { it.dataset } }
            .toSet()

    private suspend fun references(
        datasetId: String,
        names: Set<String>? = null,
    ): List<String> {
        fun uses(used: Collection<String>) = names == null || used.any { it in names }
        val dashboards =
            list(Kind.DASHBOARD, Dashboard.serializer()).flatMap { d ->
                d.widgets.filter { it.query.dataset == datasetId && uses(it.uses()) }.map { "${d.id}/${it.id}" } +
                    d.variables.filter { it.dataset == datasetId && uses(listOfNotNull(it.dimension)) }.map { "${d.id}/$${it.name}" }
            }
        val sources =
            list(Kind.SOURCE, Source.serializer())
                .filter { s -> s.dataset == datasetId && uses(s.mapping.flatMap { it.dims.keys + it.vals.keys + it.constDims.keys }) }
                .map { "source ${it.id}" }
        return dashboards + sources
    }

    private fun Widget.uses(): List<String> =
        query.measures.flatMap { listOfNotNull(it.field, it.by) } + query.filter.keys + query.groupBy + listOfNotNull(options.x)

    private suspend fun <T> list(
        kind: Kind,
        serializer: KSerializer<T>,
        visibleTo: UserPrincipal? = null,
    ): List<T> = db.tx { DefinitionRepository.list(kind, serializer, visibleTo) }

    private suspend fun <T> find(
        kind: Kind,
        id: String,
        serializer: KSerializer<T>,
    ): T? = db.tx { DefinitionRepository.get(kind, id, serializer) }

    private suspend fun document(
        kind: Kind,
        id: String,
    ): JsonElement = find(kind, id, JsonElement.serializer()) ?: throw unknown(kind, id)

    private suspend fun delete(
        kind: Kind,
        id: String,
    ): Boolean =
        db.tx {
            when (kind) {
                Kind.DATASET -> RecordRepository.deleteDataset(id)
                Kind.SOURCE -> SourceCredentialRepository.delete(id)
                Kind.DASHBOARD -> DashboardShareRepository.delete(id)
            }
            DefinitionRepository.delete(kind, id)
        }

    private fun storeDataset(dataset: Dataset): Boolean {
        validateDataset(dataset)
        return DefinitionRepository.upsert(Kind.DATASET, dataset.id, dataset, Dataset.serializer(), null)
    }

    private fun storeSource(source: Source): Boolean {
        val dataset =
            DefinitionRepository.get(Kind.DATASET, source.dataset, Dataset.serializer())
                ?: throw unknownReference(Kind.DATASET, source.dataset)
        validateSource(source, dataset)
        return DefinitionRepository.upsert(Kind.SOURCE, source.id, source, Source.serializer(), null)
    }

    private fun storeDashboard(
        dashboard: Dashboard,
        ownerId: Long?,
    ): Boolean {
        validateDashboard(dashboard)
        val missing =
            dashboard.widgets
                .map { it.query.dataset }
                .distinct()
                .filter { DefinitionRepository.get(Kind.DATASET, it, Dataset.serializer()) == null }
        if (missing.isNotEmpty()) throw badRequest("Unknown dataset(s) in widgets", missing)
        return DefinitionRepository.upsert(Kind.DASHBOARD, dashboard.id, dashboard, Dashboard.serializer(), ownerId)
    }

    private fun unknown(
        kind: Kind,
        id: String,
    ) = notFound("Unknown ${kind.key} '$id'")

    private fun unknownReference(
        kind: Kind,
        id: String,
    ) = badRequest("Unknown ${kind.key} '$id'")
}
