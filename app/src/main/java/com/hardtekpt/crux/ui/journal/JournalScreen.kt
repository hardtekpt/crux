package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.Note
import com.hardtekpt.crux.data.NoteRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.VideoThumbnail
import com.hardtekpt.crux.ui.components.input.argb
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.you.describe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import javax.inject.Inject

data class JournalUiState(
    val isLoading: Boolean = true,
    /** Everything logged, before any filter. */
    val all: List<TimelineDay> = emptyList(),
    /** What the query lets through. */
    val days: List<TimelineDay> = emptyList(),
    val query: JournalQuery = JournalQuery(),
    /** Per type pill, how many entries it would show with the other filters as they are. */
    val counts: Map<JournalFilter, Int> = emptyMap(),
    /** Choices for the filter sheet, from what has been logged. */
    val places: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Days with anything logged since Monday, and climbs among them. */
    val weekDays: Int = 0,
    val weekClimbs: Int = 0,
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    recordRepository: RecordRepository,
    noteRepository: NoteRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val query = MutableStateFlow(JournalQuery())

    val uiState: StateFlow<JournalUiState> = combine(
        climbRepository.observeClimbs(),
        recordRepository.observeResults(),
        noteRepository.observeNotes(),
        query,
    ) { climbs, results, notes, q ->
        val all = buildTimeline(climbs, results, notes)
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val week = all.filter { !it.date.isBefore(monday) }
        JournalUiState(
            today = today,
            weekDays = week.size,
            weekClimbs = week.sumOf { day -> day.entries.filterIsInstance<TimelineEntry.Climbs>().sumOf { it.day.climbs.size } },
            isLoading = false,
            all = all,
            days = all.matching(q, today),
            query = q,
            counts = JournalFilter.entries.associateWith { all.matching(q.copy(kind = it), today).count() },
            places = climbs.mapNotNull { it.place }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
            tags = notes.mapNotNull { it.tag }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun update(change: (JournalQuery) -> JournalQuery) = query.update(change)
}

/** Where the journal's taps lead. */
data class JournalActions(
    val openClimb: (Long) -> Unit = {},
    val openNote: (Long) -> Unit = {},
    val openRecords: (Long) -> Unit = {},
)

@Composable
fun JournalScreen(
    actions: JournalActions,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JournalContent(uiState = uiState, onQuery = viewModel::update, actions = actions)
}

private val GUTTER = 48.dp
private val MonoLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)

/**
 * The journal as one timeline, newest first. A header holds search, the type pills and a
 * Filters button (dates, result, places, tags); chips under it show what's applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalContent(
    uiState: JournalUiState,
    modifier: Modifier = Modifier,
    onQuery: ((JournalQuery) -> JournalQuery) -> Unit = {},
    actions: JournalActions = JournalActions(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Journal"),
    ) {
        CruxTopAppBar(title = "Journal", scrollBehavior = scrollBehavior)
        if (!uiState.isLoading && uiState.all.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                headline = "Nothing logged yet",
                sentence = "Tap Log to add a climb or a note; they line up here by day.",
            )
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s2, bottom = space.s4 + LocalNavBarClearance.current),
            modifier = Modifier.testTag("journal_list"),
        ) {
            // The header is today's day on the timeline: today's entries follow it directly.
            val todayDay = uiState.days.firstOrNull { it.date == uiState.today }
            item(key = "header") { JournalHeader(uiState, todayDay, onQuery, onOpenFilters = { showFilters = true }) }
            item(key = "header_link") { RailLink(Modifier.height(space.s4)) }
            if (uiState.days.isEmpty() && !uiState.isLoading) {
                item(key = "empty") {
                    val query = uiState.query
                    InlineEmptyState(
                        icon = Icons.Rounded.Search,
                        text = if (query.kind == JournalFilter.Training && query.activeFilters == 0 && query.search.isBlank()) {
                            "No training yet. Results you log on exercises show here, and workout sessions will too once the session logger arrives."
                        } else {
                            "Nothing matches. Try another word or clear a filter."
                        },
                    )
                }
            }
            uiState.days.forEachIndexed { index, day ->
                if (day != todayDay) item(key = "day_${day.date}") { DayHeader(day, first = index == 0) }
                items(day.entries, key = { it.key }) { entry ->
                    when (entry) {
                        is TimelineEntry.Climbs -> ClimbsEntry(entry.day, actions.openClimb)
                        is TimelineEntry.Training -> TrainingEntry(entry, actions.openRecords)
                        is TimelineEntry.NoteEntry -> NoteEntry(entry.note) { actions.openNote(entry.note.id) }
                    }
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(uiState, onQuery, onDismiss = { showFilters = false })
    }
}

/**
 * The journal's header, a panel that is also today's spot on the timeline: today's date
 * tile sits on the timeline's line, with this week's tally, then search and Filters, the
 * type pills, and any applied filters.
 */
@Composable
private fun JournalHeader(
    uiState: JournalUiState,
    todayDay: TimelineDay?,
    onQuery: ((JournalQuery) -> JournalQuery) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val space = CruxTheme.space
    val query = uiState.query
    val focus = LocalFocusManager.current
    val today = uiState.today
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(24.dp))
            .padding(top = space.s4, bottom = space.s4)
            .testTag("journal_header"),
        verticalArrangement = Arrangement.spacedBy(space.s4),
    ) {
        // Today, on the timeline's line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(GUTTER), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("journal_today"),
                ) {
                    Text(today.dayOfMonth.toString(), style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp), color = colors.onPrimaryContainer)
                    Text(today.month.getDisplayName(DateTextStyle.SHORT, Locale.UK).take(3).uppercase(), style = MonoLabel.copy(fontSize = 9.sp), color = colors.onPrimaryContainer)
                }
            }
            Column(Modifier.padding(start = space.s2, end = space.s4).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Today · ${today.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.UK)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                // Today's and the week's tallies count everything, whatever the filters show.
                val todayAll = uiState.all.firstOrNull { it.date == today }
                Text(
                    todayAll?.let { daySummary(it) } ?: "Nothing logged yet today",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("journal_today_summary"),
                )
                Text(
                    "This week: ${uiState.weekDays} ${if (uiState.weekDays == 1) "day" else "days"}, ${uiState.weekClimbs} ${if (uiState.weekClimbs == 1) "climb" else "climbs"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.padding(horizontal = space.s4),
        ) {
            OutlinedTextField(
                value = query.search,
                onValueChange = { text -> onQuery { it.copy(search = text.take(60)) } },
                placeholder = { Text("Search climbs, places, notes", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant) },
                trailingIcon = if (query.search.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                onQuery { it.copy(search = "") }
                                focus.clearFocus()
                            },
                            modifier = Modifier.testTag("journal_search_clear"),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = colors.surfaceContainerHigh,
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("journal_search"),
            )
            // Filters, with a badge counting what's set in the sheet.
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (query.activeFilters > 0) colors.primaryContainer else colors.surfaceContainerHigh)
                        .then(if (query.activeFilters > 0) Modifier.border(CruxTheme.size.borderEmphasis, colors.primary, CircleShape) else Modifier)
                        .clickable(onClick = onOpenFilters)
                        .testTag("journal_open_filters"),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Filters", tint = if (query.activeFilters > 0) colors.onPrimaryContainer else colors.onSurface)
                }
                if (query.activeFilters > 0) {
                    Text(
                        query.activeFilters.toString(),
                        style = MonoLabel.copy(fontSize = 10.sp),
                        color = colors.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = space.s4)) { TypePills(uiState, onSelect = { kind -> onQuery { it.copy(kind = kind) } }) }
        if (query.activeFilters > 0) {
            Box(Modifier.padding(start = space.s4)) { AppliedFilters(query, onQuery) }
        }
    }
}

