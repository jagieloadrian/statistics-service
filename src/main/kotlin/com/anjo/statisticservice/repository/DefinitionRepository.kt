package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Kind
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.definitionJson
import com.anjo.statisticservice.model.dto.DefinitionSummary
import kotlinx.serialization.KSerializer
import java.sql.Connection

object DefinitionRepository {

    /** [visibleTo] limits dashboards to the ones that user may view; null = everything. */
    fun <T> list(c: Connection, kind: Kind, ser: KSerializer<T>, visibleTo: UserPrincipal? = null): List<T> {
        val (where, params) = visible(kind, visibleTo)
        return c.query("SELECT body::text FROM definitions WHERE org_id = ? AND kind = ?$where ORDER BY id", Db.ORG, kind.key, *params) {
            definitionJson.decodeFromString(ser, it.getString(1))
        }
    }

    fun summaries(c: Connection, kind: Kind, visibleTo: UserPrincipal? = null): List<DefinitionSummary> {
        val (where, params) = visible(kind, visibleTo)
        return c.query(
            "SELECT id, coalesce(body->>'label', body->>'title'), updated_at::text FROM definitions WHERE org_id = ? AND kind = ?$where ORDER BY id",
            Db.ORG, kind.key, *params,
        ) { DefinitionSummary(it.getString(1), it.getString(2), it.getString(3)) }
    }

    fun isVisible(c: Connection, u: UserPrincipal, dashboardId: String): Boolean {
        val (where, params) = visible(Kind.DASHBOARD, u)
        return c.query("SELECT 1 FROM definitions WHERE org_id = ? AND kind = 'dashboard' AND id = ?$where", Db.ORG, dashboardId, *params) { 1 }.isNotEmpty()
    }

    fun ownerId(c: Connection, dashboardId: String): Long? =
        c.query("SELECT owner_id FROM definitions WHERE org_id = ? AND kind = 'dashboard' AND id = ?", Db.ORG, dashboardId) { it.getObject(1) as Long? }
            .singleOrNull()

    fun <T> get(c: Connection, kind: Kind, id: String, ser: KSerializer<T>): T? =
        c.query("SELECT body::text FROM definitions WHERE org_id = ? AND kind = ? AND id = ?", Db.ORG, kind.key, id) {
            definitionJson.decodeFromString(ser, it.getString(1))
        }.firstOrNull()

    /** true = created, false = updated; owner is kept on update */
    fun <T> upsert(c: Connection, kind: Kind, id: String, body: T, ser: KSerializer<T>, ownerId: Long?): Boolean =
        c.query(
            """INSERT INTO definitions (org_id, kind, id, body, owner_id, updated_at) VALUES (?, ?, ?, ?::jsonb, ?, now())
               ON CONFLICT (org_id, kind, id) DO UPDATE SET body = EXCLUDED.body, updated_at = now()
               RETURNING (xmax = 0)""",
            Db.ORG, kind.key, id, definitionJson.encodeToString(ser, body), ownerId,
        ) { it.getBoolean(1) }.first()

    fun delete(c: Connection, kind: Kind, id: String): Boolean =
        c.update("DELETE FROM definitions WHERE org_id = ? AND kind = ? AND id = ?", Db.ORG, kind.key, id) > 0

    private fun visible(kind: Kind, u: UserPrincipal?): Pair<String, Array<Any?>> =
        if (kind != Kind.DASHBOARD || u == null || u.role == ADMIN) "" to emptyArray()
        else """ AND (body->>'visibility' = 'all' OR owner_id = ? OR (body->>'visibility' = 'users' AND EXISTS (
                 SELECT 1 FROM dashboard_shares s WHERE s.org_id = definitions.org_id AND s.dashboard_id = definitions.id AND s.user_id = ?)))""" to
            arrayOf(u.id, u.id)
}
