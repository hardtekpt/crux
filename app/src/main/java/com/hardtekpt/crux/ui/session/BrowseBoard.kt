package com.hardtekpt.crux.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BoardBrowser
import com.hardtekpt.crux.data.BrowseCandidate
import com.hardtekpt.crux.data.BrowseRun
import com.hardtekpt.crux.data.BrowseStore
import com.hardtekpt.crux.data.ClimbSource
import com.hardtekpt.crux.data.Dataset
import com.hardtekpt.crux.data.DatasetRepository
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.Playlist
import com.hardtekpt.crux.data.PlaylistRepository
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.model.Section
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.ClimbGrade
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.keepEndScrollInSheet
import com.hardtekpt.crux.ui.datasets.BrowseFilterBar
import com.hardtekpt.crux.ui.datasets.BrowseModel
import com.hardtekpt.crux.ui.datasets.BrowseUi
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A board or dataset you can browse, and why it's offered. */
data class BrowseChoice(val dataset: Dataset, val line: String)

/** What the + menu and the browse sheet need before a board is open: the board it would open, and the others. */
data class BrowseBoards(
    /** The board the session opens straight away: its facility's, or the last one browsed; null asks. */
    val default: Dataset? = null,
    val choices: List<BrowseChoice> = emptyList(),
    /** For the + menu: "Kilter Original at Block Lab". */
    val subtitle: String = "Pick a board or dataset",
)

/**
 * Browsing a board in a session: which board, its filters and climbs ([BrowseModel]), and starting
 * a browse on the session's card at the climb you pick. The card then walks the same list.
 */
@HiltViewModel
class BrowseBoardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    datasets: DatasetRepository,
    places: PlaceRepository,
    private val store: BrowseStore,
    private val browser: BoardBrowser,
    private val playlists: PlaylistRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    val model = BrowseModel(viewModelScope, browser, store, playlists, preferences, sessionId)
    val ui: StateFlow<BrowseUi> = model.ui
    val playlistsList: StateFlow<List<Playlist>> = playlists.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val boards: StateFlow<BrowseBoards> = combine(
        sessions.observeSession(sessionId),
        datasets.observeDatasets(),
        places.observePlaces(),
        store.recent,
    ) { session, all, placeList, recent -> browseBoards(session, all, placeList, recent) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowseBoards())

    /** The browse on the card, to reopen the sheet at it. */
    val run: StateFlow<BrowseRun?> = store.observeRun(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Opens [ds] in the sheet, at the facility's last angle when it's the board you're at. */
    fun open(ds: Dataset) {
        viewModelScope.launch {
            val session = sessions.observeSession(sessionId).first()
            val angle = session?.sectionId?.let { preferences.boardAngle(it) }
            model.open(ds, angle)
            store.used(ds.id)
        }
    }

    /** Puts [picked] on the card, the list held in the order it's listed now. */
    fun climb(picked: BrowseCandidate) {
        val now = ui.value
        val ds = now.dataset ?: return
        val keys = now.climbs.map { it.key }
        viewModelScope.launch {
            store.setRun(BrowseRun.at(sessionId, ds.id, now.bar.query.filters.angle, now.bar.query.view, keys, keys.indexOf(picked.key).coerceAtLeast(0)))
            store.used(ds.id)
        }
    }

    /** Long-press: adds a climb to a playlist (a new one when [playlistId] is null). */
    fun addToPlaylist(playlistId: Long?, newName: String?, picked: BrowseCandidate) {
        val ds = ui.value.dataset ?: return
        viewModelScope.launch {
            val item = browser.items(ds, ui.value.bar.query.filters.angle, listOf(picked.key)).firstOrNull() ?: return@launch
            val climb = item.climb ?: return@launch
            val id = playlistId ?: playlists.savePlaylist(0, newName.orEmpty(), null)
            playlists.addClimbs(id, listOf(ClimbSource(climb, ds)))
        }
    }
}

