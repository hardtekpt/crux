package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.ExerciseBest
import com.hardtekpt.crux.data.ExerciseRecord
import com.hardtekpt.crux.data.Note
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.formatKg
import com.hardtekpt.crux.ui.LocalClock
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.components.input.formatDuration
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Mono11 = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)
private val BigFigure =
    TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = "tnum",
    )

/** A section title with an optional action on the right, used to separate the profile's parts. */
@Composable
fun ProfileSectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = CruxTheme.space.s4)) {
        Text(
            title,
            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAction)
                    .padding(horizontal = CruxTheme.space.s2, vertical = CruxTheme.space.s1),
            )
        }
    }
}

/**
 * The profile's opening card, styled like a gym route tag: a strip of tape colours down the
 * left edge, then the climber's headline numbers in large type.
 */
@Composable
fun ClimberCard(
    daysThisYear: Int,
    climbsThisYear: Int,
    climbingSince: LocalDate?,
    hardestBoulder: PersonalBest?,
    hardestRoute: PersonalBest?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, shape)
            .testTag("climber_card"),
    ) {
        Column(Modifier.width(10.dp).fillMaxHeight()) {
            CruxTheme.colors.profileTape.forEach { Box(Modifier.weight(1f).fillMaxWidth().background(it)) }
        }
        Column(
            Modifier.padding(CruxTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CLIMBER", style = Mono11, color = colors.onSurfaceVariant)
                Text(
                    climbingSince?.let { "Climbing since ${it.shortLabel()} ${it.year}" } ?: "Your first climb starts the story",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s5)) {
                HeroFigure(daysThisYear.toString(), "Days ${LocalDate.now(LocalClock.current).year}")
                HeroFigure(climbsThisYear.toString(), "Climbs ${LocalDate.now(LocalClock.current).year}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3), verticalAlignment = Alignment.CenterVertically) {
                HardestChip("Boulder", hardestBoulder)
                HardestChip("Route", hardestRoute)
            }
        }
    }
}

