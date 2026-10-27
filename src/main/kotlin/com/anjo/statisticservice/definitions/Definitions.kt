package com.anjo.statisticservice.definitions

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.routing.badRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.util.Base64

// Definition format v1 — contracts/definition-format.md

@Serializable
enum class MeasureType { number, boolean, text }

@Serializable
data class Measure(val name: String, val type: MeasureType = MeasureType.number, val unit: String? = null, val label: String? = null)

@Serializable
data class Dimension(val name: String, val label: String? = null)

@Serializable
data class Dataset(
    val version: Int = 1,
    val kind: String = "dataset",
    val id: String,
    val label: String? = null,
    val measures: List<Measure> = emptyList(),
    val dimensions: List<Dimension> = emptyList(),
    val calculated: Map<String, String> = emptyMap(),
    val retentionDays: Int? = null,
)

// ponytail: push only; pull/kafka/grpc settings land with their channel (US5)
@Serializable
data class Channel(val type: String = "push")

@Serializable
data class TimestampSpec(val path: String, val format: String = "iso", val maxAgeHours: Int? = null)

@Serializable
data class FieldSpec(
    val path: String? = null,
    @SerialName("const") val constValue: JsonPrimitive? = null,
    val required: Boolean = true,
    val notBlank: Boolean = true,
    val min: Double? = null,
    val max: Double? = null,
    val scale: Double? = null,
)

@Serializable
data class Rule(
    val each: String? = null,
    val timestamp: TimestampSpec? = null,
    val identity: List<String> = emptyList(),
    val dims: Map<String, FieldSpec> = emptyMap(),
    val vals: Map<String, FieldSpec> = emptyMap(),
    @SerialName("const") val constDims: Map<String, String> = emptyMap(),
)

@Serializable
data class Source(
    val version: Int = 1,
    val kind: String = "source",
    val id: String,
    val label: String? = null,
    val channel: Channel = Channel(),
    val paused: Boolean = false,
    val rateLimitPerSecond: Int = 50,
    val silentAfterMinutes: Int = 10,
    val dataset: String,
    val mapping: List<Rule> = emptyList(),
)

@Serializable
data class QueryMeasure(val field: String, val agg: String? = null, val by: String? = null, @SerialName("as") val alias: String? = null)

@Serializable
data class RelativeRange(val last: String)

@Serializable
data class Query(
    val dataset: String,
    val measures: List<QueryMeasure> = emptyList(),
    val filter: Map<String, List<String>> = emptyMap(),
    val groupBy: List<String> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    val range: RelativeRange? = null,
    val bucket: String = "auto",
    val maxPoints: Int = 1000,
)

@Serializable
data class Threshold(val value: Double, val color: String)

@Serializable
data class WidgetOptions(
    val x: String? = null,
    val unit: String? = null,
    val colors: Map<String, String> = emptyMap(),
    val min: Double? = null,
    val max: Double? = null,
    val thresholds: List<Threshold> = emptyList(),
    val decimals: Int? = null,
)

@Serializable
data class Widget(
    val id: String,
    val title: String = "",
    val type: String,
    val w: Int = 6,
    val h: Int = 3,
    val query: Query,
    val options: WidgetOptions = WidgetOptions(),
)

@Serializable
data class Variable(
    val name: String,
    val type: String,
    val dataset: String? = null,
    val dimension: String? = null,
    val multi: Boolean = false,
    val default: String? = null,
    val options: List<String> = emptyList(),
)

@Serializable
data class Dashboard(
    val version: Int = 1,
    val kind: String = "dashboard",
    val id: String,
    val title: String = "",
    val visibility: String = "private",
    val refreshSeconds: Int? = null,
    val defaultRange: RelativeRange? = null,
    val variables: List<Variable> = emptyList(),
    val widgets: List<Widget> = emptyList(),
)

@Serializable
data class Bundle(
    val version: Int = 1,
    val datasets: List<Dataset> = emptyList(),
    val sources: List<Source> = emptyList(),
    val dashboards: List<Dashboard> = emptyList(),
)

@Serializable
data class DefinitionSummary(val id: String, val label: String?, val updatedAt: String)

enum class Kind(val key: String) { DATASET("dataset"), SOURCE("source"), DASHBOARD("dashboard") }

val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,62}$")
val ID = Regex("^[A-Za-z_][A-Za-z0-9_-]{0,62}$")
val WIDGET_TYPES = setOf("line", "area", "bar", "stat", "gauge", "table")