/** The boards a session can browse, last used first, and the one it opens straight away. */
internal fun browseBoards(
    session: com.hardtekpt.crux.data.Session?,
    all: List<Dataset>,
    placeList: List<com.hardtekpt.crux.data.model.PlaceSummary>,
    recent: List<Long>,
): BrowseBoards {
    val place = placeList.firstOrNull { it.place.id == session?.placeId }?.place
    val section: Section? = place?.sections?.firstOrNull { it.id == session?.sectionId }
    val usable = all.filter { it.isCatalogue || it.climbs.isNotEmpty() }

    val order = usable.sortedWith(
        compareBy<Dataset>({ ds ->
            recent.indexOf(ds.id).let {
                if (it <
                    0
                ) {
                    Int.MAX_VALUE
                } else {
                    it
                }
            }
        }, { !it.isCatalogue }, { it.name.lowercase() }),
    )
    val atBoard = section?.takeIf { it.board != null }?.let { s ->
        order.firstOrNull { it.isCatalogue && it.fitsBoard(s) } ?: order.firstOrNull { it.isCatalogue && it.board == s.board }
            ?: order.firstOrNull { it.fitsBoard(s) }
    }
    val default = atBoard ?: recent.firstNotNullOfOrNull { id -> order.firstOrNull { it.id == id } }
    return BrowseBoards(
        default = default,
        choices = order.map { ds ->
            BrowseChoice(
                ds,
                listOfNotNull(
                    if (ds.isCatalogue) ds.boardLabel else "Your dataset",
                    "${"%,d".format(if (ds.isCatalogue) ds.catalogueCount else ds.climbs.size)} climbs",
                ).joinToString(" · "),
            )
        },
        subtitle =
            default?.let { ds -> ds.name + (place?.name?.let { " at $it" } ?: "") }
                ?: if (order.isEmpty()) "Import a board catalogue or make a dataset first" else "Pick a board or dataset",
    )
}

/** "Kilter", "MoonBoard", or the dataset's name, for the card's header. */
internal fun boardShort(ds: Dataset): String = ds.board?.maker ?: ds.name

/** What a row says about you and a climb: goes tonight, goes before, or sent. */
private fun goesLine(c: BrowseCandidate): String? = when {
    c.tonight > 0 -> "${c.tonight} tonight"
    c.sent -> "sent"
    c.goes > 0 -> "${c.goes} ${if (c.goes == 1) "go" else "goes"}"
    else -> null
}

/** "48.2k", "312". */
private fun count(n: Int?): String = when {
    n == null -> "0"
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}