/** The timeline's faint line on its own, linking the header to the first day. */
@Composable
private fun RailLink(modifier: Modifier = Modifier) {
    Box(modifier.width(GUTTER), contentAlignment = Alignment.Center) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
    }
}

/** A day's tally: climbs and sends, results, notes. */
private fun daySummary(day: TimelineDay): String {
    val climbs = day.entries.filterIsInstance<TimelineEntry.Climbs>().sumOf { it.day.climbs.size }
    val sends = day.entries.filterIsInstance<TimelineEntry.Climbs>().sumOf { e -> e.day.climbs.count { it.style.isSend } }
    val results = day.entries.filterIsInstance<TimelineEntry.Training>().sumOf { it.results.size }
    val notes = day.entries.count { it is TimelineEntry.NoteEntry }
    return listOfNotNull(
        climbs.takeIf { it > 0 }?.let { "$it ${if (it == 1) "climb" else "climbs"} · $sends sent" },
        results.takeIf { it > 0 }?.let { "$it ${if (it == 1) "result" else "results"}" },
        notes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "note" else "notes"}" },
    ).joinToString(" · ")
}

/** Four equal pills with live counts. */
@Composable
private fun TypePills(uiState: JournalUiState, onSelect: (JournalFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("journal_filters")) {
        JournalFilter.entries.forEach { option ->
            val selected = option == uiState.query.kind
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .border(
                        if (selected) CruxTheme.size.borderEmphasis else CruxTheme.size.borderHairline,
                        if (selected) colors.primary else colors.outline,
                        CircleShape,
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 10.dp)
                    .testTag("journal_filter_${option.name}"),
            ) {
                Text(option.label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurface, maxLines = 1)
                Text((uiState.counts[option] ?: 0).toString(), style = MonoLabel, color = if (selected) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

/** What the sheet has set, each as a chip that removes itself, then Clear all. */
@Composable
private fun AppliedFilters(query: JournalQuery, onQuery: ((JournalQuery) -> JournalQuery) -> Unit) {
    if (query.activeFilters == 0) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("journal_applied"),
    ) {
        if (query.period != JournalPeriod.AllTime) AppliedChip(query.period.label) { onQuery { it.copy(period = JournalPeriod.AllTime) } }
        if (query.result != JournalResult.Any) AppliedChip(query.result.label) { onQuery { it.copy(result = JournalResult.Any) } }
        query.places.forEach { place -> AppliedChip(place) { onQuery { it.copy(places = it.places - place) } } }
        query.tags.forEach { tag -> AppliedChip("#$tag") { onQuery { it.copy(tags = it.tags - tag) } } }
        Text(
            "Clear all",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onQuery { JournalQuery(kind = it.kind, search = it.search) } }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("journal_clear_filters"),
        )
    }
}

@Composable
private fun AppliedChip(label: String, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onRemove)
            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            .testTag("applied_$label"),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Icon(Icons.Rounded.Close, contentDescription = "Remove $label", tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp).size(14.dp))
    }
}

