package com.hardtekpt.crux.ui.datasets

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbSource
import com.hardtekpt.crux.data.DatasetRepository
import com.hardtekpt.crux.data.Playlist
import com.hardtekpt.crux.data.PlaylistFiles
import com.hardtekpt.crux.data.PlaylistItem
import com.hardtekpt.crux.data.PlaylistRepository
import com.hardtekpt.crux.data.datasetsFor
import com.hardtekpt.crux.data.triedKey
import com.hardtekpt.crux.ui.components.ActionSheet
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    /** Dataset climbs tried today: true where sent. */
    val today: Map<String, Boolean> = emptyMap(),
)

/** One playlist: reorder, remove, rename, export, and climb it in a session. */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val datasets: DatasetRepository,
    private val places: com.hardtekpt.crux.data.PlaceRepository,
    private val sessions: com.hardtekpt.crux.data.SessionRepository,
    private val preferences: com.hardtekpt.crux.data.prefs.UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 0L

    val uiState: StateFlow<PlaylistUiState> = combine(repository.observePlaylists(), repository.observeTriedOn(LocalDate.now(clock))) { lists, today ->
        PlaylistUiState(isLoading = false, playlist = lists.firstOrNull { it.id == playlistId }, today = today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState())

    /** The item removed last, to put back from the snackbar. */
    private val _undo = MutableStateFlow<PlaylistItem?>(null)
    val undo: StateFlow<PlaylistItem?> = _undo.asStateFlow()

    fun remove(item: PlaylistItem) {
        viewModelScope.launch {
            repository.removeItem(item.id)
            _undo.value = item
        }
    }

    fun undoRemove() {
        val item = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { repository.restoreItem(playlistId, item) }
    }

    fun clearUndo() {
        _undo.value = null
    }

    fun move(ids: List<Long>) {
        viewModelScope.launch { repository.reorder(playlistId, ids) }
    }

    fun rename(name: String, note: String?) {
        viewModelScope.launch { repository.savePlaylist(playlistId, name, note) }
    }

    fun duplicate(onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repository.duplicate(playlistId)) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            onDone()
        }
    }

    fun export(uri: Uri) {
        val playlist = uiState.value.playlist ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = PlaylistFiles.write(playlist, Instant.ofEpochMilli(clock.millis()).toString())
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    /**
     * Climb this playlist: in the running session, or a new one at a facility that uses its
     * datasets (the last place climbed when it's one of them). Then [onStarted] with the session.
     */
    fun climb(onStarted: (Long) -> Unit) {
        val playlist = uiState.value.playlist ?: return
        viewModelScope.launch {
            val running = sessions.running()
            val id = running?.id ?: run {
                val ids = playlist.items.mapNotNull { it.dataset?.id }.toSet()
                val all = datasets.observeDatasets().first()
                val links = datasets.observeLinks().first()
                val last = preferences.lastPlaceId.first()
                val spots = places.observePlaces().first().map { it.place }.flatMap { place ->
                    place.sections.filter { section -> datasetsFor(section, all, links[section.id].orEmpty()).any { it.id in ids } }.map { place.id to it.id }
                }
                val spot = spots.firstOrNull { it.first == last } ?: spots.firstOrNull()
                sessions.start(null, spot?.first ?: last, spot?.second)
            }
            sessions.setPlaylist(id, playlistId, 0)
            onStarted(id)
        }
    }
}

