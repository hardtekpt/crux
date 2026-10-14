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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.hardtekpt.crux.ui.components.AnimatedCount
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxMotion
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.VideoThumbnail
import com.hardtekpt.crux.ui.components.input.argb
import com.hardtekpt.crux.ui.components.revealWhen
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.navigation.swipeBetweenTabs
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.you.describe
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    recordRepository: RecordRepository,
    noteRepository: NoteRepository,
    sessionRepository: com.hardtekpt.crux.data.SessionRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val query = MutableStateFlow(JournalQuery())

    val uiState: StateFlow<JournalUiState> = combine(
        climbRepository.observeClimbs(),
        recordRepository.observeResults(),
        noteRepository.observeNotes(),
        sessionRepository.observeFinished(),
        query,
    ) { climbs, results, notes, sessions, q ->
        val all = buildTimeline(climbs, results, notes, sessions, clock.zone)
        JournalUiState(
            today = today,
            isLoading = false,
            all = all,
            days = all.matching(q, today),
            query = q,
            counts = JournalFilter.entries.associateWith { all.matching(q.copy(kind = it), today).count() },
            places = climbs.mapNotNull { it.place }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
            tags = notes.mapNotNull { it.tag }.distinct().sorted(),
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun update(change: (JournalQuery) -> JournalQuery) = query.update(change)
}

/** Where the journal's taps lead. */
data class JournalActions(
    val openClimb: (Long) -> Unit = {},
    val openNote: (Long) -> Unit = {},
    val openRecords: (Long) -> Unit = {},
    val openSession: (Long) -> Unit = {},
)

@Composable
fun JournalScreen(actions: JournalActions, viewModel: JournalViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JournalContent(uiState = uiState, onQuery = viewModel::update, actions = actions)
}

private val GUTTER = 48.dp
private val MonoLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)

/**
 * The journal as one timeline, newest first. The top bar holds search and a Filters button
 * (dates, result, places, tags); tabs pick the type, and chips under them show what's applied.
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
    var searching by rememberSaveable { mutableStateOf(uiState.query.search.isNotEmpty()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // Swipe sideways to move between All, Climbs, Training and Notes.
            .swipeBetweenTabs { direction ->
                JournalFilter.entries.getOrNull(uiState.query.kind.ordinal + direction)?.let { kind -> onQuery { it.copy(kind = kind) } }
            }
            .testTag("screen_Journal"),
    ) {
        if (searching) {
            SearchBar(uiState.query.search, onQuery) { searching = false }
        } else {
            CruxTopAppBar(title = "Journal", scrollBehavior = scrollBehavior) {
                IconButton(onClick = { searching = true }, modifier = Modifier.testTag("journal_open_search")) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                }
                FiltersButton(uiState.query.activeFilters) { showFilters = true }
            }
        }
        if (!uiState.isLoading && uiState.all.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                headline = "Nothing logged yet",
                sentence = "Tap Log to add a climb or a note; they line up here by day.",
            )
            return
        }
        TypeTabs(uiState, onSelect = { kind -> onQuery { it.copy(kind = kind) } })
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.query.activeFilters > 0,
            enter = androidx.compose.animation.expandVertically(CruxMotion.medium()) + androidx.compose.animation.fadeIn(CruxMotion.medium()),
            exit = androidx.compose.animation.shrinkVertically(CruxMotion.medium()) + androidx.compose.animation.fadeOut(CruxMotion.fast()),
        ) {
            Box(Modifier.padding(start = space.s4, top = space.s2)) { AppliedFilters(uiState.query, onQuery) }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
            modifier = Modifier.revealWhen(!uiState.isLoading).testTag("journal_list"),
        ) {
            if (uiState.days.isEmpty() && !uiState.isLoading) {
                item(key = "empty") {
                    val query = uiState.query
                    InlineEmptyState(
                        icon = Icons.Rounded.Search,
                        text = if (query.kind == JournalFilter.Training && query.activeFilters == 0 && query.search.isBlank()) {
                            "No training yet. Sessions you finish and results you log on exercises show here."
                        } else {
                            "Nothing matches. Try another word or clear a filter."
                        },
                    )
                }
            }
            uiState.days.forEach { day ->
                item(key = "day_${day.date}", contentType = "day") {
                    Box(Modifier.animateItem(fadeInSpec = CruxMotion.medium(), placementSpec = CruxMotion.medium(), fadeOutSpec = CruxMotion.fast())) {
                        DayHeader(day, today = day.date == uiState.today)
                    }
                }
                items(day.entries, key = { it.key }, contentType = { it::class }) { entry ->
                    // Entries slide into place and fade when a tab, search or filter changes the list.
                    Box(Modifier.animateItem(fadeInSpec = CruxMotion.medium(), placementSpec = CruxMotion.medium(), fadeOutSpec = CruxMotion.fast())) {
                        when (entry) {
                            is TimelineEntry.Climbs -> ClimbsEntry(entry.day, actions.openClimb)
                            is TimelineEntry.Training -> TrainingEntry(entry, actions.openRecords)
                            is TimelineEntry.SessionEntry -> SessionEntryRow(entry.session, actions.openClimb) { actions.openSession(entry.session.id) }
                            is TimelineEntry.NoteEntry -> NoteEntry(entry.note) { actions.openNote(entry.note.id) }
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(uiState, onQuery, onDismiss = { showFilters = false })
    }
}

/** The top bar while searching: the field takes the title's place, Close leaves search. */
@Composable
private fun SearchBar(search: String, onQuery: ((JournalQuery) -> JournalQuery) -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (search.isEmpty()) focus.requestFocus() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(CruxTheme.size.appBarHeight)
            .padding(horizontal = CruxTheme.space.s1),
    ) {
        IconButton(
            onClick = {
                onQuery { it.copy(search = "") }
                onClose()
            },
            modifier = Modifier.testTag("journal_close_search"),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
        }
        BasicTextField(
            value = search,
            onValueChange = { text -> onQuery { it.copy(search = text.take(60)) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (search.isEmpty()) {
                        Text("Search climbs, places, notes", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    }
                    field()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .testTag("journal_search"),
        )
        if (search.isNotEmpty()) {
            IconButton(onClick = { onQuery { it.copy(search = "") } }, modifier = Modifier.testTag("journal_search_clear")) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
            }
        }
    }
}

/** Filters, filled in the accent with a count when anything in the sheet is set. */
@Composable
private fun FiltersButton(active: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        colors = if (active > 0) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors(),
        modifier = Modifier.testTag("journal_open_filters"),
    ) {
        BadgedBox(
            badge = {
                if (active > 0) {
                    Badge(containerColor = colors.primary, contentColor = colors.onPrimary) { Text(active.toString()) }
                }
            },
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Filters")
        }
    }
}