/** When, result, places and tags; everything combines, and the list updates as you pick. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(uiState: JournalUiState, onQuery: ((JournalQuery) -> JournalQuery) -> Unit, onDismiss: () -> Unit) {
    val query = uiState.query
    val space = CruxTheme.space
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("journal_filter_sheet"),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4)
                .padding(bottom = space.s4)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)
            Eyebrow("When", Modifier.padding(top = space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                JournalPeriod.entries.forEach { period ->
                    CruxFilterChip(period.label, query.period == period, { onQuery { it.copy(period = period) } }, Modifier.testTag("period_${period.name}"))
                }
            }
            Eyebrow("Climbs", Modifier.padding(top = space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                JournalResult.entries.forEach { result ->
                    CruxFilterChip(result.label, query.result == result, { onQuery { it.copy(result = result) } }, Modifier.testTag("result_${result.name}"))
                }
            }
            if (uiState.places.isNotEmpty()) {
                Eyebrow("Places", Modifier.padding(top = space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    uiState.places.forEach { place ->
                        val on = place in query.places
                        CruxFilterChip(place, on, { onQuery { it.copy(places = if (on) it.places - place else it.places + place) } }, Modifier.testTag("place_filter_$place"))
                    }
                }
            }
            if (uiState.tags.isNotEmpty()) {
                Eyebrow("Note tags", Modifier.padding(top = space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    uiState.tags.forEach { tag ->
                        val on = tag in query.tags
                        CruxFilterChip(tag, on, { onQuery { it.copy(tags = if (on) it.tags - tag else it.tags + tag) } }, Modifier.testTag("tag_filter_$tag"))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = space.s3)) {
                TextButton(onClick = { onQuery { JournalQuery(kind = it.kind, search = it.search) } }, enabled = query.activeFilters > 0) { Text("Clear all") }
                Box(Modifier.weight(1f))
                val shown = uiState.days.count()
                CruxButton(
                    text = if (shown == 1) "Show 1 entry" else "Show $shown entries",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("journal_apply_filters"),
                )
            }
        }
    }
}

/** A row in the timeline: a small dot in the gutter, the entry beside it, on a faint line. */
@Composable
private fun TimelineRow(dot: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(GUTTER).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outlineVariant.copy(alpha = 0.6f)))
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
        }
        Box(Modifier.weight(1f).padding(bottom = CruxTheme.space.s3)) { content() }
    }
}

