package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.NoteRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxCardFill
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.you.ProfileStat
import com.hardtekpt.crux.ui.you.weekStreaks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DaysOnWallUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    /** The month on the calendar, and the day picked on it. */
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    /** Climbs per day on the wall. */
    val climbsByDay: Map<LocalDate, Int> = emptyMap(),
    /** Sends per day, for the month's tally. */
    val sendsByDay: Map<LocalDate, Int> = emptyMap(),
    val thisWeek: Int = 0,
    val thisMonth: Int = 0,
    val thisYear: Int = 0,
    val weekStreak: Int = 0,
    /** The picked day as the Journal shows it; null when nothing was logged that day. */
    val day: TimelineDay? = null,
    /** The first day anything was climbed, so the calendar doesn't page back forever. */
    val firstDay: LocalDate? = null,
)

/** The days you climbed, as figures and a month calendar; a picked day shows its journal. */
@HiltViewModel
class DaysOnWallViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    recordRepository: RecordRepository,
    noteRepository: NoteRepository,
    sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val view = MutableStateFlow(YearMonth.from(today) to today)

    val uiState: StateFlow<DaysOnWallUiState> = combine(
        climbRepository.observeClimbs(),
        recordRepository.observeResults(),
        noteRepository.observeNotes(),
        sessionRepository.observeFinished(),
        view,
    ) { climbs, results, notes, sessions, (month, selected) ->
        val climbsByDay = climbs.groupingBy { it.date }.eachCount()
        val days = climbsByDay.keys
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DaysOnWallUiState(
            isLoading = false,
            today = today,
            month = month,
            selected = selected,
            climbsByDay = climbsByDay,
            sendsByDay = climbs.filter { it.style.isSend }.groupingBy { it.date }.eachCount(),
            thisWeek = days.count { !it.isBefore(monday) && !it.isAfter(today) },
            thisMonth = days.count { YearMonth.from(it) == YearMonth.from(today) },
            thisYear = days.count { it.year == today.year },
            weekStreak = weekStreaks(days, today).first,
            day = buildTimeline(climbs, results, notes, sessions, clock.zone).firstOrNull { it.date == selected },
            firstDay = days.minOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaysOnWallUiState(today = today, month = YearMonth.from(today), selected = today))

    fun pick(day: LocalDate) = view.update { YearMonth.from(day) to day }

    /** Pages the calendar a month back or on, picking that month's last day on the wall. */
    fun page(by: Long) = view.update { (month, selected) ->
        val next = month.plusMonths(by)
        val state = uiState.value
        val last = state.climbsByDay.keys.filter { YearMonth.from(it) == next }.maxOrNull()
        next to (last ?: if (next == YearMonth.from(today)) today else selected)
    }
}

private val WeekdayStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp)
private val DayNumber = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

/**
 * Days on the wall: days this week, month and year and the week streak at the top, then a month
 * calendar with each climbing day filled (deeper for more climbs). Tapping a day shows that
 * day's journal under the calendar.
 */
@Composable
fun DaysOnWallScreen(onBack: () -> Unit, actions: JournalActions, viewModel: DaysOnWallViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DaysOnWallContent(state, onBack, actions, onPick = viewModel::pick, onPage = viewModel::page)
}

