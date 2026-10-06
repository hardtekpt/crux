package com.hardtekpt.crux.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.data.dashboard.DashboardWidget
import com.hardtekpt.crux.data.dashboard.WidgetSize
import com.hardtekpt.crux.data.dashboard.WidgetType
import com.hardtekpt.crux.ui.charts.BarChart
import com.hardtekpt.crux.ui.charts.ChartCard
import com.hardtekpt.crux.ui.charts.DonutChart
import com.hardtekpt.crux.ui.charts.SeriesPoint
import com.hardtekpt.crux.ui.charts.TimeSeriesChart
import com.hardtekpt.crux.ui.charts.formatTick
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.components.TrendDirection
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.oneDecimal
import com.hardtekpt.crux.ui.weight
import com.hardtekpt.crux.ui.weightChange
import com.hardtekpt.crux.ui.weightUnit
import com.hardtekpt.crux.ui.weightValue
import com.hardtekpt.crux.data.model.Project
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.relativeLabel
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.signedOneDecimal
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.LocalDate

/** Where a widget's taps lead. Ignored while the dashboard is being edited. */
data class WidgetActions(
    val openTemplate: (Long) -> Unit = {},
    val openJournal: () -> Unit = {},
    val openProgress: () -> Unit = {},
    val openYou: () -> Unit = {},
    val openProblem: (Long) -> Unit = {},
)

/** Draws one dashboard widget at its size from the shared home state. */
@Composable
fun DashboardWidgetContent(
    widget: DashboardWidget,
    state: HomeUiState,
    actions: WidgetActions,
    modifier: Modifier = Modifier,
) {
    val large = widget.size == WidgetSize.LARGE
    when (widget.type) {
        WidgetType.TODAYS_PLAN -> TodaysPlanWidget(state, large, actions.openTemplate, modifier)
        WidgetType.WEEK_CLIMBS -> StatTile(
            label = "Climbs this week",
            value = state.figure(state.week.climbs),
            delta = "${state.week.sends} sent",
            direction = if (state.week.sends > 0) TrendDirection.Wanted else TrendDirection.Neutral,
            modifier = modifier,
            valueModifier = Modifier.testTag("week_climbs"),
        )
        WidgetType.DAYS_ON_WALL -> StatTile(
            label = "Days on the wall",
            value = state.figure(state.week.daysClimbed),
            delta = "since Monday",
            modifier = modifier,
        )
        WidgetType.LATEST_BEST -> {
            val best = state.latestBest
            if (best == null) {
                StatTile(label = "Latest best", value = "–", delta = "send something", modifier = modifier)
            } else {
                StatTile(
                    label = "Latest best",
                    value = best.grade,
                    unit = best.style.label.lowercase(),
                    isPersonalBest = true,
                    modifier = modifier.clickable(onClick = actions.openProgress),
                    valueModifier = Modifier.testTag("latest_best"),
                )
            }
        }
        WidgetType.BODYWEIGHT -> {
            val weight = state.weight
            if (weight == null) {
                StatTile(label = "Bodyweight", value = "–", delta = "log a weigh-in", modifier = modifier)
            } else {
                val units = LocalUnits.current
                StatTile(
                    label = "Bodyweight",
                    value = units.weight(weight.latest.value).value,
                    unit = units.weightUnit(),
                    delta = weight.change?.let { "${units.weightChange(it)} · ${weight.window}" }
                        ?: "weighed ${weight.latest.date.shortLabel()}",
                    modifier = modifier.clickable(onClick = actions.openYou),
                    valueModifier = Modifier.testTag("bodyweight"),
                )
            }
        }
        WidgetType.WEIGHT_TREND -> WeightTrendWidget(state, large, modifier)
        WidgetType.WEEKLY_SENDS -> {
            val weeks = if (large) state.charts.weeklySends else state.charts.weeklySends.takeLast(5)
            ChartCard(
                title = "Sends per week",
                trailing = "${formatTick(weeks.sumOf { it.value })} in ${weeks.size} weeks",
                modifier = modifier,
            ) {
                BarChart(bars = weeks, formatValue = ::formatTick, height = if (large) 170.dp else 110.dp)
            }
        }
        WidgetType.SENDS_BY_STYLE -> ChartCard(title = "Sends by style", modifier = modifier) {
            if (state.charts.sendsByStyle.isEmpty()) {
                EmptyWidgetText("Log a send to see how you send.")
            } else {
                DonutChart(
                    slices = state.charts.sendsByStyle,
                    centerValue = formatTick(state.charts.sendsByStyle.sumOf { it.value }),
                    centerLabel = "sends",
                    formatValue = ::formatTick,
                    size = if (large) 130.dp else 96.dp,
                )
            }
        }
        WidgetType.RECENT_CLIMBS -> RecentClimbsWidget(state, large, actions.openJournal, modifier)
        WidgetType.PROJECTS -> ProjectsWidget(state, large, actions.openProblem, actions.openProgress, modifier)
    }
}