val definitionJson = Json { encodeDefaults = true; explicitNulls = false }

class DefinitionStore(private val db: Db) {

    suspend fun <T> list(kind: Kind, ser: KSerializer<T>): List<T> = db.tx { c ->
        c.query("SELECT body::text FROM definitions WHERE org_id = ? AND kind = ? ORDER BY id", Db.ORG, kind.key) {
            definitionJson.decodeFromString(ser, it.getString(1))
        }
    }

    suspend fun summaries(kind: Kind): List<DefinitionSummary> = db.tx { c ->
        c.query(
            "SELECT id, coalesce(body->>'label', body->>'title'), updated_at::text FROM definitions WHERE org_id = ? AND kind = ? ORDER BY id",
            Db.ORG, kind.key,
        ) { DefinitionSummary(it.getString(1), it.getString(2), it.getString(3)) }
    }

    suspend fun <T> get(kind: Kind, id: String, ser: KSerializer<T>): T? = db.tx { c -> c.get(kind, id, ser) }

    suspend fun dataset(id: String) = get(Kind.DATASET, id, Dataset.serializer())
    suspend fun source(id: String) = get(Kind.SOURCE, id, Source.serializer())
    suspend fun dashboard(id: String) = get(Kind.DASHBOARD, id, Dashboard.serializer())

    suspend fun putDataset(d: Dataset): Boolean = db.tx { c -> c.putDataset(d) }
    suspend fun putSource(s: Source): Boolean = db.tx { c -> c.putSource(s) }
    suspend fun putDashboard(d: Dashboard, ownerId: Long? = null): Boolean = db.tx { c -> c.putDashboard(d, ownerId) }

    suspend fun delete(kind: Kind, id: String): Boolean = db.tx { c ->
        if (kind == Kind.DATASET) c.update("DELETE FROM records WHERE org_id = ? AND dataset_id = ?", Db.ORG, id)
        if (kind == Kind.SOURCE) c.update("DELETE FROM source_credentials WHERE org_id = ? AND source_id = ?", Db.ORG, id)
        c.update("DELETE FROM definitions WHERE org_id = ? AND kind = ? AND id = ?", Db.ORG, kind.key, id) > 0
    }

    /** Returns the new token once; only its SHA-256 is stored (research D9). */
    suspend fun rotateCredential(sourceId: String): String {
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        db.tx { c ->
            c.update(
                """INSERT INTO source_credentials (org_id, source_id, token_hash) VALUES (?, ?, ?)
                   ON CONFLICT (org_id, source_id) DO UPDATE SET token_hash = EXCLUDED.token_hash, created_at = now()""",
                Db.ORG, sourceId, sha256(token),
            )
        }
        return token
    }

    suspend fun verifyCredential(sourceId: String, token: String): Boolean {
        val stored = db.tx { c ->
            c.query("SELECT token_hash FROM source_credentials WHERE org_id = ? AND source_id = ?", Db.ORG, sourceId) { it.getBytes(1) }
        }.firstOrNull() ?: return false
        return MessageDigest.isEqual(stored, sha256(token))
    }

    // --- per-connection helpers, reused by import (one transaction) ---

    fun <T> Connection.get(kind: Kind, id: String, ser: KSerializer<T>): T? =
        query("SELECT body::text FROM definitions WHERE org_id = ? AND kind = ? AND id = ?", Db.ORG, kind.key, id) {
            definitionJson.decodeFromString(ser, it.getString(1))
        }.firstOrNull()

    fun Connection.putDataset(d: Dataset): Boolean {
        validateDataset(d)
        return upsert(Kind.DATASET, d.id, definitionJson.encodeToString(Dataset.serializer(), d), null)
    }

    fun Connection.putSource(s: Source): Boolean {
        val dataset = get(Kind.DATASET, s.dataset, Dataset.serializer())
            ?: throw badRequest("Unknown dataset '${s.dataset}'")
        validateSource(s, dataset)
        return upsert(Kind.SOURCE, s.id, definitionJson.encodeToString(Source.serializer(), s), null)
    }

    fun Connection.putDashboard(d: Dashboard, ownerId: Long?): Boolean {
        validateDashboard(d)
        val missing = d.widgets.map { it.query.dataset }.distinct().filter { get(Kind.DATASET, it, Dataset.serializer()) == null }
        if (missing.isNotEmpty()) throw badRequest("Unknown dataset(s) in widgets", missing)
        return upsert(Kind.DASHBOARD, d.id, definitionJson.encodeToString(Dashboard.serializer(), d), ownerId)
    }

