package com.anjo.statisticservice.service.mapping

import com.anjo.statisticservice.exception.MappingException
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.FieldSpec
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.Record
import com.anjo.statisticservice.model.Rule
import com.anjo.statisticservice.model.Source
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Turns one payload into records of the source's dataset. */
object Mapper {

    fun map(source: Source, dataset: Dataset, payload: JsonElement, now: Instant): List<Record> {
        val reasons = mutableListOf<String>()
        val records = if (source.mapping.isEmpty()) native(dataset, payload, now, reasons)
        else source.mapping.flatMapIndexed { i, rule -> applyRule(rule, i, dataset, payload, now, reasons) }
        if (reasons.isNotEmpty()) throw MappingException(reasons)
        return records
    }

    private fun applyRule(rule: Rule, index: Int, ds: Dataset, root: JsonElement, now: Instant, reasons: MutableList<String>): List<Record> {
        val items: List<Pair<String?, JsonElement?>> = when (val each = rule.each) {
            null -> listOf(null to null)
            else -> when (val coll = lookup(root, each)) {
                is JsonObject -> coll.entries.map { it.key to it.value }
                is JsonArray -> coll.mapIndexed { i, e -> i.toString() to e }
                else -> {
                    reasons += "mapping[$index].each: no object or array at path '$each'"
                    return emptyList()
                }
            }
        }
        return items.mapNotNull { (key, item) ->
            fun resolve(path: String): JsonElement? = when {
                path == "@key" -> key?.let(::JsonPrimitive)
                path.startsWith("@.") -> item?.let { lookup(it, path.removePrefix("@.")) }
                else -> lookup(root, path)
            }

            val before = reasons.size
            val (ts, estimated) = timestamp(rule, ::resolve, now, reasons)
            val dims = buildMap {
                rule.dims.forEach { (name, f) ->
                    val v = f.constValue ?: resolve(f.path!!)
                    when {
                        v == null || v is JsonNull -> reasons += "dims.$name: missing at path '${f.path}'"
                        v !is JsonPrimitive -> reasons += "dims.$name: expected a value at path '${f.path}'"
                        f.notBlank && v.content.isBlank() -> reasons += "dims.$name: must not be blank"
                        else -> put(name, v.content)
                    }
                }
                putAll(rule.constDims)
            }
            val vals = buildMap {
                rule.vals.forEach { (name, f) -> value(name, f, f.constValue ?: resolve(f.path!!), ds, reasons)?.let { put(name, it) } }
            }
            val ident = if (rule.identity.isEmpty()) null else {
                val parts = rule.identity.map { p ->
                    (resolve(p) as? JsonPrimitive)?.content ?: "".also { reasons += "identity: missing at path '$p'" }
                }
                (parts + listOfNotNull(key)).joinToString("|")
            }
            if (reasons.size > before) null else Record(ds.id, ts, estimated, dims, vals, ident)
        }
    }

    private fun timestamp(rule: Rule, resolve: (String) -> JsonElement?, now: Instant, reasons: MutableList<String>): Pair<Instant, Boolean> {
        val spec = rule.timestamp ?: return now to true
        val raw = resolve(spec.path) as? JsonPrimitive
        if (raw == null || raw is JsonNull) return now to true
        val ts = parseTs(raw, spec.format) ?: run {
            reasons += "timestamp: cannot parse '${raw.content}' as ${spec.format}"
            return now to true
        }
        if (spec.maxAgeHours != null && ts < now - spec.maxAgeHours.hours) reasons += "timestamp: older than ${spec.maxAgeHours} h"
        return ts to false
    }

    private fun parseTs(raw: JsonPrimitive, format: String): Instant? = runCatching {
        when (format) {
            "epoch_ms" -> Instant.fromEpochMilliseconds(raw.longOrNull ?: raw.content.toLong())
            "epoch_s" -> Instant.fromEpochMilliseconds(((raw.doubleOrNull ?: raw.content.toDouble()) * 1000).toLong())
            // iso: instant with offset, or local date-time read as UTC (RPi payloads)
            else -> runCatching { Instant.parse(raw.content) }.getOrElse { LocalDateTime.parse(raw.content).toInstant(TimeZone.UTC) }
        }
    }.getOrNull()

    private fun value(name: String, f: FieldSpec, v: JsonElement?, ds: Dataset, reasons: MutableList<String>): JsonPrimitive? {
        if (v == null || v is JsonNull) {
            if (f.required) reasons += "vals.$name: missing at path '${f.path}'"
            return null
        }
        if (v !is JsonPrimitive) {
            reasons += "vals.$name: expected a value at path '${f.path}'"
            return null
        }
        return when (ds.measures.first { it.name == name }.type) {
            MeasureType.number -> {
                val d = (v.doubleOrNull ?: v.content.toDoubleOrNull())?.let { it * (f.scale ?: 1.0) }
                when {
                    d == null -> null.also { reasons += "vals.$name: expected a number, got '${v.content}'" }
                    f.min != null && d < f.min -> null.also { reasons += "vals.$name: $d is below min ${f.min}" }
                    f.max != null && d > f.max -> null.also { reasons += "vals.$name: $d is above max ${f.max}" }
                    else -> JsonPrimitive(d)
                }
            }
            MeasureType.boolean -> (v.booleanOrNull ?: v.content.toBooleanStrictOrNull())?.let(::JsonPrimitive)
                ?: null.also { reasons += "vals.$name: expected a boolean, got '${v.content}'" }
            MeasureType.text -> if (f.notBlank && v.content.isBlank()) null.also { reasons += "vals.$name: must not be blank" }
            else JsonPrimitive(v.content)
        }
    }

    /** Native record format: `{ts?, dims, vals}` or a list of them. */
    private fun native(ds: Dataset, payload: JsonElement, now: Instant, reasons: MutableList<String>): List<Record> {
        val items = when (payload) {
            is JsonArray -> payload
            is JsonObject -> listOf(payload)
            else -> return emptyList<Record>().also { reasons += "body: expected a record object or an array of records" }
        }
        val dimNames = ds.dimensions.map { it.name }.toSet()
        return items.mapIndexedNotNull { i, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null.also { reasons += "[$i]: expected an object" }
            val before = reasons.size
            val ts = (obj["ts"] as? JsonPrimitive)?.takeIf { it !is JsonNull }
            val parsed = ts?.let { parseTs(it, "iso") ?: null.also { _ -> reasons += "[$i].ts: cannot parse '${it.content}'" } }
            val dims = (obj["dims"] as? JsonObject).orEmpty().mapValues { (k, v) ->
                if (k !in dimNames) reasons += "[$i].dims.$k: not declared in dataset"
                (v as? JsonPrimitive)?.content.orEmpty()
            }
            val vals = (obj["vals"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
                if (ds.measures.none { it.name == k }) null.also { reasons += "[$i].vals.$k: not declared in dataset" }
                else value("$k", FieldSpec(path = "[$i].vals.$k"), v, ds, reasons)?.let { k to it }
            }.toMap()
            if (reasons.size > before) null else Record(ds.id, parsed ?: now, parsed == null, dims, vals, null)
        }
    }

    private fun lookup(root: JsonElement, path: String): JsonElement? =
        path.split('.').fold(root as JsonElement?) { e, seg ->
            when (e) {
                is JsonObject -> e[seg]
                is JsonArray -> seg.toIntOrNull()?.let { e.getOrNull(it) }
                else -> null
            }
        }

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
}
