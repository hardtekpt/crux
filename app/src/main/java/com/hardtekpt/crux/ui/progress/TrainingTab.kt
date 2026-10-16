package com.hardtekpt.crux.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.you.describe
import java.time.LocalDate

/** The Training tab: plan against done over the last weeks, three figures, then every exercise. */
fun LazyListScope.training(progress: TrainingProgress, today: LocalDate, onOpenExercise: (Long) -> Unit) {
    if (progress.isEmpty) {
        item {
            EmptyState(
                icon = Icons.Rounded.FitnessCenter,
                headline = "No training yet",
                sentence = "Finish a session or log a result on an exercise and your training shows up here.",
            )
        }
        return
    }
    item(key = "training_week") {
        val week = progress.thisWeek
        Eyebrow(
            when {
                week == null -> "This week"
                progress.scheduled -> "This week · ${week.done} of ${week.planned} planned"
                else -> "This week · ${week.sessions} ${if (week.sessions == 1) "session" else "sessions"}"
            },
            Modifier.padding(bottom = CruxTheme.space.s1),
        )
    }
    item(key = "training_bars") { WeekBars(progress.weeks, progress.scheduled, Modifier.testTag("training_weeks")) }
    item(key = "training_figures") {
        Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.padding(top = CruxTheme.space.s2)) {
            StatTile(label = "Sessions", value = progress.sessionsLast30.toString(), delta = "last 30 days", modifier = Modifier.weight(1f))
            StatTile(label = "Trained", value = minutesLabel(progress.minutesLast30), delta = "last 30 days", modifier = Modifier.weight(1f))
            StatTile(
                label = "New bests",
                value = progress.newBestsLast30.toString(),
                delta = "last 30 days",
                isPersonalBest = progress.newBestsLast30 > 0,
                modifier = Modifier.weight(1f),
                valueModifier = Modifier.testTag("training_new_bests"),
            )
        }
    }
    if (progress.exercises.isNotEmpty()) {
        item(key = "training_exercises") {
            Eyebrow("Exercises · ${progress.exercises.size}", Modifier.padding(top = CruxTheme.space.s4, bottom = CruxTheme.space.s1))
        }
        items(progress.exercises, key = { "training_exercise_${it.exercise.id}" }) { exercise ->
            ExerciseProgressRow(exercise, today) { onOpenExercise(exercise.exercise.id) }
        }
    }
}

/**
 * The last weeks as bars: filled by how much of the plan got done, or by sessions when nothing is
 * on the schedule. This week is in the second accent.
 */
