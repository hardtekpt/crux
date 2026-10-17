package com.hardtekpt.crux.ui.train

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.DayPlan
import com.hardtekpt.crux.data.ScheduleSlot
import com.hardtekpt.crux.data.local.ScheduleKind
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxDayNumber
import com.hardtekpt.crux.ui.theme.CruxMicroSmall
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** A day of this week: what's planned on it, and what of that was done. */
data class WeekDay(
    val date: LocalDate,
    val slots: List<ScheduleSlot> = emptyList(),
    /** Plans with a session finished on the day. */
    val donePlans: Set<Long> = emptySet(),
    /** Minutes of each finished plan, for the card. */
    val doneMinutes: Map<Long, Long> = emptyMap(),
    /** Anything climbed on the day. */
    val climbed: Boolean = false,
) {
    val isRest: Boolean get() = slots.isEmpty()
    fun done(slot: ScheduleSlot): Boolean = when (slot.kind) {
        ScheduleKind.PLAN -> slot.planId in donePlans
        ScheduleKind.CLIMB -> climbed
    }
}

private val DayLabel = CruxMicroSmall

/** The Week tab: Monday to Sunday of this week, each day's plans or climbing, or rest. */
internal fun LazyListScope.week(uiState: TrainUiState, onEditDay: (WeekDay) -> Unit) {
    item(key = "week_head") {
        val planned = uiState.week.sumOf { day -> day.slots.size }
        Eyebrow(if (planned == 0) "This week" else "This week · $planned planned", Modifier.testTag("week_eyebrow"))
    }
    if (!uiState.isLoading && uiState.week.all { it.isRest }) {
        item(key = "week_empty") {
            InlineEmptyState(
                icon = Icons.Rounded.CalendarMonth,
                text = "Nothing planned yet. Tap a day to put a plan or a climbing day on it; the week repeats.",
            )
        }
    }
    items(uiState.week, key = { "day_${it.date.dayOfWeek}" }) { day ->
        WeekDayRow(
            day,
            today = day.date == uiState.today,
            onClick = { onEditDay(day) },
            modifier = Modifier.animateItem(placementSpec = com.hardtekpt.crux.ui.components.CruxMotion.medium()),
        )
    }
}

@Composable
private fun WeekDayRow(day: WeekDay, today: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Plan ${day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.UK)}", onClick = onClick)
            .testTag("week_day_${day.date.dayOfWeek.name}"),
    ) {
        // The day's tile, filled in the accent today.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(if (today) colors.primaryContainer else colors.surfaceContainer)
                .padding(vertical = 8.dp),
        ) {
            Text(
                day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.UK).uppercase(),
                style = DayLabel,
                color = if (today) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
            Text(
                day.date.dayOfMonth.toString(),
                style = CruxDayNumber,
                color = if (today) colors.onPrimaryContainer else colors.onSurface,
            )
        }
        Column(Modifier.weight(1f).animateContentSize(com.hardtekpt.crux.ui.components.CruxMotion.medium()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (day.isRest) {
                Text(
                    "Rest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                day.slots.forEach { slot -> SlotCard(slot, day, today) }
            }
        }
    }
}

@Composable
private fun SlotCard(slot: ScheduleSlot, day: WeekDay, today: Boolean) {
    val colors = MaterialTheme.colorScheme
    val done = day.done(slot)
    val climb = slot.kind == ScheduleKind.CLIMB
    // Turning done blends the border to green.
    val border by androidx.compose.animation.animateColorAsState(
        if (done) CruxTheme.colors.success else colors.outlineVariant,
        com.hardtekpt.crux.ui.components.CruxMotion.slow(),
        label = "done",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(if (climb) "week_climb" else "week_plan"),
    ) {
        // A climbing day says so with the climbing icon; amber is kept for bests.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (climb) Icon(Icons.Rounded.Landscape, contentDescription = null, tint = colors.primary, modifier = Modifier.size(CruxTheme.size.iconSm))
            Text(
                if (climb) "Climbing day" else slot.planName.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val minutes = slot.planId?.let { day.doneMinutes[it] }
        val detail = listOfNotNull(
            when {
                done && minutes != null -> "Done · $minutes min"
                done -> "Done"
                today -> "Today"
                else -> null
            },
            slot.placeName.takeIf { climb },
        ).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(
                detail.uppercase(),
                style = CruxTheme.type.code.copy(fontSize = 10.sp),
                color = if (done) CruxTheme.colors.success else colors.onSurfaceVariant,
            )
        }
    }
}

