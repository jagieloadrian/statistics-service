package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.DashboardShares
import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.Definitions
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.definitionJson
import com.anjo.statisticservice.model.dto.DefinitionSummary
import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.json.extract

object DefinitionRepository {
    private val label = coalesce(Definitions.body.extract<String>("label"), Definitions.body.extract<String>("title"))
    private val updatedAtText = Definitions.updatedAt.castTo(TextColumnType())

    /** [visibleTo] limits dashboards to the ones that user may view; null = everything. */
    fun <T> list(kind: Kind, ser: KSerializer<T>, visibleTo: UserPrincipal? = null): List<T> =
        Definitions.select(Definitions.body).where { key(kind) }.visibleTo(kind, visibleTo).orderBy(Definitions.id)
            .map { definitionJson.decodeFromString(ser, it[Definitions.body]) }

    fun summaries(kind: Kind, visibleTo: UserPrincipal? = null): List<DefinitionSummary> =
        Definitions.select(Definitions.id, label, updatedAtText).where { key(kind) }.visibleTo(kind, visibleTo).orderBy(Definitions.id)
            .map { DefinitionSummary(it[Definitions.id], it[label], it[updatedAtText]) }

    fun isVisible(u: UserPrincipal, dashboardId: String): Boolean =
        !Definitions.selectAll().where { key(Kind.DASHBOARD, dashboardId) }.visibleTo(Kind.DASHBOARD, u).empty()

    fun ownerId(dashboardId: String): Long? =
        Definitions.select(Definitions.ownerId).where { key(Kind.DASHBOARD, dashboardId) }.singleOrNull()?.get(Definitions.ownerId)

    fun <T> get(kind: Kind, id: String, ser: KSerializer<T>): T? =
        Definitions.select(Definitions.body).where { key(kind, id) }.singleOrNull()
            ?.let { definitionJson.decodeFromString(ser, it[Definitions.body]) }

    /** true = created, false = updated; owner is kept on update */
    // ponytail: two racing creates both report created; RETURNING (xmax = 0) if import counts must be exact
    fun <T> upsert(kind: Kind, id: String, body: T, ser: KSerializer<T>, ownerId: Long?): Boolean {
        val created = Definitions.selectAll().where { key(kind, id) }.empty()
        Definitions.upsert(onUpdateExclude = listOf(Definitions.ownerId)) {
            it[orgId] = Db.ORG
            it[this.kind] = kind.key
            it[this.id] = id
            it[this.body] = definitionJson.encodeToString(ser, body)
            it[this.ownerId] = ownerId
            it[updatedAt] = CurrentTimestampWithTimeZone
        }
        return created
    }

    fun delete(kind: Kind, id: String): Boolean = Definitions.deleteWhere { key(kind, id) } > 0

    private fun key(kind: Kind, id: String? = null): Op<Boolean> {
        val k = (Definitions.orgId eq Db.ORG) and (Definitions.kind eq kind.key)
        return if (id == null) k else k and (Definitions.id eq id)
    }

    private fun Query.visibleTo(kind: Kind, u: UserPrincipal?): Query {
        if (kind != Kind.DASHBOARD || u == null || u.role == ADMIN) return this
        val visibility = Definitions.body.extract<String>("visibility")
        val shared = DashboardShares.select(intLiteral(1)).where {
            (DashboardShares.orgId eq Definitions.orgId) and (DashboardShares.dashboardId eq Definitions.id) and (DashboardShares.userId eq u.id)
        }
        return andWhere { (visibility eq "all") or (Definitions.ownerId eq u.id) or ((visibility eq "users") and exists(shared)) }
    }
}