/**
 * Every climb on a day, including those inside its sessions, counted the way the session card
 * shows them: goes on the same problem in a session as one.
 */
private fun TimelineDay.climbs(): List<Climb> = entries.filterIsInstance<TimelineEntry.Climbs>().flatMap { it.day.climbs } +
    entries.filterIsInstance<TimelineEntry.SessionEntry>().flatMap { it.session.climbGroups }

/** A day's tally: climbs and sends, results, notes, sessions. */
private fun daySummary(day: TimelineDay): String {
    val dayClimbs = day.climbs()
    val climbs = dayClimbs.size
    val sends = dayClimbs.count { it.style.isSend }
    val results = day.entries.filterIsInstance<TimelineEntry.Training>().sumOf { it.results.size }
    val notes = day.entries.count { it is TimelineEntry.NoteEntry }
    val sessions = day.entries.count { it is TimelineEntry.SessionEntry }
    return listOfNotNull(
        climbs.takeIf { it > 0 }?.let { "$it ${if (it == 1) "climb" else "climbs"} · $sends sent" },
        results.takeIf { it > 0 }?.let { "$it ${if (it == 1) "result" else "results"}" },
        notes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "note" else "notes"}" },
        sessions.takeIf { it > 0 }?.let { "$it ${if (it == 1) "session" else "sessions"}" },
    ).joinToString(" · ")
}

/** The four types as equal tabs with live counts, the picked one underlined. */
@Composable
private fun TypeTabs(uiState: JournalUiState, onSelect: (JournalFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CruxTheme.space.s2)
                .selectableGroup()
                .testTag("journal_filters"),
        ) {
            JournalFilter.entries.forEach { option ->
                val selected = option == uiState.query.kind
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(option) })
                        .testTag("journal_filter_${option.name}"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                    ) {
                        val label by androidx.compose.animation.animateColorAsState(
                            if (selected) colors.onSurface else colors.onSurfaceVariant,
                            CruxMotion.fast(),
                            label = "tab",
                        )
                        val count by androidx.compose.animation.animateColorAsState(
                            if (selected) colors.primary else colors.onSurfaceVariant,
                            CruxMotion.fast(),
                            label = "tabCount",
                        )
                        Text(option.label, style = MaterialTheme.typography.titleSmall, color = label, maxLines = 1)
                        AnimatedCount((uiState.counts[option] ?: 0).toString(), style = MonoLabel, color = count)
                    }
                    val underline by androidx.compose.animation.animateColorAsState(
                        if (selected) colors.primary else Color.Transparent,
                        CruxMotion.fast(),
                        label = "underline",
                    )
                    val width by androidx.compose.animation.core.animateFloatAsState(
                        if (selected) 0.7f else 0.2f,
                        CruxMotion.medium(),
                        label = "underlineWidth",
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(width)
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(underline),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(CruxTheme.size.borderHairline).background(colors.outlineVariant))
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
                        CruxFilterChip(place, on, {
                            onQuery { it.copy(places = if (on) it.places - place else it.places + place) }
                        }, Modifier.testTag("place_filter_$place"))
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