    /** true = created, false = updated; owner is kept on update */
    private fun Connection.upsert(kind: Kind, id: String, body: String, ownerId: Long?): Boolean =
        query(
            """INSERT INTO definitions (org_id, kind, id, body, owner_id, updated_at) VALUES (?, ?, ?, ?::jsonb, ?, now())
               ON CONFLICT (org_id, kind, id) DO UPDATE SET body = EXCLUDED.body, updated_at = now()
               RETURNING (xmax = 0)""",
            Db.ORG, kind.key, id, body, ownerId,
        ) { it.getBoolean(1) }.first()
}

fun sha256(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

private fun ensure(ok: Boolean, reasons: MutableList<String>, reason: () -> String) {
    if (!ok) reasons += reason()
}

private fun throwIfAny(what: String, reasons: List<String>) {
    if (reasons.isNotEmpty()) throw badRequest("Invalid $what", reasons)
}

fun validateDataset(d: Dataset) {
    val r = mutableListOf<String>()
    ensure(d.version == 1, r) { "version: must be 1" }
    ensure(ID.matches(d.id), r) { "id: '${d.id}' must match ${ID.pattern}" }
    val names = d.measures.map { it.name } + d.dimensions.map { it.name } + d.calculated.keys
    names.filterNot { NAME.matches(it) }.forEach { r += "name: '$it' must match ${NAME.pattern}" }
    names.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { r += "name: '$it' is declared more than once" }
    ensure("ts" !in names, r) { "name: 'ts' is reserved" }
    ensure(d.retentionDays == null || d.retentionDays > 0, r) { "retentionDays: must be > 0" }
    throwIfAny("dataset", r)
}

fun validateSource(s: Source, dataset: Dataset) {
    val r = mutableListOf<String>()
    ensure(s.version == 1, r) { "version: must be 1" }
    ensure(ID.matches(s.id), r) { "id: '${s.id}' must match ${ID.pattern}" }
    ensure(s.channel.type == "push", r) { "channel.type: '${s.channel.type}' not supported yet" }
    ensure(s.rateLimitPerSecond > 0, r) { "rateLimitPerSecond: must be > 0" }
    ensure(s.silentAfterMinutes > 0, r) { "silentAfterMinutes: must be > 0" }
    val dims = dataset.dimensions.map { it.name }.toSet()
    val measures = dataset.measures.map { it.name }.toSet()
    s.mapping.forEachIndexed { i, rule ->
        (rule.dims.keys + rule.constDims.keys).filterNot { it in dims }.forEach { r += "mapping[$i]: dimension '$it' not declared in dataset" }
        rule.vals.keys.filterNot { it in measures }.forEach { r += "mapping[$i]: measure '$it' not declared in dataset" }
        (rule.dims + rule.vals).forEach { (name, f) ->
            ensure((f.path == null) != (f.constValue == null), r) { "mapping[$i].$name: exactly one of path/const" }
        }
        ensure(rule.timestamp == null || rule.timestamp.format in setOf("iso", "epoch_ms", "epoch_s"), r) {
            "mapping[$i].timestamp.format: iso | epoch_ms | epoch_s"
        }
    }
    throwIfAny("source", r)
}

fun validateDashboard(d: Dashboard) {
    val r = mutableListOf<String>()
    ensure(d.version == 1, r) { "version: must be 1" }
    ensure(ID.matches(d.id), r) { "id: '${d.id}' must match ${ID.pattern}" }
    ensure(d.visibility in setOf("private", "users", "all"), r) { "visibility: private | users | all" }
    ensure(d.refreshSeconds == null || d.refreshSeconds >= 5, r) { "refreshSeconds: null or >= 5" }
    ensure(d.variables.count { it.type == "timeRange" } <= 1, r) { "variables: at most one timeRange" }
    d.widgets.forEach { w ->
        ensure(w.type in WIDGET_TYPES, r) { "widget ${w.id}: type must be one of $WIDGET_TYPES" }
        ensure(w.w in 1..12, r) { "widget ${w.id}: w must be 1..12" }
        ensure(w.h >= 1, r) { "widget ${w.id}: h must be >= 1" }
    }
    ensure(d.widgets.map { it.id }.toSet().size == d.widgets.size, r) { "widgets: ids must be unique" }
    throwIfAny("dashboard", r)
}