@Composable
fun DaysOnWallContent(
    state: DaysOnWallUiState,
    onBack: () -> Unit = {},
    actions: JournalActions = JournalActions(),
    onPick: (LocalDate) -> Unit = {},
    onPage: (Long) -> Unit = {},
) {
    val space = CruxTheme.space
    Column(Modifier.fillMaxSize().testTag("screen_DaysOnWall")) {
        CruxTopAppBar(title = "Days on the wall", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s2, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item(key = "figures") {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min).testTag("days_figures")) {
                    ProfileStat(state.thisWeek.toString(), "Week", Modifier.weight(1f))
                    ProfileStat(state.thisMonth.toString(), "Month", Modifier.weight(1f))
                    ProfileStat(state.thisYear.toString(), "Year", Modifier.weight(1f))
                    ProfileStat("${state.weekStreak} wk", "Streak", Modifier.weight(1f), highlight = state.weekStreak > 0)
                }
            }
            item(key = "calendar") {
                CruxCard(fill = CruxCardFill.Low) {
                    MonthCalendar(state, onPick = onPick, onPage = onPage)
                }
            }
            item(key = "day_header") {
                val climbs = state.climbsByDay[state.selected] ?: 0
                val sends = state.sendsByDay[state.selected] ?: 0
                Eyebrow(
                    listOfNotNull(
                        state.selected.dayLabel(),
                        if (climbs > 0) "$climbs ${if (climbs == 1) "climb" else "climbs"} · $sends sent" else null,
                    ).joinToString(" · "),
                    Modifier.padding(top = space.s2).testTag("days_selected"),
                )
            }
            item(key = "day") {
                val day = state.day
                if (day == null) {
                    InlineEmptyState(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        text = if (state.selected.isAfter(
                                state.today,
                            )
                        ) {
                            "That day hasn't happened yet."
                        } else {
                            "Nothing logged that day. Pick a filled day to see its climbs."
                        },
                    )
                } else {
                    JournalDayEntries(day, actions)
                }
            }
        }
    }
}

/** A month as a grid of days, Monday first, with the month and its tally above. */
@Composable
private fun MonthCalendar(state: DaysOnWallUiState, onPick: (LocalDate) -> Unit, onPage: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val month = state.month
    val days = state.climbsByDay.filterKeys { YearMonth.from(it) == month }
    val canGoBack = state.firstDay?.let { YearMonth.from(it) < month } ?: false
    val canGoOn = month < YearMonth.from(state.today)
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPage(-1) }, enabled = canGoBack, modifier = Modifier.testTag("calendar_previous")) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${month.month.getDisplayName(DateTextStyle.FULL, Locale.UK)} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("calendar_month"),
                )
                val climbs = days.values.sum()
                Text(
                    when {
                        days.isEmpty() -> "No days on the wall"
                        else -> "${days.size} ${if (days.size == 1) "day" else "days"} · $climbs ${if (climbs == 1) "climb" else "climbs"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onPage(1) }, enabled = canGoOn, modifier = Modifier.testTag("calendar_next")) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    style = WeekdayStyle,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Weeks from the Monday on or before the 1st, through the month's last day.
        val first = month.atDay(1)
        val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ((month.atEndOfMonth().toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (w in 0 until weeks) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 0 until 7) {
                        val date = start.plusDays((w * 7 + d).toLong())
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (YearMonth.from(date) == month) {
                                DayCell(
                                    date,
                                    climbs = days[date] ?: 0,
                                    today = date == state.today,
                                    selected = date == state.selected,
                                    future = date.isAfter(state.today),
                                    onPick = onPick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One day: filled when climbed (deeper with more climbs), ringed when picked, outlined today. */
@Composable
private fun DayCell(date: LocalDate, climbs: Int, today: Boolean, selected: Boolean, future: Boolean, onPick: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Days off stay empty, so the climbing days stand out.
    val fill = when {
        climbs == 0 -> androidx.compose.ui.graphics.Color.Transparent
        climbs <= 2 -> colors.primary.copy(alpha = 0.55f)
        climbs <= 5 -> colors.primary.copy(alpha = 0.8f)
        else -> colors.primary
    }
    val ink = when {
        climbs > 0 -> colors.onPrimary
        future -> colors.onSurfaceVariant.copy(alpha = 0.4f)
        else -> colors.onSurfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, colors.onSurface, CircleShape)
                } else if (today) {
                    Modifier.border(1.5.dp, colors.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .padding(if (selected || today) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = !future) { onPick(date) }
            .semantics { contentDescription = "${date.dayLabel()}: ${if (climbs == 0) "no climbs" else "$climbs climbs"}" }
            .testTag("calendar_day_$date"),
    ) {
        Text(date.dayOfMonth.toString(), style = DayNumber, color = ink)
    }
}