/** The day: a small date tile, the weekday and a one-line tally. */
@Composable
private fun DayHeader(day: TimelineDay, first: Boolean) {
    val colors = MaterialTheme.colorScheme
    val summary = daySummary(day)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("journal_day"),
    ) {
        Box(Modifier.width(GUTTER).fillMaxHeight(), contentAlignment = Alignment.Center) {
            RailLink(Modifier.fillMaxHeight())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainer)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(day.date.dayOfMonth.toString(), style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp))
                Text(day.date.month.getDisplayName(DateTextStyle.SHORT, Locale.UK).take(3).uppercase(), style = MonoLabel.copy(fontSize = 9.sp), color = colors.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(start = CruxTheme.space.s2, top = CruxTheme.space.s4, bottom = CruxTheme.space.s2)) {
            Text(day.date.dayLabel(), style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Climbs at a place: the place on top, then each climb with its tape colour. */
@Composable
private fun ClimbsEntry(day: JournalDay, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    TimelineRow(dot = colors.primary) {
        Column {
            Text(
                listOfNotNull(day.place, day.venue.label).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            day.climbs.forEach { climb -> TapeRow(climb, onClick = { onOpen(climb.id) }) }
        }
    }
}

/**
 * One climb: a short tape mark (the gym's colour on local scales, else green for a send and
 * grey for a go), the grade, the name, and how it went.
 */
@Composable
private fun TapeRow(climb: Climb, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sent = climb.style.isSend
    val tape = climb.gradeColour?.let { argb(it) } ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("journal_climb"),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tape),
        )
        Text(
            climb.grade,
            style = CruxTheme.type.grade,
            color = if (sent) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(climb.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val outcome = buildList {
                add(climb.style.label)
                if (!climb.style.singleAttempt) add("${climb.attempts} ${if (climb.attempts == 1) "go" else "goes"}")
                climb.effort?.let { add("felt $it/10") }
                climb.notes?.let { add(it) }
            }.joinToString(" · ")
            Text(
                outcome,
                style = MaterialTheme.typography.bodySmall,
                color = if (sent) CruxTheme.colors.success else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            climb.imagePath != null -> ImageThumbnail(climb.imagePath, "Photo", onClick = onClick, size = 36.dp)
            climb.videoPath != null -> VideoThumbnail(climb.videoPath, "Video", onClick = onClick, size = 36.dp)
        }
    }
}

@Composable
private fun TrainingEntry(entry: TimelineEntry.Training, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    TimelineRow(dot = colors.tertiary) {
        Column {
            Text("Training", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            entry.results.forEach { result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(result.exercise.id) }
                        .padding(vertical = 6.dp)
                        .testTag("journal_training"),
                ) {
                    Text(result.exercise.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (result.isBest) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = "Personal record", tint = colors.secondary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        result.record.describe(result.exercise.metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL),
                        style = CruxTheme.type.gradeSmall,
                        color = if (result.isBest) colors.secondary else colors.onSurface,
                    )
                }
            }
        }
    }
}

/** A note: its tag, first line and a line of the rest, on a quiet panel. */
@Composable
private fun NoteEntry(note: Note, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TimelineRow(dot = colors.onSurfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("journal_note"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            note.tag?.let { Text(it.uppercase(), style = MonoLabel, color = colors.primary) }
            Text(note.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.body.isNotBlank()) {
                Text(note.body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
