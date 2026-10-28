package com.anjo.statisticservice.service

import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.model.AGGREGATIONS
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.NUMBER_AGGREGATIONS
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.RANKED_AGGREGATIONS
import com.anjo.statisticservice.model.RAW_BUCKET
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.TIMESTAMP_AGGREGATIONS
import com.anjo.statisticservice.model.TIMESTAMP_FIELD
import com.anjo.statisticservice.model.dimensionNames
import com.anjo.statisticservice.model.measureType
import com.anjo.statisticservice.model.names
import com.anjo.statisticservice.model.numberMeasures
import com.anjo.statisticservice.service.mapping.Expr

private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,62}$")
private val ID = Regex("^[A-Za-z_][A-Za-z0-9_-]{0,62}$")
private val WIDGET_TYPES = setOf("line", "area", "bar", "stat", "gauge", "table")
private val VISIBILITIES = setOf("private", "users", "all")
private val TIMESTAMP_FORMATS = setOf("iso", "epoch_ms", "epoch_s")
private const val SUPPORTED_VERSION = 1
private const val MIN_REFRESH_SECONDS = 5
private const val GRID_COLUMNS = 12

fun violations(rules: MutableList<String>.() -> Unit): List<String> = buildList(rules)

fun MutableList<String>.ensure(
    ok: Boolean,
    reason: () -> String,
) {
    if (!ok) add(reason())
}

fun datasetViolations(d: Dataset): List<String> =
    violations {
        ensure(d.version == SUPPORTED_VERSION) { "version: must be $SUPPORTED_VERSION" }
        ensure(ID.matches(d.id)) { "id: '${d.id}' must match ${ID.pattern}" }
        val names = d.names()
        names.filterNot { NAME.matches(it) }.forEach { add("name: '$it' must match ${NAME.pattern}") }
        names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add("name: '$it' is declared more than once") }
        ensure(TIMESTAMP_FIELD !in names) { "name: '$TIMESTAMP_FIELD' is reserved" }
        ensure(d.retentionDays == null || d.retentionDays > 0) { "retentionDays: must be > 0" }
        d.calculated.forEach { (name, expr) ->
            runCatching { Expr.toSql(expr, d.numberMeasures()) }.onFailure { add("calculated.$name: ${it.message}") }
        }
    }

fun sourceViolations(s: Source): List<String> =
    violations {
        ensure(s.version == SUPPORTED_VERSION) { "version: must be $SUPPORTED_VERSION" }
        ensure(ID.matches(s.id)) { "id: '${s.id}' must match ${ID.pattern}" }
        ensure(s.channel.type == "push") { "channel.type: '${s.channel.type}' not supported yet" }
        ensure(s.rateLimitPerSecond > 0) { "rateLimitPerSecond: must be > 0" }
        ensure(s.silentAfterMinutes > 0) { "silentAfterMinutes: must be > 0" }
        s.mapping.forEachIndexed { i, rule ->
            (rule.dims + rule.vals).forEach { (name, spec) ->
                ensure((spec.path == null) != (spec.constValue == null)) { "mapping[$i].$name: exactly one of path/const" }
            }
            ensure(rule.timestamp == null || rule.timestamp.format in TIMESTAMP_FORMATS) {
                "mapping[$i].timestamp.format: ${TIMESTAMP_FORMATS.joinToString(" | ")}"
            }
        }
    }

fun dashboardViolations(d: Dashboard): List<String> =
    violations {
        ensure(d.version == SUPPORTED_VERSION) { "version: must be $SUPPORTED_VERSION" }
        ensure(ID.matches(d.id)) { "id: '${d.id}' must match ${ID.pattern}" }
        ensure(d.visibility in VISIBILITIES) { "visibility: ${VISIBILITIES.joinToString(" | ")}" }
        ensure(d.refreshSeconds == null || d.refreshSeconds >= MIN_REFRESH_SECONDS) { "refreshSeconds: null or >= $MIN_REFRESH_SECONDS" }
        ensure(d.variables.count { it.type == "timeRange" } <= 1) { "variables: at most one timeRange" }
        d.widgets.forEach { w ->
            ensure(w.type in WIDGET_TYPES) { "widget ${w.id}: type must be one of $WIDGET_TYPES" }
            ensure(w.w in 1..GRID_COLUMNS) { "widget ${w.id}: w must be 1..$GRID_COLUMNS" }
            ensure(w.h >= 1) { "widget ${w.id}: h must be >= 1" }
        }
        ensure(d.widgets.map { it.id }.toSet().size == d.widgets.size) { "widgets: ids must be unique" }
    }

fun validateDataset(d: Dataset) = throwIfAny("dataset", datasetViolations(d))

fun validateDashboard(d: Dashboard) = throwIfAny("dashboard", dashboardViolations(d))

fun validateSource(
    s: Source,
    dataset: Dataset,
) {
    val dims = dataset.dimensionNames()
    val measures = dataset.measures.map { it.name }.toSet()
    val mappingViolations =
        violations {
            s.mapping.forEachIndexed { i, rule ->
                (rule.dims.keys + rule.constDims.keys).filterNot { it in dims }.forEach { add("mapping[$i]: dimension '$it' not declared in dataset") }
                rule.vals.keys.filterNot { it in measures }.forEach { add("mapping[$i]: measure '$it' not declared in dataset") }
            }
        }
    throwIfAny("source", sourceViolations(s) + mappingViolations)
}

fun validateQuery(
    q: Query,
    ds: Dataset,
) {
    val reasons =
        violations {
            (q.groupBy + q.filter.keys).filterNot { it in ds.dimensionNames() }.forEach { add("Unknown dimension '$it'") }
            q.measures.forEach { addAll(measureViolations(it, ds, raw = q.bucket == RAW_BUCKET)) }
        }
    throwIfAny("query", reasons)
}

private fun measureViolations(
    m: QueryMeasure,
    ds: Dataset,
    raw: Boolean,
): List<String> =
    violations {
        val isTimestamp = m.field == TIMESTAMP_FIELD
        val type = ds.measureType(m.field)
        if (!isTimestamp && type == null) {
            add("Unknown measure '${m.field}'")
            return@violations
        }
        if (raw || m.agg == null) return@violations
        val agg = m.agg
        ensure(agg in AGGREGATIONS) { "agg: ${AGGREGATIONS.joinToString(" | ")}" }
        ensure(agg !in NUMBER_AGGREGATIONS || type == MeasureType.NUMBER) { "agg '$agg' needs a number measure: '${m.field}'" }
        ensure(!isTimestamp || agg in TIMESTAMP_AGGREGATIONS) { "agg '$agg' not allowed on $TIMESTAMP_FIELD" }
        if (agg in RANKED_AGGREGATIONS) {
            ensure(m.by != null) { "agg '$agg' needs 'by'" }
            ensure(m.by == null || ds.measureType(m.by) == MeasureType.NUMBER) { "'by' must be a number measure: '${m.by}'" }
        }
    }

fun normalizeEmail(email: String) = email.trim().lowercase()

private fun throwIfAny(
    what: String,
    reasons: List<String>,
) {
    if (reasons.isNotEmpty()) throw badRequest("Invalid $what", reasons)
}