/**
 * Browse a board: the board's name (tap to switch), Tune for every filter, a search, the filter
 * bar, how many climbs match with Clear, and the climbs. Tapping one puts it on the session's
 * card; holding one adds it to a playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseBoardSheet(onDismiss: () -> Unit, viewModel: BrowseBoardViewModel = hiltViewModel()) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistsList.collectAsStateWithLifecycle()
    // Picking a board: when the session has none to open, or from the title.
    var picking by rememberSaveable { mutableStateOf(false) }
    var allOpen by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf<BrowseCandidate?>(null) }
    LaunchedEffect(boards.default?.id, run?.datasetId) {
        if (ui.dataset == null) {
            val start = run?.datasetId?.let { id -> boards.choices.firstOrNull { it.dataset.id == id }?.dataset } ?: boards.default
            if (start != null) {
                viewModel.open(start)
                picking = false
            } else if (boards.choices.isNotEmpty()) {
                picking = true
            }
        }
    }
    val space = CruxTheme.space
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("browse_sheet"),
    ) {
        Column(
            Modifier.fillMaxHeight(0.92f).padding(horizontal = space.s4).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            if (picking || ui.dataset == null) {
                Text("Browse a board", style = MaterialTheme.typography.headlineSmall)
                if (boards.choices.isEmpty()) {
                    Text(
                        "Import a board catalogue (You › Datasets › + › Board snapshot) or make a dataset to browse it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.keepEndScrollInSheet().testTag("browse_boards")) {
                    itemsIndexed(boards.choices, key = { _, it -> it.dataset.id }) { _, choice ->
                        val on = choice.dataset.id == ui.dataset?.id
                        CruxListRow(
                            title = choice.dataset.name,
                            supporting = choice.line,
                            selected = on,
                            leading = {
                                Icon(if (choice.dataset.isCatalogue) Icons.Rounded.Apps else Icons.AutoMirrored.Rounded.List, contentDescription = null)
                            },
                            trailing = if (on) {
                                { Icon(Icons.Rounded.Check, contentDescription = "Open now") }
                            } else {
                                null
                            },
                            onClick = {
                                viewModel.open(choice.dataset)
                                picking = false
                            },
                            modifier = Modifier.testTag("browse_board_${choice.dataset.id}"),
                        )
                    }
                    item {
                        Text(
                            "Next time the session opens the last one straight away; switch from the title.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = space.s2),
                        )
                    }
                }
                return@Column
            }
            val ds = ui.dataset ?: return@Column
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { picking = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.weight(1f).testTag("browse_switch"),
                ) {
                    Text(
                        ds.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Switch board", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = {
                    allOpen = true
                }, modifier = Modifier.testTag("browse_tune")) { Icon(Icons.Rounded.Tune, contentDescription = "All filters") }
            }
            CruxTextField("", ui.text, viewModel.model::setText, placeholder = "Search name or setter", modifier = Modifier.testTag("browse_search"))
            BrowseFilterBar(ui.bar, viewModel.model.actions, allOpen, { allOpen = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    if (ui.loading) "Reading…" else "${"%,d".format(ui.climbs.size)} ${if (ui.climbs.size == 1) "climb" else "climbs"}",
                    Modifier.weight(1f).testTag("browse_count"),
                )
                if (ui.bar.query.filters.count > 0) {
                    TextButton(onClick = viewModel.model::clear, modifier = Modifier.testTag("browse_clear")) { Text("Clear") }
                }
            }
            val list = rememberLazyListState()
            // Reopened from the card: back where you were.
            LaunchedEffect(ui.loading) {
                val r = run
                if (!ui.loading && r != null && r.datasetId == ds.id) {
                    ui.climbs.indexOfFirst { it.key == r.keys.getOrNull(r.index) }.takeIf { it > 0 }?.let { list.scrollToItem(it) }
                }
            }
            LazyColumn(
                state = list,
                verticalArrangement = Arrangement.spacedBy(space.s2),
                contentPadding = PaddingValues(bottom = space.s4),
                modifier = Modifier.weight(1f).keepEndScrollInSheet().testTag("browse_climbs"),
            ) {
                if (!ui.loading && ui.climbs.isEmpty()) {
                    item {
                        Text(
                            "Nothing matches. Clear a filter to see more.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(ui.climbs, key = { _, it -> it.key }) { _, c ->
                    val up = run?.let { r -> r.datasetId == ds.id && r.keys.getOrNull(r.index) == c.key } == true
                    CruxListRow(
                        title = c.name,
                        supporting = listOfNotNull(
                            c.setter,
                            c.ascents?.let { "${count(it)} ascents" },
                            c.rating?.let { "★ %.1f".format(it) },
                            "benchmark".takeIf { c.benchmark },
                        ).joinToString(" · "),
                        leading = { ClimbGrade(c.gradeLabel ?: "–") },
                        trailing = goesLine(c)?.let { line ->
                            {
                                Text(
                                    line.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (c.tonight >
                                        0
                                    ) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (c.sent) {
                                        CruxTheme.colors.success
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                )
                            }
                        },
                        selected = up,
                        onClick = {
                            viewModel.climb(c)
                            onDismiss()
                        },
                        onLongClick = { adding = c },
                        modifier = Modifier.testTag("browse_climb"),
                    )
                }
            }
        }
    }
    adding?.let { c ->
        com.hardtekpt.crux.ui.datasets.AddToPlaylistSheet(
            count = 1,
            playlists = playlists,
            onPick = { id ->
                viewModel.addToPlaylist(id, null, c)
                adding = null
            },
            onNew = { name ->
                viewModel.addToPlaylist(null, name, c)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
}
