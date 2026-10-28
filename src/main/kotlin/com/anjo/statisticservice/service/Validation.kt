package com.anjo.statisticservice.service

import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.names
import com.anjo.statisticservice.model.numberMeasures
import com.anjo.statisticservice.service.mapping.Expr
import java.time.ZoneId

private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,62}$")
private val ID = Regex("^[A-Za-z_][A-Za-z0-9_-]{0,62}$")
private val WIDGET_TYPES = setOf("line", "area", "bar", "stat", "gauge", "table")

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
    val names = d.names()
    names.filterNot { NAME.matches(it) }.forEach { r += "name: '$it' must match ${NAME.pattern}" }
    names.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { r += "name: '$it' is declared more than once" }
    ensure("ts" !in names, r) { "name: 'ts' is reserved" }
    ensure(d.retentionDays == null || d.retentionDays > 0, r) { "retentionDays: must be > 0" }
    d.calculated.forEach { (name, expr) ->
        runCatching { Expr.toSql(expr, d.numberMeasures()) }.onFailure { r += "calculated.$name: ${it.message}" }
    }
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

fun validatePrefs(p: Prefs) {
    if (p.units !in setOf("metric", "imperial")) throw badRequest("units: metric | imperial")
    if (p.theme !in setOf("system", "light", "dark")) throw badRequest("theme: system | light | dark")
    if (p.timeZone != null && runCatching { ZoneId.of(p.timeZone) }.isFailure) throw badRequest("timeZone: unknown zone '${p.timeZone}'")
}

fun requirePassword(p: String) {
    if (p.length < 10) throw badRequest("password: at least 10 characters")
}

fun normalizeEmail(email: String) = email.trim().lowercase()