/** The timeline's faint line down the middle of the gutter, drawn behind a row. */
@Composable
private fun Modifier.railLine(): Modifier {
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    return drawBehind {
        val x = GUTTER.toPx() / 2
        drawLine(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
    }
}

/** A row in the timeline: a small dot in the gutter, the entry beside it, on a faint line. */
@Composable
private fun TimelineRow(dot: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // The line is drawn, not laid out, so the row is measured once while scrolling.
    Row(modifier.fillMaxWidth().railLine()) {
        Box(Modifier.width(GUTTER), contentAlignment = Alignment.TopCenter) {
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

/** The day: a small date tile, the weekday and a one-line tally. Today's tile is in the accent. */
@Composable
private fun DayHeader(day: TimelineDay, today: Boolean) {
    val colors = MaterialTheme.colorScheme
    val summary = daySummary(day)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .railLine()
            .testTag("journal_day"),
    ) {
        Box(Modifier.width(GUTTER), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (today) colors.primaryContainer else colors.surfaceContainer)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .then(if (today) Modifier.testTag("journal_today") else Modifier),
            ) {
                Text(
                    day.date.dayOfMonth.toString(),
                    style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp),
                    color = if (today) colors.onPrimaryContainer else colors.onSurface,
                )
                Text(
                    day.date.month.getDisplayName(DateTextStyle.SHORT, Locale.UK).take(3).uppercase(),
                    style = MonoLabel.copy(fontSize = 9.sp),
                    color = if (today) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.padding(start = CruxTheme.space.s2, top = CruxTheme.space.s4, bottom = CruxTheme.space.s2)) {
            Text(
                if (today) "Today · ${day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.UK)}" else day.date.dayLabel(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One day's entries as the Journal shows them, for pages that show a single day. */
@Composable
internal fun JournalDayEntries(day: TimelineDay, actions: JournalActions) {
    Column {
        day.entries.forEach { entry ->
            when (entry) {
                is TimelineEntry.Climbs -> ClimbsEntry(entry.day, actions.openClimb)
                is TimelineEntry.Training -> TrainingEntry(entry, actions.openRecords)
                is TimelineEntry.SessionEntry -> SessionEntryRow(entry.session, actions.openClimb) { actions.openSession(entry.session.id) }
                is TimelineEntry.NoteEntry -> NoteEntry(entry.note) { actions.openNote(entry.note.id) }
            }
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

/** A finished session: its name, how long, what got done and how it felt. */
@Composable
private fun SessionEntryRow(session: com.hardtekpt.crux.data.Session, onOpenClimb: (Long) -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TimelineRow(dot = colors.tertiary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow)
                .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("journal_session"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    com.hardtekpt.crux.ui.session.durationLabel(session.durationMillis(session.startedAtMillis)),
                    style = MonoLabel,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                listOfNotNull(
                    "${session.setsDone} of ${session.setsPlanned} sets".takeIf { session.items.isNotEmpty() },
                    session.climbGroups.size.takeIf {
                        it > 0
                    }?.let { n -> "$n ${if (n == 1) "climb" else "climbs"} · ${session.climbGroups.count { it.style.isSend }} sent" },
                    session.effort?.let { "felt $it/10" },
                ).joinToString(" · ").ifEmpty { "Nothing logged" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (session.items.isNotEmpty()) {
                // A bar per exercise, filled by how much of it got done.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    session.items.forEach { item ->
                        val done = if (item.target.sets == 0) 0f else (item.done.toFloat() / item.target.sets).coerceAtMost(1f)
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(colors.tertiary))
                        }
                    }
                }
            }
            session.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            // The climbs logged during it, inside the session.
            if (session.climbGroups.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 2.dp))
                session.climbGroups.sortedBy { it.id }.forEach { climb -> TapeRow(climb, onClick = { onOpenClimb(climb.id) }) }
            }
        }
    }
}

@Composable
private fun TrainingEntry(entry: TimelineEntry.Training, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    TimelineRow(dot = colors.tertiary) {
        Column {
            Text(
                "Training",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
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
                    Text(
                        result.exercise.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (result.isBest) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = "Personal record", tint = colors.secondary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        result.record.describe(
                            result.exercise.metric,
                            com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL,
                        ),
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
