package com.anjo.statisticservice.service

import com.anjo.statisticservice.model.Counts
import com.anjo.statisticservice.model.MinuteCounts
import com.anjo.statisticservice.model.Rejection
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.SourceActivity
import com.anjo.statisticservice.model.StatusView
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SourceStatusService {
    private val activities = ConcurrentHashMap<String, SourceActivity>()

    fun accepted(
        sourceId: String,
        count: Int,
        now: Instant,
    ) = record(sourceId, now, rejected = false) { activity ->
        activity.lastRecordAt = now
        activity.countsAt(now).accepted += count
    }

    fun rejected(
        sourceId: String,
        reasons: List<String>,
        payload: String,
        now: Instant,
    ) = record(sourceId, now, rejected = true) { activity ->
        activity.countsAt(now).rejected++
        activity.newestRejections.addFirst(Rejection(now.toString(), reasons.joinToString("; "), payload.take(EXCERPT_LENGTH)))
        if (activity.newestRejections.size > MAX_REJECTIONS) activity.newestRejections.removeLast()
    }

    fun forget(sourceId: String) {
        activities.remove(sourceId)
    }

    fun view(
        source: Source,
        now: Instant = Clock.System.now(),
    ): StatusView {
        val activity = activities[source.id] ?: SourceActivity()
        return synchronized(activity) {
            val minute = now.epochMinute()

            fun total(
                since: Long,
                count: (MinuteCounts) -> Long,
            ) = activity.countsByEpochMinute
                .tailMap(since, true)
                .values
                .sumOf(count)
            val hourStart = minute - MINUTES_PER_HOUR + 1
            val dayStart = minute - MINUTES_PER_DAY + 1
            val last = activity.lastRecordAt
            val state =
                when {
                    source.paused -> "paused"
                    activity.recentAttemptsRejected.size == ERRORING_STREAK && activity.recentAttemptsRejected.all { it } -> "erroring"
                    last == null -> "waiting-for-data"
                    now - last > source.silentAfterMinutes.minutes -> "silent"
                    else -> "receiving"
                }
            StatusView(
                state = state,
                lastRecordAt = last?.toString(),
                accepted = Counts(total(hourStart) { it.accepted }, total(dayStart) { it.accepted }),
                rejected = Counts(total(hourStart) { it.rejected }, total(dayStart) { it.rejected }),
                rejections = activity.newestRejections.toList(),
            )
        }
    }

    private fun record(
        sourceId: String,
        now: Instant,
        rejected: Boolean,
        update: (SourceActivity) -> Unit,
    ) {
        val activity = activities.computeIfAbsent(sourceId) { SourceActivity() }
        synchronized(activity) {
            update(activity)
            activity.recentAttemptsRejected.addLast(rejected)
            if (activity.recentAttemptsRejected.size > ERRORING_STREAK) activity.recentAttemptsRejected.removeFirst()
            activity.countsByEpochMinute.headMap(now.epochMinute() - MINUTES_PER_DAY + 1).clear()
        }
    }

    private fun SourceActivity.countsAt(now: Instant) = countsByEpochMinute.getOrPut(now.epochMinute()) { MinuteCounts() }

    private fun Instant.epochMinute() = epochSeconds / SECONDS_PER_MINUTE

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 1440
        const val ERRORING_STREAK = 5
        const val MAX_REJECTIONS = 50
        const val EXCERPT_LENGTH = 500
    }
}