@Composable
private fun TodaysPlanWidget(state: HomeUiState, large: Boolean, onOpen: (Long) -> Unit, modifier: Modifier) {
    val plan = state.todaysPlan
    CruxCard(modifier = modifier.fillMaxWidth()) {
        Text(
            "Today's plan".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (plan == null) {
            Text("No plan yet", style = MaterialTheme.typography.titleMedium)
            return@CruxCard
        }
        Text(plan.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = CruxTheme.space.s1))
        Text(
            "About ${plan.estimatedMinutes} min · ${plan.exerciseCount} exercises",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (large) {
            Column(Modifier.padding(top = CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                plan.blocks.flatMap { it.items }.forEach { item ->
                    Row {
                        Text(item.exercise.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(item.prescription, style = CruxTheme.type.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text(
                plan.blocks.joinToString(" · ") { it.name },
                style = CruxTheme.type.code,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = CruxTheme.space.s3),
        ) {
            // Disabled until the session logger lands.
            CruxButton(text = "Start session", onClick = {}, enabled = false, icon = Icons.Rounded.PlayArrow)
            CruxButton(
                text = "View plan",
                onClick = { onOpen(plan.id) },
                variant = CruxButtonVariant.Outlined,
                modifier = Modifier.testTag("view_plan"),
            )
        }
    }
}

@Composable
private fun WeightTrendWidget(state: HomeUiState, large: Boolean, modifier: Modifier) {
    val start = state.today.minusDays(91)
    val points = state.weights.filter { !it.date.isBefore(start) }.sortedBy { it.date }
    val change = if (points.size > 1) points.last().value - points.first().value else null
    val units = LocalUnits.current
    ChartCard(
        title = "Weight trend",
        trailing = change?.let { "${units.weightChange(it)} over 3M" },
        modifier = modifier,
    ) {
        if (points.size < 2) {
            EmptyWidgetText("Log two weigh-ins to see the trend.")
        } else {
            TimeSeriesChart(
                points = points.map { SeriesPoint(it.date.toEpochDay().toDouble(), units.weightValue(it.value)) },
                formatX = { LocalDate.ofEpochDay(it.toLong()).shortLabel() },
                formatY = { it.oneDecimal() },
                unit = " ${units.weightUnit()}",
                height = if (large) 190.dp else 120.dp,
                description = "Bodyweight over the last 3 months",
            )
        }
    }
}

@Composable
private fun RecentClimbsWidget(state: HomeUiState, large: Boolean, onOpenJournal: () -> Unit, modifier: Modifier) {
    val climbs = state.recentClimbs.take(if (large) 6 else 3)
    CruxCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent climbs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (climbs.isNotEmpty()) {
                CruxButton("Journal", onOpenJournal, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
            }
        }
        if (!state.isLoading && climbs.isEmpty()) {
            EmptyWidgetText("No climbs logged yet. Tap Log, then Log climb, to start the journal.")
        }
        Column(modifier = Modifier.padding(top = CruxTheme.space.s1)) {
            climbs.forEachIndexed { index, climb ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                ClimbLine(climb, state.today, onOpenJournal)
            }
        }
    }
}

@Composable
private fun ProjectsWidget(state: HomeUiState, large: Boolean, onOpen: (Long) -> Unit, onOpenProgress: () -> Unit, modifier: Modifier) {
    val projects = state.projects.take(if (large) 6 else 3)
    CruxCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.projects.size > projects.size) {
                CruxButton("All ${state.projects.size}", onOpenProgress, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
            }
        }
        if (!state.isLoading && projects.isEmpty()) {
            EmptyWidgetText("No open projects. Log a go on a saved problem and it stays here until you send it.")
        }
        Column(modifier = Modifier.padding(top = CruxTheme.space.s1)) {
            projects.forEachIndexed { index, project ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                ProjectLine(project, state.today, onOpen)
            }
        }
    }
}

/**
 * A recent climb as a flat line, like the projects above it: tape mark (the gym's colour on
 * local scales, else green for a send and grey for a go), the grade, name and where, and
 * how it went with when on the right.
 */
@Composable
private fun ClimbLine(climb: com.hardtekpt.crux.data.model.Climb, today: LocalDate, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sent = climb.style.isSend
    val tape = climb.gradeColour?.let { com.hardtekpt.crux.ui.components.input.argb(it) } ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp)
            .testTag("recent_climb"),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tape),
        )
        Text(climb.grade, style = CruxTheme.type.grade, color = if (sent) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.width(44.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(climb.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(climb.place, climb.venue.label).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                climb.style.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (sent) CruxTheme.colors.success else colors.onSurfaceVariant,
                maxLines = 1,
            )
            Text(climb.date.relativeLabel(today), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * A project as a flat line inside the widget: its tape (or a grey mark), the grade, name and
 * where it is, and on the right how many goes it has taken so far.
 */
@Composable
private fun ProjectLine(project: Project, today: LocalDate, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stats = project.stats
    val tape = project.problem.tape?.let { CruxTheme.colors.tape[it.coerceIn(CruxTheme.colors.tape.indices)] } ?: colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen(project.problem.id) }
            .padding(vertical = 10.dp)
            .testTag("home_project"),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tape),
        )
        Text(project.problem.grade, style = CruxTheme.type.grade, modifier = Modifier.width(44.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(project.problem.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(project.placeName, project.areaName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${stats.sessions} ${if (stats.sessions == 1) "day" else "days"} on it · last go ${stats.lastGo.relativeLabel(today).lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stats.attempts.toString(), style = CruxTheme.type.grade.copy(fontSize = 18.sp), color = colors.secondary)
            Text(
                if (stats.attempts == 1) "go" else "goes",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyWidgetText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = CruxTheme.space.s3),
    )
}

private fun HomeUiState.figure(value: Int) = if (isLoading) "–" else value.toString()
