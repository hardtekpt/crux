package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.NoteRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxCardFill
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.you.ProfileStat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class WeekClimbsUiState(
    val isLoading: Boolean = true,
    /** The Monday of the week shown, and of this week. */
    val week: LocalDate = LocalDate.now(),
    val thisWeek: LocalDate = LocalDate.now(),
    val climbs: Int = 0,
    val sent: Int = 0,
    val days: Int = 0,
    /** The hardest send's grade, in the scale of the discipline climbed most. */
    val hardest: String? = null,
    /** The week's days with climbs, newest first, as the Journal shows them. */
    val entries: List<TimelineDay> = emptyList(),
    /** The first week anything was climbed, so the arrows stop there. */
    val firstWeek: LocalDate? = null,
)

private fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** A week's climbs: its figures, and each day's climbs, sessions included. */
@HiltViewModel
class WeekClimbsViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    recordRepository: RecordRepository,
    noteRepository: NoteRepository,
    sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val thisWeek = monday(LocalDate.now(clock))
    private val week = MutableStateFlow(thisWeek)

    val uiState: StateFlow<WeekClimbsUiState> = combine(
        climbRepository.observeClimbs(),
        recordRepository.observeResults(),
        noteRepository.observeNotes(),
        sessionRepository.observeFinished(),
        week,
    ) { climbs, results, notes, sessions, monday ->
        val end = monday.plusDays(6)
        val inWeek = climbs.filter { !it.date.isBefore(monday) && !it.date.isAfter(end) }
        WeekClimbsUiState(
            isLoading = false,
            week = monday,
            thisWeek = thisWeek,
            climbs = inWeek.size,
            sent = inWeek.count { it.style.isSend },
            days = inWeek.map { it.date }.distinct().size,
            hardest = hardestSend(inWeek),
            entries = buildTimeline(climbs, results, notes, sessions, clock.zone)
                .filter { !it.date.isBefore(monday) && !it.date.isAfter(end) }
                .mapNotNull { day ->
                    // Only what was climbed: climbs, and sessions with climbs in them.
                    val kept = day.entries.filter { it is TimelineEntry.Climbs || (it is TimelineEntry.SessionEntry && it.session.climbs.isNotEmpty()) }
                    if (kept.isEmpty()) null else day.copy(entries = kept)
                },
            firstWeek = climbs.minOfOrNull { it.date }?.let(::monday),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekClimbsUiState(week = thisWeek, thisWeek = thisWeek))

    /** Moves a week back or on, never past this week. */
    fun page(by: Long) = week.update { it.plusWeeks(by).coerceAtMost(thisWeek) }
}

/** The hardest send, among the discipline and scale climbed most that week. */
private fun hardestSend(climbs: List<Climb>): String? {
    val sends = climbs.filter { it.style.isSend && !it.gradeScale.isLocal }
    val main = sends.groupingBy { it.discipline to it.gradeScale }.eachCount().maxByOrNull { it.value }?.key ?: return null
    return sends.filter { (it.discipline to it.gradeScale) == main }.maxByOrNull { it.gradeIndex }?.grade
}

private val RANGE = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/**
 * Climbs this week, in full: the week's climbs, sends, days and hardest send, then each day's
 * climbs as the Journal shows them. The arrows go back through earlier weeks.
 */
@Composable
fun WeekClimbsScreen(onBack: () -> Unit, actions: JournalActions, viewModel: WeekClimbsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeekClimbsContent(state, onBack, actions, onPage = viewModel::page)
}

@Composable
fun WeekClimbsContent(state: WeekClimbsUiState, onBack: () -> Unit = {}, actions: JournalActions = JournalActions(), onPage: (Long) -> Unit = {}) {
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    val current = state.week == state.thisWeek
    Column(Modifier.fillMaxSize().testTag("screen_WeekClimbs")) {
        CruxTopAppBar(title = if (current) "Climbs this week" else "Climbs that week", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s2, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item(key = "week") {
                CruxCard(fill = CruxCardFill.Low) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onPage(-1) },
                            enabled = state.firstWeek?.let { it < state.week } ?: false,
                            modifier = Modifier.testTag("week_previous"),
                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous week") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.week.format(RANGE)} – ${state.week.plusDays(6).format(RANGE)}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("week_range"),
                            )
                            Text(
                                when (val weeks = java.time.temporal.ChronoUnit.WEEKS.between(state.week, state.thisWeek)) {
                                    0L -> "This week"
                                    1L -> "Last week"
                                    else -> "$weeks weeks ago"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onPage(1) }, enabled = !current, modifier = Modifier.testTag("week_next")) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next week")
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space.s2),
                        modifier = Modifier.padding(top = space.s3).height(IntrinsicSize.Min).testTag("week_figures"),
                    ) {
                        ProfileStat(state.climbs.toString(), if (state.climbs == 1) "Climb" else "Climbs", Modifier.weight(1f))
                        ProfileStat(state.sent.toString(), "Sent", Modifier.weight(1f))
                        ProfileStat(state.days.toString(), if (state.days == 1) "Day" else "Days", Modifier.weight(1f))
                        ProfileStat(state.hardest ?: "–", "Hardest", Modifier.weight(1f))
                    }
                }
            }
            if (!state.isLoading && state.entries.isEmpty()) {
                item(key = "empty") {
                    InlineEmptyState(
                        icon = Icons.Rounded.Landscape,
                        text = if (current) "No climbs yet this week. Log one and it shows here." else "No climbs that week.",
                    )
                }
            }
            items(state.entries, key = { it.date.toString() }) { day ->
                Column(Modifier.testTag("week_day")) {
                    Eyebrow(day.date.dayLabel(), Modifier.padding(top = space.s2))
                    JournalDayEntries(day, actions)
                }
            }
        }
    }
}
