package com.hardtekpt.crux.ui.datasets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BoardseshBoards
import com.hardtekpt.crux.data.CatalogueClimb
import com.hardtekpt.crux.data.CatalogueImporter
import com.hardtekpt.crux.data.CatalogueRepository
import com.hardtekpt.crux.data.CatalogueRow
import com.hardtekpt.crux.data.ClimbSource
import com.hardtekpt.crux.data.Dataset
import com.hardtekpt.crux.data.DatasetRepository
import com.hardtekpt.crux.data.Playlist
import com.hardtekpt.crux.data.PlaylistRepository
import com.hardtekpt.crux.data.convertGrade
import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.boardSizeLabel
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a catalogue's climbs are listed. */
enum class CatalogueSort(val label: String) { POPULAR("Popular"), GRADE("Grade"), NAME("Name") }

/** What the catalogue page asks for: the angle, a search, a sort and benchmarks only. */
data class CatalogueQuery(
    val angle: Int? = null,
    val text: String = "",
    val sort: CatalogueSort = CatalogueSort.POPULAR,
    val benchmarks: Boolean = false,
    /** A grade range, as indices in [catalogueScale]. */
    val gradeFrom: Int? = null,
    val gradeTo: Int? = null,
    /** Hide the climbs you have sent. */
    val hideSent: Boolean = false,
)

private const val HIDE_SENT_SCAN = 2000

/** The scale a catalogue's grades are read and filtered in: the climber's boulder scale, or Font for a local one. */
internal fun catalogueScale(scales: GradeScales): GradeScale = scales.boulder.takeIf { !it.isLocal } ?: GradeScale.FONT

private fun fontOf(scale: GradeScale, index: Int?): Int? = index?.let { convertGrade(scale, it, GradeScale.FONT) }

data class CatalogueUiState(
    val dataset: Dataset? = null,
    val angles: List<Int> = emptyList(),
    val query: CatalogueQuery = CatalogueQuery(),
    val rows: List<CatalogueRow> = emptyList(),
    val loading: Boolean = true,
    val scales: GradeScales = GradeScales(),
    val playlists: List<Playlist> = emptyList(),
    /** Climbs tried today, by [com.hardtekpt.crux.data.triedKey]. */
    val today: Map<String, Boolean> = emptyMap(),
)

/** A board catalogue: its climbs at an angle, searched and sorted, a page at a time. */
@HiltViewModel
class CatalogueViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val datasets: DatasetRepository,
    private val catalogue: CatalogueRepository,
    private val playlists: PlaylistRepository,
    preferences: UserPreferencesRepository,
    clock: Clock,
) : ViewModel() {
    val datasetId: Long = savedStateHandle.get<Long>("datasetId") ?: 0L
    private val scalesFlow = preferences.gradeScales
    private val sentKeys = playlists.observeSent()
    private val query = MutableStateFlow(CatalogueQuery())
    private val found = MutableStateFlow<Pair<CatalogueQuery, List<CatalogueRow>>?>(null)
    private val angles = MutableStateFlow<List<Int>>(emptyList())

    val uiState: StateFlow<CatalogueUiState> = combine(
        combine(datasets.observeDatasets(), preferences.gradeScales, playlists.observePlaylists(), playlists.observeTriedOn(LocalDate.now(clock))) {
                a,
                b,
                c,
                d,
            ->
            listOf(a, b, c, d)
        },
        query,
        found,
        angles,
    ) { parts, q, result, angleList ->
        @Suppress("UNCHECKED_CAST")
        CatalogueUiState(
            dataset = (parts[0] as List<Dataset>).firstOrNull { it.id == datasetId },
            angles = angleList,
            query = q,
            rows = result?.second.orEmpty(),
            loading = result?.first != q,
            scales = parts[1] as GradeScales,
            playlists = parts[2] as List<Playlist>,
            today = parts[3] as Map<String, Boolean>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogueUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val key = datasets.observeDatasets().first().firstOrNull { it.id == datasetId }?.catalogueKey ?: return@launch
            val list = catalogue.angles(key)
            angles.value = list
            // 40° is the commonest board angle; a MoonBoard has one or two.
            query.update { it.copy(angle = if (40 in list) 40 else list.firstOrNull()) }
            query.collectLatest { q ->
                val angle = q.angle ?: return@collectLatest
                val scale = catalogueScale(scalesFlow.first())
                val from = fontOf(scale, q.gradeFrom)
                val to = fontOf(scale, q.gradeTo)
                val rows = if (q.hideSent) {
                    // The sent ones are in another database, so look further down the list and take a page of what is left.
                    val sent = sentKeys.first()
                    catalogue.search(key, angle, q.text, q.sort.ordinal, q.benchmarks, from, to, limit = HIDE_SENT_SCAN)
                        .filter { "c${it.uuid}" !in sent }.take(CatalogueRepository.PAGE)
                } else {
                    catalogue.search(key, angle, q.text, q.sort.ordinal, q.benchmarks, from, to)
                }
                found.value = q to rows
            }
        }
    }

    fun update(change: (CatalogueQuery) -> CatalogueQuery) = query.update(change)

    /** A climb with its grade at every angle, for its sheet. */
    suspend fun climb(uuid: String): CatalogueClimb? = uiState.value.dataset?.catalogueKey?.let { catalogue.climb(it, uuid) }

    fun addToPlaylist(playlistId: Long?, newName: String?, climb: CatalogueClimb) {
        val dataset = uiState.value.dataset ?: return
        viewModelScope.launch {
            val id = playlistId ?: playlists.savePlaylist(0, newName.orEmpty(), null)
            val added = playlists.addClimbs(id, listOf(ClimbSource(climb.asDatasetClimb(query.value.angle), dataset)))
            val name = playlists.observePlaylists().first().firstOrNull { it.id == id }?.name.orEmpty()
            _message.value = if (added == 0) "Already in $name" else "Added ${climb.name} to $name"
        }
    }

    fun messageShown() {
        _message.value = null
    }

    /** Deletes the catalogue and its climbs; climbs you logged from it stay as they are. */
    fun delete(onDone: () -> Unit) {
        val dataset = uiState.value.dataset ?: return
        viewModelScope.launch {
            dataset.catalogueKey?.let { catalogue.delete(it) }
            datasets.deleteDataset(dataset.id)
            onDone()
        }
    }
}