@Composable
private fun HeroFigure(value: String, label: String) {
    Column {
        Text(value, style = BigFigure, color = MaterialTheme.colorScheme.onSurface)
        Text(label.uppercase(), style = Mono11, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HardestChip(label: String, best: PersonalBest?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        if (best != null) GradeBadge(best.grade, GradeState.PersonalBest) else GradeBadge("–", GradeState.Attempted)
        Text(label.uppercase(), style = Mono11, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Consistency, the way code hosts show commits: one square per day, a column per week,
 * shaded by how much you did. Fits as many weeks as the width allows, today at the right.
 */
@Composable
fun ConsistencyGrid(activity: Map<LocalDate, Int>, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val cell = 13.dp
    val gap = 3.dp
    val labelWidth = 22.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val weeks = (((maxWidth - labelWidth + gap) / (cell + gap)).toInt()).coerceIn(4, 53)
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = thisMonday.minusWeeks((weeks - 1).toLong())
        val activeDays = (0 until weeks * 7).map { firstMonday.plusDays(it.toLong()) }.count { (activity[it] ?: 0) > 0 && !it.isAfter(today) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Month initials over the first week of each month.
            Row(Modifier.padding(start = labelWidth)) {
                (0 until weeks).forEach { w ->
                    val monday = firstMonday.plusWeeks(w.toLong())
                    val label = if (monday.dayOfMonth <= 7) monday.month.getDisplayName(DateTextStyle.SHORT, Locale.UK) else ""
                    Box(Modifier.width(cell + gap)) {
                        Text(
                            label,
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            Row {
                Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                    listOf("M", "", "W", "", "F", "", "S").forEach {
                        Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                            Text(it, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp), color = colors.onSurfaceVariant)
                        }
                    }
                }
                Canvas(
                    Modifier
                        .width((cell + gap) * weeks)
                        .height((cell + gap) * 7)
                        .semantics { contentDescription = "$activeDays active days in the last $weeks weeks" }
                        .testTag("consistency_grid"),
                ) {
                    val c = cell.toPx()
                    val g = gap.toPx()
                    for (w in 0 until weeks) {
                        for (d in 0 until 7) {
                            val day = firstMonday.plusDays((w * 7 + d).toLong())
                            if (day.isAfter(today)) continue
                            val n = activity[day] ?: 0
                            val color = when {
                                n == 0 -> colors.surfaceContainerHighest
                                n == 1 -> colors.primary.copy(alpha = 0.35f)
                                n <= 3 -> colors.primary.copy(alpha = 0.6f)
                                n <= 5 -> colors.primary.copy(alpha = 0.82f)
                                else -> colors.primary
                            }
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(w * (c + g), d * (c + g)),
                                size = Size(c, c),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                            if (day == today) {
                                drawRoundRect(
                                    color = colors.onSurface,
                                    topLeft = Offset(w * (c + g), d * (c + g)),
                                    size = Size(c, c),
                                    cornerRadius = CornerRadius(3.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = labelWidth),
            ) {
                Text("Less", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp), color = colors.onSurfaceVariant)
                listOf(
                    colors.surfaceContainerHighest,
                    colors.primary.copy(alpha = 0.35f),
                    colors.primary.copy(alpha = 0.6f),
                    colors.primary.copy(alpha = 0.82f),
                    colors.primary,
                ).forEach {
                    Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(it))
                }
                Text("More", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp), color = colors.onSurfaceVariant)
            }
        }
    }
}

/** A small figure with a label, for the consistency and records summaries. */
@Composable
fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (highlight) colors.primaryContainer else colors.surfaceContainer)
            .padding(horizontal = CruxTheme.space.s3, vertical = CruxTheme.space.s2),
    ) {
        Text(
            value,
            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, fontFeatureSettings = "tnum"),
            color = if (highlight) colors.onPrimaryContainer else colors.onSurface,
        )
        Text(
            label.uppercase(),
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp),
            color = if (highlight) colors.onPrimaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** "+20 kg × 5", "45 s", "12 reps": a result in the terms its exercise is measured in. */
fun ExerciseRecord.describe(metric: MetricType, imperial: Boolean = false): String {
    val load = loadKg?.let { com.hardtekpt.crux.data.model.signedLoad(it, imperial).takeIf { s -> !s.startsWith("0 ") } }
    val time = seconds?.let { formatDuration(it) }
    val count = reps?.let { "$it ${if (metric.usesIntervals) "repeats" else "reps"}" }
    return when {
        metric.usesLoad -> listOfNotNull(load ?: "bodyweight", count?.let { "× $reps" }, time?.takeIf { metric.usesTime }).joinToString(" ")
        metric.usesTime -> time ?: "–"
        else -> count ?: "–"
    }
}

/** One exercise's PR: the result, when it was set, and how many results it beat. */
@Composable
fun RecordRow(best: ExerciseBest, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = CruxTheme.space.s4, vertical = CruxTheme.space.s3)
            .testTag("record_row"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(best.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${best.best.date.shortLabel()} · ${best.results} ${if (best.results == 1) "result" else "results"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            best.best.describe(best.exercise.metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL),
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            color = colors.secondary,
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** A note as a paper-like card: its first line as the title, the rest clipped below. */
@Composable
fun NoteCard(note: Note, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(CruxTheme.space.s4)
            .testTag("note_card"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(note.created.shortLabel().uppercase(), style = Mono11, color = colors.onSurfaceVariant)
            note.tag?.let { tag ->
                Text(
                    tag.uppercase(),
                    style = Mono11,
                    color = colors.primary,
                    modifier = Modifier
                        .padding(start = CruxTheme.space.s2)
                        .border(CruxTheme.size.borderHairline, colors.primary.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("note_tag"),
                )
            }
            Spacer(Modifier.weight(1f))
            if (note.pinned) Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = colors.primary, modifier = Modifier.height(16.dp))
        }
        Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (note.body.isNotBlank()) {
            Text(note.body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row that leads to settings, showing what's set right now. */
@Composable
fun PreferenceRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CruxTheme.space.s3),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** Unused width helper kept private to this file's grid maths. */
private operator fun Dp.div(other: Dp): Float = this.value / other.value
