package com.hardtekpt.crux.ui

import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.ui.components.GradeState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** `SAT 3 OCT` style day label; eyebrows uppercase it. */
fun LocalDate.dayLabel(): String = format(DAY_FORMAT)

fun LocalDate.shortLabel(): String = format(SHORT_DAY_FORMAT)

/** Today, Yesterday, then a short date. */
fun LocalDate.relativeLabel(today: LocalDate): String = when (ChronoUnit.DAYS.between(this, today)) {
    0L -> "Today"
    1L -> "Yesterday"
    in 2L..6L -> "${ChronoUnit.DAYS.between(this, today)} days ago"
    else -> shortLabel()
}

/** One decimal, no trailing `.0` noise kept: 72.4, 73.0. */
fun Double.oneDecimal(): String = String.format(Locale.UK, "%.1f", this)

fun Double.wholeOrOneDecimal(): String =
    if (this == this.roundToInt().toDouble()) roundToInt().toString() else oneDecimal()

/** Signed change with a real minus sign: +0.4, −1.2. */
fun Double.signedOneDecimal(): String {
    val sign = when {
        this > 0.05 -> "+"
        this < -0.05 -> "−"
        else -> "±"
    }
    return sign + abs(this).oneDecimal()
}

val Climb.gradeState: GradeState
    get() = if (style.isSend) GradeState.Sent else GradeState.Attempted

/** Outcome then attempts: `Flash`, `Redpoint · 4 attempts`. */
fun Climb.outcomeLine(): String = buildString {
    append(style.label)
    if (!style.singleAttempt) {
        append(" · ")
        append(if (attempts == 1) "1 attempt" else "$attempts attempts")
    }
}

fun Climb.displayName(): String = name ?: "Unnamed ${discipline.label.lowercase()}"

/** Latest weigh-in and how it moved over about 30 days. */
data class WeightSummary(
    val latest: Measurement,
    val change: Double?,
    /** `30 days` or `since 12 Sep` when there is no entry a full 30 days back. */
    val window: String,
)

fun List<Measurement>.weightSummary(): WeightSummary? {
    val sorted = sortedByDescending { it.date }
    val latest = sorted.firstOrNull() ?: return null
    val cutoff = latest.date.minusDays(30)
    val reference = sorted.firstOrNull { !it.date.isAfter(cutoff) }
        ?: sorted.lastOrNull()?.takeIf { it.date.isBefore(latest.date) }
    return WeightSummary(
        latest = latest,
        change = reference?.let { latest.value - it.value },
        window = when {
            reference == null -> "first weigh-in"
            !reference.date.isAfter(cutoff) -> "30 days"
            else -> "since ${reference.date.shortLabel()}"
        },
    )
}