/** A board difficulty as the climber's boulder scale has it: "6C", or "V5". */
internal fun boardGrade(difficulty: Double?, scales: GradeScales): String {
    val font = difficulty?.let(BoardseshBoards::fontIndex) ?: return "–"
    val scale = scales.boulder.takeIf { !it.isLocal } ?: GradeScale.FONT
    return convertGrade(GradeScale.FONT, font, scale)?.let(scale::label) ?: GradeScale.FONT.label(font)
}

/** "48.2k", "312". */
private fun count(n: Int?): String = when {
    n == null -> "0"
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}

/**
 * A board catalogue: its climbs at the chosen angle (each graded and sent at each angle), with a
 * search over names and setters, Popular, Grade and Name sorts and a Benchmarks filter. A dot marks
 * climbs you tried today. Tap one for its grades, Log this climb and + Playlist.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CatalogueScreen(onBack: () -> Unit, onLogClimb: (String) -> Unit, viewModel: CatalogueViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<CatalogueClimb?>(null) }
    var addingTo by remember { mutableStateOf<CatalogueClimb?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val space = CruxTheme.space
    val dataset = uiState.dataset

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.messageShown()
        snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().testTag("screen_Catalogue")) {
            CruxTopAppBar(title = dataset?.name.orEmpty(), onBack = onBack) {
                // Its one action straight in the top bar, as Edit place's delete is; it still asks first.
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("catalogue_delete")) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete catalogue")
                }
            }
            val q = uiState.query
            val range = GradeRange(catalogueScale(uiState.scales), q.gradeFrom, q.gradeTo)
            LazyColumn(
                contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s2),
                modifier = Modifier.weight(1f).testTag("catalogue_climbs"),
            ) {
                item(key = "line") {
                    Text(
                        listOfNotNull(dataset?.boardLabel, dataset?.let { "${"%,d".format(it.catalogueCount)} climbs" }, "read-only").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "search") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        CruxTextField(
                            label = "",
                            value = q.text,
                            onValueChange = { text -> viewModel.update { it.copy(text = text.take(60)) } },
                            placeholder = "Search name or setter",
                            modifier = Modifier.weight(1f).testTag("catalogue_search"),
                        )
                        FiltersButton(
                            active = listOf(range.active, q.hideSent).count { it },
                            onClick = { filtering = true },
                            modifier = Modifier.testTag("catalogue_open_filters"),
                        )
                    }
                }
                if (range.active || q.hideSent) {
                    item(key = "applied") {
                        Text(
                            listOfNotNull(
                                range.label()?.let {
                                    "Grades $it"
                                },
                                "hiding sent climbs".takeIf { q.hideSent },
                            ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("catalogue_grade_applied"),
                        )
                    }
                }
                if (uiState.angles.size > 1) {
                    item(key = "angles") {
                        // The chosen angle (40° to start) in view, not the row's start.
                        val angleRow = androidx.compose.foundation.lazy.rememberLazyListState()
                        LaunchedEffect(q.angle, uiState.angles) {
                            uiState.angles.indexOf(q.angle).takeIf { it > 0 }?.let { angleRow.scrollToItem((it - 1).coerceAtLeast(0)) }
                        }
                        LazyRow(state = angleRow, horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.testTag("catalogue_angles")) {
                            items(uiState.angles) { angle ->
                                CruxFilterChip("$angle°", q.angle == angle, {
                                    viewModel.update { it.copy(angle = angle) }
                                }, Modifier.testTag("catalogue_angle_$angle"))
                            }
                        }
                    }
                }
                item(key = "sort") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                        CatalogueSort.entries.forEach { sort ->
                            CruxFilterChip(sort.label, q.sort == sort, {
                                viewModel.update { it.copy(sort = sort) }
                            }, Modifier.testTag("catalogue_sort_${sort.name}"))
                        }
                        CruxFilterChip("Benchmarks", q.benchmarks, {
                            viewModel.update { it.copy(benchmarks = !it.benchmarks) }
                        }, Modifier.testTag("catalogue_benchmarks"))
                    }
                }
                if (!uiState.loading && uiState.rows.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "Nothing matches at ${q.angle}°.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = space.s4),
                        )
                    }
                }
                items(uiState.rows, key = { it.uuid }) { row ->
                    CruxListRow(
                        title = row.name,
                        supporting = listOfNotNull(
                            row.setter,
                            "${count(row.ascents)} ascents",
                            row.quality?.let { "★ %.1f".format(it) },
                            "benchmark".takeIf { row.benchmark == true },
                        ).joinToString(" · "),
                        leading = { com.hardtekpt.crux.ui.components.ClimbGrade(boardGrade(row.difficulty, uiState.scales)) },
                        trailing = uiState.today["c${row.uuid}"]?.let { sent ->
                            {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(if (sent) CruxTheme.colors.success else MaterialTheme.colorScheme.secondary, CircleShape),
                                )
                            }
                        },
                        onClick = { scope.launch { open = viewModel.climb(row.uuid) } },
                        modifier = Modifier.testTag("catalogue_climb"),
                    )
                }
                if (uiState.rows.size >= CatalogueRepository.PAGE) {
                    item(key = "more") {
                        Text(
                            "Showing the first ${CatalogueRepository.PAGE}. Search to find others.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "credit") {
                    Text(
                        "Climbs and grades by each board's community, from a Boardsesh snapshot. Climb Logger isn't affiliated with Kilter or Moon Climbing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = space.s4),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = space.s4 + LocalNavBarClearance.current))
    }

    val ds = dataset ?: return
    if (filtering) {
        GradeFilterSheet(
            range = GradeRange(catalogueScale(uiState.scales), uiState.query.gradeFrom, uiState.query.gradeTo),
            shown = uiState.rows.size,
            onRange = { r -> viewModel.update { it.copy(gradeFrom = r.from, gradeTo = r.to) } },
            hideSent = uiState.query.hideSent,
            onHideSent = { on -> viewModel.update { it.copy(hideSent = on) } },
            onDismiss = { filtering = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${ds.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text("Its climbs leave Climb Logger, and your playlists lose them until you import it again. Climbs you logged from it stay as they are.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }, modifier = Modifier.testTag("confirm_delete_catalogue")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    open?.let { climb ->
        CatalogueClimbSheet(
            climb = climb,
            board = ds.holdsBoard,
            angle = uiState.query.angle,
            scales = uiState.scales,
            onLog = {
                open = null
                onLogClimb(climb.uuid)
            },
            onPlaylist = {
                open = null
                addingTo = climb
            },
            onDismiss = { open = null },
        )
    }
    addingTo?.let { climb ->
        AddToPlaylistSheet(
            count = 1,
            playlists = uiState.playlists,
            onPick = { id ->
                addingTo = null
                viewModel.addToPlaylist(id, null, climb)
            },
            onNew = { name ->
                addingTo = null
                viewModel.addToPlaylist(null, name, climb)
            },
            onDismiss = { addingTo = null },
        )
    }
}

/** A catalogue climb: who set it, its description, its grade and ascents at each angle, Log this climb and + Playlist. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CatalogueClimbSheet(
    climb: CatalogueClimb,
    board: String?,
    angle: Int?,
    scales: GradeScales,
    onLog: () -> Unit,
    onPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val space = CruxTheme.space
    val here = climb.gradeAt(angle)
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("catalogue_climb_sheet"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(climb.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(climb.setter?.let { "set by $it" }, climb.setterAngle?.let { "at $it°" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(boardGrade(here?.difficulty, scales), style = CruxTheme.type.grade)
        }
        // The holds on the board; tap for the whole board.
        val frames = climb.frames
        if (board != null && frames != null) {
            com.hardtekpt.crux.ui.components.BoardDiagramCard(board, frames, climb.name, Modifier.fillMaxWidth())
            com.hardtekpt.crux.ui.components.HoldLegend(frames)
            com.hardtekpt.crux.ui.components.LightButton(board, frames, climb.name)
        }
        if (board != null) com.hardtekpt.crux.ui.components.MoonBoardAppButton(board, climb.name)
        climb.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Eyebrow("By angle", Modifier.padding(top = space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
            climb.grades.forEach { grade ->
                CruxFilterChip(
                    "${grade.angle}° ${boardGrade(grade.difficulty, scales)} · ${count(grade.ascents)}",
                    grade.angle == here?.angle,
                    {},
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.padding(top = space.s2)) {
            CruxButton("Log this climb", onLog, icon = Icons.Rounded.Check, modifier = Modifier.weight(1f).testTag("catalogue_log"))
            CruxButton(
                "Playlist",
                onPlaylist,
                icon = Icons.Rounded.Add,
                variant = CruxButtonVariant.Outlined,
                modifier = Modifier.weight(1f).testTag("catalogue_playlist"),
            )
        }
    }
}

/**
 * Importing a board catalogue: what the file is, its name and (for Kilter) your board's size and
 * holds, then progress while it copies, and what it kept.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CatalogueImportSheet(
    state: CatalogueImporter.State,
    /** Your board sections' boards, to start at your size. */
    yours: List<Triple<BoardModel, String?, BoardHolds?>>,
    onImport: (String, String?, BoardHolds?) -> Unit,
    onOpen: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val space = CruxTheme.space
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("catalogue_import_sheet"),
    ) {
        when (state) {
            CatalogueImporter.State.Idle, is CatalogueImporter.State.Failed -> Unit

            CatalogueImporter.State.Reading -> {
                Text("Reading the file…", style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            is CatalogueImporter.State.Ready -> {
                val inspection = state.inspection
                val board = inspection.board
                val mine = yours.firstOrNull { it.first == board }
                var name by rememberSaveable { mutableStateOf(board.label) }
                var size by rememberSaveable { mutableStateOf(mine?.second?.takeIf { it in board.sizes } ?: board.sizes.firstOrNull()) }
                var holds by rememberSaveable { mutableStateOf(mine?.third ?: BoardHolds.FULL_RIDE.takeIf { board.hasHolds }) }
                Text("Import ${board.label}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${"%,d".format(inspection.climbs)} climbs in ${inspection.fileName}" +
                        if (board.needsSize) ". Only those for your size are kept." else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CruxTextField(label = "Name", value = name, onValueChange = { name = it.take(60) }, modifier = Modifier.testTag("catalogue_import_name"))
                if (board.needsSize) {
                    Eyebrow("Your board's size", Modifier.padding(top = space.s1))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        board.sizes.forEach { option ->
                            CruxFilterChip(boardSizeLabel(option), size == option, { size = option }, Modifier.testTag("catalogue_size_$option"))
                        }
                    }
                }
                if (board.hasHolds) {
                    Eyebrow("Holds", Modifier.padding(top = space.s1))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        BoardHolds.entries.forEach { option ->
                            CruxFilterChip(option.label, holds == option, { holds = option }, Modifier.testTag("catalogue_holds_${option.key}"))
                        }
                    }
                }
                Text(
                    "The catalogue stays on this phone, apart from your data: it isn't in backups or exports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CruxButton(
                    "Import",
                    { onImport(name, size, holds) },
                    size = CruxButtonSize.Large,
                    modifier = Modifier.fillMaxWidth().padding(top = space.s2).testTag("catalogue_import_confirm"),
                )
            }

            is CatalogueImporter.State.Importing -> {
                Text("Importing ${state.board.label}", style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else (state.done.toFloat() / state.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().testTag("catalogue_import_progress"),
                )
                Text(
                    "You can leave this page; the import carries on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is CatalogueImporter.State.Done -> {
                Text("Imported ${state.name}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${"%,d".format(state.climbs)} climbs",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("catalogue_import_result"),
                )
                CruxButton("Open", {
                    onOpen(state.datasetId)
                }, size = CruxButtonSize.Large, modifier = Modifier.fillMaxWidth().testTag("catalogue_import_open"))
            }
        }
    }
}
