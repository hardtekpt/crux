package com.hardtekpt.crux.ui.progress

import com.hardtekpt.crux.data.ExerciseRecord
import com.hardtekpt.crux.data.ScheduleSlot
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.TrainingStat
import com.hardtekpt.crux.data.local.ScheduleKind
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.score
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One week of training, Monday to Sunday. */
data class TrainingWeek(
    val start: LocalDate,
    /** Sessions and climbing days on the Week schedule; 0 with no schedule. */
    val planned: Int,
    /** How many of those got done: a session for each plan, a day with climbs for each climbing day. */
    val done: Int,
    val sessions: Int,
    val sets: Int,
    val minutes: Int,
    val newBests: Int,
) {
    fun value(stat: TrainingStat): Double = when (stat) {
        TrainingStat.ON_PLAN -> done.toDouble()
        TrainingStat.SESSIONS -> sessions.toDouble()
        TrainingStat.SETS -> sets.toDouble()
        TrainingStat.TIME -> minutes.toDouble()
        TrainingStat.NEW_BESTS -> newBests.toDouble()
    }
}

/** One day an exercise was trained: its best result that day, and how many results there were. */
data class ExerciseDay(val date: LocalDate, val best: ExerciseRecord, val results: Int, val fromSession: Boolean, val isBest: Boolean)

/** An exercise's progress: its best ever, and each day it was trained, newest first. */
data class ExerciseProgress(val exercise: Exercise, val best: ExerciseRecord, val days: List<ExerciseDay>) {
    val lastTrained: LocalDate get() = days.first().date

    /** The best was set lately, so it gets the star. */
    fun bestIsNew(today: LocalDate): Boolean = !best.date.isBefore(today.minusDays(NEW_BEST_DAYS))

    /** Results per week over the weeks ending this one, oldest first. */
    fun weeklyResults(today: LocalDate, weeks: Int): List<Pair<LocalDate, Int>> {
        val monday = today.monday()
        return (weeks - 1 downTo 0).map { back ->
            val start = monday.minusWeeks(back.toLong())
            start to days.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) }.sumOf { it.results }
        }
    }
}

data class TrainingProgress(
    /** The last weeks, oldest first, ending with this one. */
    val weeks: List<TrainingWeek> = emptyList(),
    val scheduled: Boolean = false,
    val sessionsLast30: Int = 0,
    val minutesLast30: Int = 0,
    val newBestsLast30: Int = 0,
    /** Every exercise with a result, most recently trained first. */
    val exercises: List<ExerciseProgress> = emptyList(),
) {
    val isEmpty: Boolean get() = exercises.isEmpty() && weeks.all { it.sessions == 0 }
    val thisWeek: TrainingWeek? get() = weeks.lastOrNull()
}

/** The figure a result is charted by: the load where load counts, else the time held, else the reps. */
fun MetricType.headline(record: ExerciseRecord): Double = when {
    usesLoad -> record.loadKg ?: 0.0
    usesTime && !usesIntervals -> (record.seconds ?: 0).toDouble()
    else -> (record.reps ?: 0).toDouble()
}

private fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun MetricType.better(a: ExerciseRecord, b: ExerciseRecord): Boolean {
    val sa = score(a)
    val sb = score(b)
    return sa.indices.map { sa[it].compareTo(sb[it]) }.firstOrNull { it != 0 }?.let { it > 0 } ?: false
}

/**
 * Training progress from what Crux already keeps: finished sessions, every result (hand-logged and
 * from sessions), the Week schedule and the days you climbed.
 */