@Composable
fun WeekBars(weeks: List<TrainingWeek>, scheduled: Boolean, modifier: Modifier = Modifier, height: Dp = 72.dp, labels: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val most = weeks.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = weeks.joinToString { if (scheduled) "${it.done} of ${it.planned}" else "${it.sessions} sessions" }
        },
    ) {
        weeks.forEachIndexed { index, week ->
            val now = index == weeks.lastIndex
            val fraction = if (scheduled) week.done.toFloat() / week.planned.coerceAtLeast(1) else week.sessions.toFloat() / most
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHigh),
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceIn(0.06f, 1f))
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (now) colors.primary else colors.primary.copy(alpha = 0.55f)),
                        )
                    }
                }
                if (labels) {
                    Text(
                        if (scheduled) "${week.done}/${week.planned}" else week.sessions.toString(),
                        style = CruxTheme.type.code,
                        color = if (now) colors.onSurface else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** A small trend line, the newest point marked; gold when it's a new best. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, newBest: Boolean = false) {
    val line = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = 4.dp.toPx()
        fun at(i: Int): Offset {
            val x = if (values.size == 1) size.width - pad else pad + (size.width - 2 * pad) * i / (values.size - 1)
            val y = if (max == min) size.height / 2 else size.height - pad - ((values[i] - min) / span).toFloat() * (size.height - 2 * pad)
            return Offset(x, y)
        }
        if (values.size > 1) {
            val path = Path().apply {
                moveTo(at(0).x, at(0).y)
                (1 until values.size).forEach { lineTo(at(it).x, at(it).y) }
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(if (newBest) gold else line, radius = 3.5.dp.toPx(), center = at(values.lastIndex))
    }
}

/** An exercise in the Training tab: its name, its best, and its best set per day as a trend. */
@Composable
fun ExerciseProgressRow(progress: ExerciseProgress, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL
    val metric = progress.exercise.metric
    val newBest = progress.bestIsNew(today)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = CruxTheme.space.s4, vertical = CruxTheme.space.s3)
            .testTag("training_exercise"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(progress.exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(progress.best.describe(metric, imperial), style = CruxTheme.type.code, color = colors.onSurfaceVariant, maxLines = 1)
                if (newBest) {
                    Icon(Icons.Rounded.Star, contentDescription = "New best", tint = colors.secondary, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
        }
        Sparkline(
            progress.days.take(SPARK_DAYS).reversed().map { metric.headline(it.best) },
            Modifier.size(width = 88.dp, height = 30.dp),
            newBest = newBest,
        )
    }
}

/**
 * A ring filled to [fraction] with [label] in the middle, for "3/4 this week".
 */
@Composable
fun ProgressRing(fraction: Float, label: String, modifier: Modifier = Modifier, size: Dp = 72.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(
                    color,
                    -90f,
                    360f * fraction.coerceIn(0f, 1f),
                    false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private const val SPARK_DAYS = 12

/** What an exercise's chart shows: its best set each day, or how much was done. */
enum class ExerciseChart(val label: String) { Best("Best set"), Volume("Volume") }

/** The windows an exercise's chart offers. */
enum class ExerciseRange(val label: String, val days: Long?) { Quarter("3M", 91), Year("1Y", 365), All("All", null) }

/** The unit an exercise's headline figure is in: kg or lb, seconds, or reps. */
fun com.hardtekpt.crux.data.model.MetricType.headlineUnit(imperial: Boolean): String = when {
    usesLoad -> com.hardtekpt.crux.data.model.loadUnit(imperial)
    usesTime && !usesIntervals -> "s"
    usesIntervals -> "repeats"
    else -> "reps"
}

/** The headline figure in the climber's units. */
fun com.hardtekpt.crux.data.model.MetricType.headlineShown(record: com.hardtekpt.crux.data.ExerciseRecord, imperial: Boolean): Double =
    if (usesLoad) com.hardtekpt.crux.data.model.loadValue(headline(record), imperial) else headline(record)

/**
 * An exercise's chart: its best set on each day it was trained (load, time held or reps), or the
 * results logged per week (per month over a year or more), over 3 months, a year or everything.
 */
@Composable
fun ExerciseChartCard(progress: ExerciseProgress, today: LocalDate, modifier: Modifier = Modifier) {
    var chart by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(ExerciseChart.Best) }
    var range by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(ExerciseRange.Quarter) }
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL
    val metric = progress.exercise.metric
    val start = range.days?.let { today.minusDays(it) }
    val days = progress.days.filter { start == null || !it.date.isBefore(start) }.sortedBy { it.date }
    com.hardtekpt.crux.ui.components.CruxCard(modifier = modifier.fillMaxWidth().testTag("exercise_chart")) {
        Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            com.hardtekpt.crux.ui.components.CruxSegmentedButtons(
                options = ExerciseChart.entries,
                selected = chart,
                label = { it.label },
                onSelect = { chart = it },
                modifier = Modifier.weight(1.2f),
            )
            com.hardtekpt.crux.ui.components.CruxSegmentedButtons(
                options = ExerciseRange.entries,
                selected = range,
                label = { it.label },
                onSelect = { range = it },
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.padding(top = CruxTheme.space.s3)) {
            when {
                chart == ExerciseChart.Best && days.size < 2 -> Text(
                    "Train it on another day in this window to see the trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = CruxTheme.space.s4),
                )

                chart == ExerciseChart.Best -> com.hardtekpt.crux.ui.charts.TimeSeriesChart(
                    points = days.map { com.hardtekpt.crux.ui.charts.SeriesPoint(it.date.toEpochDay().toDouble(), metric.headlineShown(it.best, imperial)) },
                    formatX = { LocalDate.ofEpochDay(it.toLong()).shortLabel() },
                    formatY = { com.hardtekpt.crux.ui.charts.formatTick(it) },
                    unit = " " + metric.headlineUnit(imperial),
                    height = 170.dp,
                    description = "${progress.exercise.name}: best set per day",
                )

                else -> {
                    // Weeks over 3 months; months over a year or more.
                    val monthly = range != ExerciseRange.Quarter
                    val bars = if (monthly) {
                        val first = (start ?: progress.days.last().date).withDayOfMonth(1)
                        generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(today) }.toList().takeLast(24).map { month ->
                            com.hardtekpt.crux.ui.charts.BarDatum(
                                month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.UK),
                                progress.days.filter { it.date.year == month.year && it.date.month == month.month }.sumOf { it.results }.toDouble(),
                            )
                        }
                    } else {
                        progress.weeklyResults(today, 13).map { (week, count) -> com.hardtekpt.crux.ui.charts.BarDatum(week.shortLabel(), count.toDouble()) }
                    }
                    com.hardtekpt.crux.ui.charts.BarChart(
                        bars = bars,
                        formatValue = { com.hardtekpt.crux.ui.charts.formatTick(it) },
                        height = 170.dp,
                        description = "${progress.exercise.name}: sets per ${if (monthly) "month" else "week"}",
                    )
                }
            }
        }
    }
}
