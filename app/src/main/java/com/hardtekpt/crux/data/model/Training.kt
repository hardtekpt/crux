package com.hardtekpt.crux.data.model

import java.util.Locale
import kotlin.math.roundToInt

/** What an exercise trains; groups the library. */
enum class ExerciseCategory(val label: String) {
    FINGERS("Fingers"),
    PULLING("Pulling"),
    CORE("Core"),
    CLIMBING("On the wall"),
    ANTAGONIST("Antagonist"),
    MOBILITY("Mobility"),
    OTHER("Other"),
}

/**
 * How an exercise is measured. It decides which targets a plan asks for and, later, what
 * a session logs and what counts as a personal best. New types (edge size, grade x attempts,
 * distance) slot in here.
 */
enum class MetricType(
    val label: String,
    val shortLabel: String,
    val usesReps: Boolean,
    val usesTime: Boolean,
    val usesLoad: Boolean,
) {
    REPS("Reps", "Reps", usesReps = true, usesTime = false, usesLoad = false),
    WEIGHTED_REPS("Reps with load", "Reps+kg", usesReps = true, usesTime = false, usesLoad = true),
    TIME("Time", "Time", usesReps = false, usesTime = true, usesLoad = false),
    WEIGHTED_TIME("Time with load", "Time+kg", usesReps = false, usesTime = true, usesLoad = true),
}

data class Exercise(
    val id: Long,
    val name: String,
    val category: ExerciseCategory,
    val metric: MetricType,
    val notes: String?,
)

/** What a plan asks for one exercise. Fields the metric does not use are ignored. */
data class ExerciseTarget(
    val sets: Int = 3,
    val reps: Int = 8,
    val seconds: Int = 10,
    /** Added load in kg; negative means assisted. */
    val loadKg: Double = 0.0,
    val restSeconds: Int = 120,
) {
    /** `5 × 5 · +10 kg`, `6 × 10 s · +5 kg`, `3 × 12`. */
    fun prescription(metric: MetricType): String = buildString {
        append("$sets × ")
        append(if (metric.usesTime) formatDuration(seconds) else "$reps")
        if (metric.usesLoad && loadKg != 0.0) {
            append(" · ")
            append(if (loadKg > 0) "+" else "−")
            append(formatKg(kotlin.math.abs(loadKg)))
            append(" kg")
        }
    }

    fun restLabel(): String? = if (restSeconds <= 0) null else formatDuration(restSeconds)

    /** Rough seconds this exercise takes: work plus rest between sets. */
    fun estimatedSeconds(metric: MetricType): Int {
        val work = if (metric.usesTime) seconds else reps * SECONDS_PER_REP
        return sets * work + (sets - 1).coerceAtLeast(0) * restSeconds
    }

    companion object {
        private const val SECONDS_PER_REP = 4

        fun defaultFor(metric: MetricType) = when (metric) {
            MetricType.REPS -> ExerciseTarget(sets = 3, reps = 10, restSeconds = 90)
            MetricType.WEIGHTED_REPS -> ExerciseTarget(sets = 5, reps = 5, loadKg = 10.0, restSeconds = 180)
            MetricType.TIME -> ExerciseTarget(sets = 3, seconds = 30, restSeconds = 60)
            MetricType.WEIGHTED_TIME -> ExerciseTarget(sets = 6, seconds = 10, loadKg = 5.0, restSeconds = 180)
        }
    }
}

/** `45 s`, `3 min`, `1 min 30 s`. */
fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

fun formatKg(kg: Double): String =
    if (kg == kg.roundToInt().toDouble()) kg.roundToInt().toString() else String.format(Locale.UK, "%.1f", kg)

/** One exercise placed in a plan, with its targets. */
data class PlanItem(
    val exercise: Exercise,
    val target: ExerciseTarget,
) {
    val prescription: String get() = target.prescription(exercise.metric)
}

data class PlanBlock(
    val name: String,
    val items: List<PlanItem>,
)

/** A session plan: named blocks of exercises from the library. */
data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val description: String,
    val blocks: List<PlanBlock>,
) {
    val exerciseCount: Int get() = blocks.sumOf { it.items.size }

    /** Work and rest, plus a short changeover between exercises, rounded up to 5 min. */
    val estimatedMinutes: Int
        get() {
            val seconds = blocks.flatMap { it.items }.sumOf { it.target.estimatedSeconds(it.exercise.metric) + CHANGEOVER }
            return ((seconds + 299) / 300) * 5
        }

    private companion object {
        const val CHANGEOVER = 60
    }
}