fun trainingProgress(
    sessions: List<Session>,
    records: List<ExerciseRecord>,
    exercises: List<Exercise>,
    slots: List<ScheduleSlot>,
    climbDays: Set<LocalDate>,
    today: LocalDate,
    zone: ZoneId,
    weeks: Int = 8,
): TrainingProgress {
    val byId = exercises.associateBy { it.id }
    // Results that beat every earlier one on their exercise; the first result on an exercise isn't one.
    val newBestDates = mutableListOf<LocalDate>()
    val progress = records.groupBy { it.exerciseId }.mapNotNull { (id, mine) ->
        val exercise = byId[id] ?: return@mapNotNull null
        val metric = exercise.metric
        var best: ExerciseRecord? = null
        mine.sortedWith(compareBy<ExerciseRecord> { it.date }.thenBy { it.id }).forEach { record ->
            val before = best
            if (before == null || metric.better(record, before)) {
                if (before != null) newBestDates += record.date
                best = record
            }
        }
        val top = best ?: return@mapNotNull null
        val days = mine.groupBy { it.date }.map { (date, onDay) ->
            val dayBest = onDay.reduce { a, b -> if (metric.better(b, a)) b else a }
            ExerciseDay(date, dayBest, onDay.size, onDay.any { it.fromSession }, isBest = top in onDay)
        }.sortedByDescending { it.date }
        ExerciseProgress(exercise, top, days)
    }.sortedByDescending { it.lastTrained }

    val finished = sessions.filter { !it.running }
    fun dateOf(session: Session) = Instant.ofEpochMilli(session.startedAtMillis).atZone(zone).toLocalDate()
    val planSlots = slots.count { it.kind == ScheduleKind.PLAN }
    val climbSlots = slots.count { it.kind == ScheduleKind.CLIMB }
    val monday = today.monday()
    val trainingWeeks = (weeks - 1 downTo 0).map { back ->
        val start = monday.minusWeeks(back.toLong())
        val end = start.plusDays(7)
        fun inWeek(date: LocalDate) = !date.isBefore(start) && date.isBefore(end)
        val mine = finished.filter { inWeek(dateOf(it)) }
        val training = mine.filter { it.items.isNotEmpty() }
        val climbed = climbDays.count(::inWeek)
        TrainingWeek(
            start = start,
            planned = planSlots + climbSlots,
            done = minOf(planSlots, training.size) + minOf(climbSlots, climbed),
            sessions = training.size,
            sets = training.sumOf { it.setsDone },
            minutes = training.sumOf { session -> (session.durationMillis(session.endedAtMillis ?: session.startedAtMillis) / 60_000).toInt() },
            newBests = newBestDates.count(::inWeek),
        )
    }
    val since = today.minusDays(29)
    val recent = finished.filter { it.items.isNotEmpty() && !dateOf(it).isBefore(since) }
    return TrainingProgress(
        weeks = trainingWeeks,
        scheduled = slots.isNotEmpty(),
        sessionsLast30 = recent.size,
        minutesLast30 = recent.sumOf { (it.durationMillis(it.endedAtMillis ?: it.startedAtMillis) / 60_000).toInt() },
        newBestsLast30 = newBestDates.count { !it.isBefore(since) },
        exercises = progress,
    )
}

/** How long a "new best" keeps its star. */
const val NEW_BEST_DAYS = 14L

/** "45 min", "1 h 20 min", "9 h". */
fun minutesLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 || minutes >= 600 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Training progress as it changes, for Progress and Home. */
class TrainingProgressSource @javax.inject.Inject constructor(
    private val sessions: com.hardtekpt.crux.data.SessionRepository,
    private val records: com.hardtekpt.crux.data.RecordRepository,
    private val exercises: com.hardtekpt.crux.data.ExerciseRepository,
    private val schedule: com.hardtekpt.crux.data.ScheduleRepository,
    private val climbs: com.hardtekpt.crux.data.ClimbRepository,
    private val clock: java.time.Clock,
) {
    fun observe(): kotlinx.coroutines.flow.Flow<TrainingProgress> = kotlinx.coroutines.flow.combine(
        sessions.observeFinished(),
        records.observeAll(),
        exercises.observeExercises(),
        schedule.observeWeek(),
        climbs.observeClimbs(),
    ) { finished, results, library, slots, climbList ->
        trainingProgress(finished, results, library, slots, climbList.map { it.date }.toSet(), LocalDate.now(clock), clock.zone)
    }
}