/**
 * A playlist: its climbs in order, with a dot for those tried today (green once sent). Drag a
 * climb by its handle to move it, swipe it away to remove it (with Undo), tap it to log it.
 * ⋯ holds Rename, Duplicate, Export and Delete; Climb this playlist starts or joins a session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onLogClimb: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel(),
    /** Log climb on a board catalogue's climb, by the board's id for it. */
    onLogCatalogueClimb: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> uri?.let(viewModel::export) }
    val space = CruxTheme.space

    var seen by rememberSaveable { mutableStateOf(false) }
    val playlist = uiState.playlist
    if (playlist != null) seen = true
    if (seen && playlist == null && !uiState.isLoading) LaunchedEffect(Unit) { onBack() }

    LaunchedEffect(undo) {
        val item = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Removed ${item.name}", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove() else viewModel.clearUndo()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().testTag("screen_Playlist")) {
            CruxTopAppBar(title = playlist?.name.orEmpty(), onBack = onBack) {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("playlist_menu")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                }
            }
            val list = playlist ?: return@Column
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = space.s4)
                    .padding(bottom = space.s4 + 72.dp + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                Text(playlistLine(list), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (list.items.isEmpty()) {
                    Text(
                        "No climbs yet. In a dataset, long-press climbs and add them, or open a climb and tap + Playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = space.s4),
                    )
                }
                ReorderableItems(
                    items = list.items,
                    today = uiState.today,
                    onMove = viewModel::move,
                    onRemove = viewModel::remove,
                    onOpen = { item -> item.climb?.let { climb -> climb.catalogueUuid?.let(onLogCatalogueClimb) ?: onLogClimb(climb.id) } },
                )
            }
        }
        if (playlist != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            ) {
                CruxButton(
                    "Climb this playlist",
                    { viewModel.climb(onOpenSession) },
                    icon = Icons.Rounded.PlayArrow,
                    size = CruxButtonSize.Large,
                    enabled = playlist.items.any { it.matched },
                    modifier = Modifier.fillMaxWidth().testTag("playlist_climb"),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp + LocalNavBarClearance.current))
    }

    val list = playlist ?: return
    if (menu) {
        PlaylistMenuSheet(list.name, { menu = false }) {
            CruxListRow("Rename", supporting = "Name and note", onClick = {
                menu = false
                renaming = true
            }, modifier = Modifier.testTag("playlist_rename"))
            CruxListRow("Duplicate", supporting = "A copy with the same climbs", onClick = {
                menu = false
                viewModel.duplicate(onOpenPlaylist)
            }, modifier = Modifier.testTag("playlist_duplicate"))
            CruxListRow("Export", supporting = "A Climb Logger playlist file to share", onClick = {
                menu = false
                saveFile.launch("${list.name.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase().ifEmpty { "playlist" }}.crux-playlist.json")
            }, modifier = Modifier.testTag("playlist_export"))
            CruxListRow("Delete playlist", supporting = "Asks first", onClick = {
                menu = false
                confirmDelete = true
            }, modifier = Modifier.testTag("playlist_delete"))
        }
    }
    if (renaming) {
        PlaylistDetailsSheet(
            title = "Rename",
            confirm = "Save",
            initialName = list.name,
            initialNote = list.note.orEmpty(),
            onSave = { name, note ->
                renaming = false
                viewModel.rename(name, note)
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${list.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("The climbs stay in their datasets, and climbs you logged are never touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }, modifier = Modifier.testTag("confirm_delete_playlist")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** "8 climbs · Kilter Original and Garage board". */
internal fun playlistLine(playlist: Playlist): String {
    val names = playlist.datasetNames
    return listOfNotNull(
        "${playlist.items.size} ${if (playlist.items.size == 1) "climb" else "climbs"}",
        when (names.size) {
            0 -> null
            1 -> names.single()
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        },
    ).joinToString(" · ")
}

/**
 * The playlist's climbs: a row each, dragged by its handle (after a long press) to a new place,
 * or swiped away. A climb whose dataset isn't here is greyed and can't be logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderableItems(
    items: List<PlaylistItem>,
    today: Map<String, Boolean>,
    onMove: (List<Long>) -> Unit,
    onRemove: (PlaylistItem) -> Unit,
    onOpen: (PlaylistItem) -> Unit,
) {
    val heights = remember { mutableStateMapOf<Long, Int>() }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { CruxTheme.space.s2.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.testTag("playlist_items")) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) onRemove(item)
                        value != SwipeToDismissBoxValue.Settled
                    },
                )
                val lifted = dragging == item.id
                Box(
                    Modifier
                        .onSizeChanged { heights[item.id] = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .offset { IntOffset(0, if (lifted) offset.roundToInt() else 0) }
                        .then(if (lifted) Modifier.shadow(8.dp, MaterialTheme.shapes.medium) else Modifier),
                ) {
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                                    .padding(horizontal = CruxTheme.space.s4),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer) }
                        },
                    ) {
                        CruxListRow(
                            title = item.name,
                            supporting = listOfNotNull(
                                item.datasetName,
                                item.climb?.setter,
                                "not in your datasets".takeIf { !item.matched },
                            ).joinToString(" · "),
                            leading = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.DragIndicator,
                                        contentDescription = "Hold and drag to move",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .pointerInput(item.id, items) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        dragging = item.id
                                                        offset = 0f
                                                    },
                                                    onDragEnd = {
                                                        val step = (heights[item.id] ?: 1) + gap
                                                        val to = (index + (offset / step).roundToInt()).coerceIn(0, items.lastIndex)
                                                        if (to != index) {
                                                            val ids = items.map { it.id }.toMutableList()
                                                            ids.add(to, ids.removeAt(index))
                                                            onMove(ids)
                                                        }
                                                        dragging = null
                                                        offset = 0f
                                                    },
                                                    onDragCancel = {
                                                        dragging = null
                                                        offset = 0f
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        offset += amount.y
                                                    },
                                                )
                                            }
                                            .testTag("playlist_handle"),
                                    )
                                    Text(
                                        item.gradeLabel ?: "–",
                                        style = CruxTheme.type.grade,
                                        maxLines = 1,
                                        modifier = Modifier.width(44.dp).padding(start = 4.dp),
                                    )
                                }
                            },
                            trailing = item.climb?.let { climb ->
                                today[climb.triedKey()]?.let { sent ->
                                    {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .background(if (sent) CruxTheme.colors.success else MaterialTheme.colorScheme.outline, CircleShape),
                                        )
                                    }
                                }
                            },
                            onClick = if (item.matched) ({ onOpen(item) }) else null,
                            modifier = Modifier.alpha(if (item.matched) 1f else 0.5f).testTag("playlist_item"),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistMenuSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ActionSheet(title, onDismiss, Modifier.testTag("playlist_sheet")) { content() }
}

/** A playlist's name and an optional note, for New playlist and Rename. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistDetailsSheet(
    title: String,
    confirm: String,
    initialName: String,
    initialNote: String,
    onSave: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    var error by remember { mutableStateOf<String?>(null) }
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("playlist_details"),
        title = title,
        confirm = SheetButton(confirm, {
            if (name.isBlank()) error = "Give the playlist a name" else onSave(name, note.ifBlank { null })
        }, tag = "playlist_details_save"),
        scroll = false,
    ) {
        CruxTextField(
            label = "Name",
            value = name,
            onValueChange = {
                name = it.take(60)
                error = null
            },
            placeholder = "Tonight, 7A benchmarks",
            error = error,
            modifier = Modifier.testTag("field_playlist_name"),
        )
        CruxTextField(
            label = "Note",
            value = note,
            onValueChange = { note = it.take(300) },
            placeholder = "Optional",
            singleLine = false,
            minLines = 2,
            modifier = Modifier.testTag("field_playlist_note"),
        )
    }
}

/** Which playlist to add climbs to, or a new one named here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistSheet(count: Int, playlists: List<Playlist>, onPick: (Long) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit) {
    var creating by rememberSaveable { mutableStateOf(playlists.isEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("add_to_playlist"),
        title = if (count == 1) "Add to a playlist" else "Add $count climbs to a playlist",
    ) {
        playlists.forEach { list ->
            CruxListRow(
                title = list.name,
                supporting = playlistLine(list),
                onClick = { onPick(list.id) },
                modifier = Modifier.testTag("add_to_${list.name}"),
            )
        }
        if (creating) {
            CruxTextField(
                label = "New playlist",
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = "Tonight",
                modifier = Modifier.testTag("field_new_playlist"),
            )
            CruxButton("Create and add", { onNew(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("new_playlist_add"))
        } else {
            CruxListRow(
                title = "New playlist",
                leading = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { creating = true },
                modifier = Modifier.testTag("add_to_new"),
            )
        }
    }
}

/** Climbs from a dataset, as playlist items take them. */
internal fun sourcesOf(dataset: com.hardtekpt.crux.data.Dataset, ids: Collection<Long>): List<ClimbSource> =
    dataset.climbs.filter { it.id in ids }.map { ClimbSource(it, dataset) }

/** The playlist rows on the Datasets page. */
@Composable
internal fun PlaylistRow(playlist: Playlist, onOpen: () -> Unit) {
    CruxListRow(
        title = playlist.name,
        supporting = playlistLine(playlist),
        leading = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        onClick = onOpen,
        modifier = Modifier.testTag("playlist_row"),
    )
}
