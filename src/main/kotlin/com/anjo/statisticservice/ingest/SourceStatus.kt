package com.anjo.statisticservice.ingest

import com.anjo.statisticservice.definitions.Source
import kotlinx.serialization.Serializable
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
data class Rejection(val at: String, val reason: String, val excerpt: String)

@Serializable
data class Counts(val hour: Long, val day: Long)

@Serializable
data class StatusView(
    val state: String,
    val lastRecordAt: String?,
    val accepted: Counts,
    val rejected: Counts,
    val rejections: List<Rejection>,
)

/** Per-source ingest health, in memory only; lost on restart (research D10). */
class SourceStatus {
    private class Entry {
        val minutes = TreeMap<Long, LongArray>() // epoch minute -> [accepted, rejected], last 24 h
        var lastRecordAt: Instant? = null
        val lastAttempts = ArrayDeque<Boolean>() // true = rejected, last 5
        val rejections = ArrayDeque<Rejection>() // newest first, last 50
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun accepted(sourceId: String, count: Int, now: Instant = Clock.System.now()) = update(sourceId, now, rejected = false) { e ->
        e.lastRecordAt = now
        bucket(e, now)[0] += count.toLong()
    }

    fun rejected(sourceId: String, reasons: List<String>, payload: String, now: Instant = Clock.System.now()) =
        update(sourceId, now, rejected = true) { e ->
            bucket(e, now)[1]++
            e.rejections.addFirst(Rejection(now.toString(), reasons.joinToString("; "), payload.take(500)))
            if (e.rejections.size > 50) e.rejections.removeLast()
        }

    fun forget(sourceId: String) {
        entries.remove(sourceId)
    }

    fun view(source: Source, now: Instant = Clock.System.now()): StatusView {
        val e = entries[source.id] ?: Entry()
        return synchronized(e) {
            val minute = now.epochSeconds / 60
            fun sum(i: Int, since: Long) = e.minutes.tailMap(since, true).values.sumOf { it[i] }
            val last = e.lastRecordAt
            val state = when {
                source.paused -> "paused"
                e.lastAttempts.size == 5 && e.lastAttempts.all { it } -> "erroring"
                last == null -> "waiting-for-data"
                now - last > source.silentAfterMinutes.minutes -> "silent"
                else -> "receiving"
            }
            StatusView(
                state, last?.toString(),
                Counts(sum(0, minute - 59), sum(0, minute - 1439)),
                Counts(sum(1, minute - 59), sum(1, minute - 1439)),
                e.rejections.toList(),
            )
        }
    }

    private fun update(sourceId: String, now: Instant, rejected: Boolean, block: (Entry) -> Unit) {
        val e = entries.computeIfAbsent(sourceId) { Entry() }
        synchronized(e) {
            block(e)
            e.lastAttempts.addLast(rejected)
            if (e.lastAttempts.size > 5) e.lastAttempts.removeFirst()
            e.minutes.headMap(now.epochSeconds / 60 - 1439).clear()
        }
    }

    private fun bucket(e: Entry, now: Instant) = e.minutes.getOrPut(now.epochSeconds / 60) { LongArray(2) }
}
