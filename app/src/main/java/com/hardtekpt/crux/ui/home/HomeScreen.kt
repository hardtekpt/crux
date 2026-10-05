package com.hardtekpt.crux.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.TemplateBlock
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.components.TrendDirection
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.oneDecimal
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.relativeLabel
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.signedOneDecimal
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.weightSummary
import java.time.LocalDate

@Composable
fun HomeScreen(
    onOpenTemplate: (Long) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenYou: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onOpenTemplate = onOpenTemplate,
        onOpenJournal = onOpenJournal,
        onOpenProgress = onOpenProgress,
        onOpenYou = onOpenYou,
    )
}

/** The dashboard: five fixed widgets. Editing and the widget catalogue come in phase 4. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onOpenTemplate: (Long) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenYou: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Home"),
    ) {
        CruxTopAppBar(title = "Crux", scrollBehavior = scrollBehavior)
        LazyColumn(
            modifier = Modifier.testTag("home_list"),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s16 + space.s12),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item { Eyebrow(uiState.today.dayLabel()) }
            item { TodaysPlanWidget(uiState.todaysPlan, onOpenTemplate) }

            item { Eyebrow("This week", Modifier.padding(top = space.s3)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    StatTile(
                        label = "Climbs",
                        value = uiState.figure(uiState.week.climbs),
                        delta = "${uiState.week.sends} sent",
                        direction = if (uiState.week.sends > 0) TrendDirection.Wanted else TrendDirection.Neutral,
                        modifier = Modifier.weight(1f),
                        valueModifier = Modifier.testTag("week_climbs"),
                    )
                    StatTile(
                        label = "Days on the wall",
                        value = uiState.figure(uiState.week.daysClimbed),
                        delta = "since Monday",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { Eyebrow("Bests and body", Modifier.padding(top = space.s3)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    LatestBestTile(uiState.latestBest, Modifier.weight(1f).clickable(onClick = onOpenProgress))
                    BodyweightTile(uiState, Modifier.weight(1f).clickable(onClick = onOpenYou))
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow("Recent climbs", Modifier.weight(1f))
                    if (uiState.recentClimbs.isNotEmpty()) {
                        CruxButton(
                            text = "Journal",
                            onClick = onOpenJournal,
                            variant = CruxButtonVariant.Text,
                            size = CruxButtonSize.Small,
                        )
                    }
                }
            }
            if (!uiState.isLoading && uiState.recentClimbs.isEmpty()) {
                item {
                    InlineEmptyState(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        text = "No climbs logged yet. Tap Log, then Log climb, to start the journal.",
                    )
                }
            }
            items(uiState.recentClimbs, key = { it.id }) { climb ->
                CruxListRow(
                    title = climb.displayName(),
                    supporting = listOfNotNull(climb.outcomeLine(), climb.place, climb.date.relativeLabel(uiState.today))
                        .joinToString(" · "),
                    leading = { GradeBadge(climb.grade, climb.gradeState) },
                    modifier = Modifier.testTag("recent_climb"),
                )
            }
        }
    }
}

@Composable
private fun TodaysPlanWidget(plan: WorkoutTemplate?, onOpenTemplate: (Long) -> Unit) {
    CruxCard(modifier = Modifier.fillMaxWidth()) {
        Text("Today's plan".uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (plan == null) {
            Text("No workout planned", style = MaterialTheme.typography.titleMedium)
            return@CruxCard
        }
        Text(
            plan.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = CruxTheme.space.s1),
        )
        Text(
            plan.blocks.joinToString(" · ") { it.name },
            style = CruxTheme.type.code,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "About ${plan.estimatedMinutes} min · ${plan.exerciseCount} exercises",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = CruxTheme.space.s3),
        ) {
            // Disabled until the session logger lands in phase 2.
            CruxButton(
                text = "Start session",
                onClick = {},
                enabled = false,
                icon = Icons.Rounded.PlayArrow,
            )
            CruxButton(
                text = "View plan",
                onClick = { onOpenTemplate(plan.id) },
                variant = CruxButtonVariant.Outlined,
                modifier = Modifier.testTag("view_plan"),
            )
        }
    }
}

@Composable
private fun LatestBestTile(best: PersonalBest?, modifier: Modifier) {
    if (best == null) {
        StatTile(label = "Latest best", value = "–", delta = "send something", modifier = modifier)
    } else {
        StatTile(
            label = "Latest best",
            value = best.grade,
            unit = best.style.label.lowercase(),
            isPersonalBest = true,
            modifier = modifier,
            valueModifier = Modifier.testTag("latest_best"),
        )
    }
}

@Composable
private fun BodyweightTile(uiState: HomeUiState, modifier: Modifier) {
    val weight = uiState.weight
    if (weight == null) {
        StatTile(label = "Bodyweight", value = "–", delta = "log a weigh-in", modifier = modifier)
    } else {
        StatTile(
            label = "Bodyweight",
            value = weight.latest.value.oneDecimal(),
            unit = "kg",
            delta = weight.change?.let { "${it.signedOneDecimal()} kg · ${weight.window}" }
                ?: "weighed ${weight.latest.date.shortLabel()}",
            modifier = modifier,
            valueModifier = Modifier.testTag("bodyweight"),
        )
    }
}

private fun HomeUiState.figure(value: Int) = if (isLoading) "–" else value.toString()

@Preview(showBackground = true, backgroundColor = 0xFF0F1413, heightDp = 1100)
@Composable
private fun HomeContentPreview() {
    val today = LocalDate.of(2026, 10, 5)
    CruxTheme {
        HomeContent(
            uiState = HomeUiState(
                isLoading = false,
                today = today,
                todaysPlan = WorkoutTemplate(1, "Max hangs + limit bouldering", "", 75, listOf(TemplateBlock("Warm-up", emptyList()), TemplateBlock("Max hangs", emptyList()))),
                week = WeekSummary(climbs = 9, sends = 6, daysClimbed = 2),
                latestBest = PersonalBest(Discipline.BOULDER, AscentStyle.FLASH, GradeScale.FONT, 9, "Blue crimps", "Block Lab", today),
                weight = listOf(Measurement(1, 72.4, today), Measurement(2, 73.4, today.minusDays(35))).weightSummary(),
                recentClimbs = listOf(
                    Climb(1, Discipline.BOULDER, GradeScale.FONT, 9, AscentStyle.FLASH, 1, Venue.GYM, today, "Purple sloper", "Block Lab", null),
                ),
            ),
        )
    }
}