/** Picks what a day holds: any of the plans, climbing (at a place, if wanted), or rest. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DaySheet(day: WeekDay, plans: List<WorkoutTemplate>, places: List<PlaceSummary>, onSave: (DayPlan) -> Unit, onDismiss: () -> Unit) {
    val space = CruxTheme.space
    var picked by remember { mutableStateOf(day.slots.mapNotNull { it.planId }.toSet()) }
    var climbing by remember { mutableStateOf(day.slots.any { it.kind == ScheduleKind.CLIMB }) }
    var placeId by remember { mutableStateOf(day.slots.firstOrNull { it.kind == ScheduleKind.CLIMB }?.placeId) }
    val name = day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.UK)
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("day_sheet"),
        title = "Every $name",
        confirm = SheetButton("Save $name", {
            // Plans keep the order they have under Plans.
            onSave(DayPlan(plans.map { it.id }.filter { it in picked }, climbing, placeId.takeIf { climbing }))
        }, tag = "day_save"),
    ) {
        Eyebrow("Plans · pick any", Modifier.padding(top = space.s2))
        if (plans.isEmpty()) {
            Text("No plans yet. Make one under Plans.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        plans.forEach { plan ->
            val on = plan.id in picked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Checkbox) { picked = if (on) picked - plan.id else picked + plan.id }
                    .padding(end = space.s3)
                    .testTag("day_plan_${plan.name}"),
            ) {
                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("~${plan.estimatedMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Eyebrow("Climbing", Modifier.padding(top = space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
            CruxFilterChip("Climbing day", climbing, { climbing = !climbing }, Modifier.testTag("day_climbing"))
        }
        if (climbing && places.isNotEmpty()) {
            Text("Where · optional", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                places.forEach { summary ->
                    val place = summary.place
                    CruxFilterChip(place.name, placeId == place.id, {
                        placeId = if (placeId ==
                            place.id
                        ) {
                            null
                        } else {
                            place.id
                        }
                    }, Modifier.testTag("day_place_${place.name}"))
                }
            }
        }
        val rest = picked.isEmpty() && !climbing
        Text(
            if (rest) "Nothing picked: a rest day." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = space.s1),
        )
    }
}

/** A week's days as dots, for Home: a filled dot for a plan, a ring for a climbing day, green once done. */
@Composable
fun WeekStrip(week: List<WeekDay>, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().testTag("week_strip"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { day ->
            val isToday = day.date == today
            val dot = when {
                day.slots.isNotEmpty() && day.slots.all { day.done(it) } -> CruxTheme.colors.success
                day.slots.any { it.kind == ScheduleKind.PLAN } -> colors.primary
                day.slots.any { it.kind == ScheduleKind.CLIMB } -> colors.primary
                else -> Color.Transparent
            }
            val done = day.slots.isNotEmpty() && day.slots.all { day.done(it) }
            val ring = day.slots.none { it.kind == ScheduleKind.PLAN } && day.slots.any { it.kind == ScheduleKind.CLIMB } && !done
            // Read as one: "Tuesday 14, today, plan, done".
            val spoken = listOfNotNull(
                day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.UK) + " " + day.date.dayOfMonth,
                "today".takeIf { isToday },
                when {
                    day.slots.any { it.kind == ScheduleKind.PLAN } && day.slots.any { it.kind == ScheduleKind.CLIMB } -> "plan and climbing"
                    day.slots.any { it.kind == ScheduleKind.PLAN } -> "plan"
                    day.slots.any { it.kind == ScheduleKind.CLIMB } -> "climbing day"
                    else -> "rest day"
                },
                "done".takeIf { done },
            ).joinToString(", ")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = spoken }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isToday) colors.primaryContainer else Color.Transparent)
                    .padding(vertical = 5.dp),
            ) {
                Text(
                    day.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.UK),
                    style = DayLabel,
                    color = if (isToday) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
                Text(
                    day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isToday) colors.onPrimaryContainer else colors.onSurface,
                )
                // Done is a tick, not only a green dot, so it doesn't rest on colour alone.
                Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
                    if (done) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Rounded.Check,
                            contentDescription = null,
                            tint = dot,
                            modifier = Modifier.size(10.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .then(if (ring) Modifier.border(1.5.dp, dot, RoundedCornerShape(3.dp)) else Modifier.background(dot)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * This week, Monday to Sunday, from the repeating schedule: each day's slots, the plans whose
 * sessions were finished that day, and whether anything was climbed.
 */
fun buildWeek(
    today: LocalDate,
    slots: List<ScheduleSlot>,
    sessions: List<com.hardtekpt.crux.data.Session>,
    climbDays: Set<LocalDate>,
    zone: java.time.ZoneId,
): List<WeekDay> {
    val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val byDay = slots.groupBy { it.day }
    val finished = sessions.filter { it.templateId != null && it.endedAtMillis != null }
        .groupBy { java.time.Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() }
    return (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        val done = finished[date].orEmpty()
        WeekDay(
            date = date,
            slots = byDay[date.dayOfWeek].orEmpty(),
            donePlans = done.mapNotNull { it.templateId }.toSet(),
            doneMinutes = done.associate { it.templateId!! to ((it.endedAtMillis!! - it.startedAtMillis) / 60_000) },
            climbed = date in climbDays,
        )
    }
}
