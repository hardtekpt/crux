package com.hardtekpt.crux.ui.datasets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClashChoice
import com.hardtekpt.crux.data.Dataset
import com.hardtekpt.crux.data.DatasetClimb
import com.hardtekpt.crux.data.DatasetFiles
import com.hardtekpt.crux.data.DatasetRepository
import com.hardtekpt.crux.data.ImportCount
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A file being imported, before anything is saved: what it holds, where it goes (a new dataset,
 * or [intoId]) and in which scales its grades are read. [result] is set once it's imported.
 */
data class ImportDraft(
    val fileName: String,
    val parsed: DatasetFiles.Parsed,
    val intoId: Long? = null,
    val name: String,
    val dataset: Dataset,
    val clash: ClashChoice = ClashChoice.SKIP,
    val result: ImportCount? = null,
) {
    /** The scale a discipline's grades are read in: the dataset's, else the climber's. */
    fun scaleFor(discipline: Discipline, settings: GradeScales, into: Dataset?): GradeScale =
        (into ?: dataset).scaleFor(discipline) ?: settings.forDiscipline(discipline)
}

/**
 * Reads files into an [ImportDraft] and imports it. Shared by the Datasets page (into a new
 * dataset, or one you pick) and a dataset's own Import climbs.
 */
class DatasetImport(
    private val context: Context,
    private val repository: DatasetRepository,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val playlists: com.hardtekpt.crux.data.PlaylistRepository? = null,
) {
    /** A playlist file imported: its id, name, and how many of its climbs were found here. */
    data class PlaylistImported(val id: Long, val name: String, val matched: Int, val total: Int)

    private val _playlist = MutableStateFlow<PlaylistImported?>(null)
    val playlist: StateFlow<PlaylistImported?> = _playlist.asStateFlow()

    fun playlistSeen() {
        _playlist.value = null
    }

    private val _draft = MutableStateFlow<ImportDraft?>(null)
    val draft: StateFlow<ImportDraft?> = _draft.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun open(uri: Uri, intoId: Long? = null) {
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("unreadable")
                    // A playlist file goes straight in: it only points at climbs.
                    if (playlists != null && com.hardtekpt.crux.data.PlaylistFiles.isPlaylist(text)) {
                        val parsed = com.hardtekpt.crux.data.PlaylistFiles.parse(text)
                        val (id, matched) = playlists.import(parsed)
                        _playlist.value = PlaylistImported(id, parsed.name, matched, parsed.items.size)
                        return@runCatching null
                    }
                    name to DatasetFiles.parse(name, text)
                }
            }
            read.onSuccess { result ->
                val (name, parsed) = result ?: return@onSuccess
                val base = name?.substringBefore('.')?.ifBlank { null } ?: "Imported"
                val dataset = parsed.dataset ?: Dataset(name = base)
                _draft.value = ImportDraft(fileName = name ?: "file", parsed = parsed, intoId = intoId, name = dataset.name, dataset = dataset)
                _error.value = null
            }.onFailure {
                _error.value = (it as? DatasetFiles.Unreadable)?.message ?: "This file can't be read. Pick a CSV or a Climb Logger dataset file."
            }
        }
    }

    fun update(change: (ImportDraft) -> ImportDraft) = _draft.update { it?.let(change) }

    fun dismiss() {
        _draft.value = null
    }

    fun clearError() {
        _error.value = null
    }

    /** Imports the draft with grades read in [scaleFor]; then [onDone] with the dataset's id. */
    fun import(scaleFor: (Discipline) -> GradeScale, onDone: (Long) -> Unit = {}) {
        val d = _draft.value ?: return
        scope.launch {
            val climbs = DatasetFiles.resolve(d.parsed.climbs, scaleFor)
            val (id, count) = repository.importClimbs(
                newDataset = d.dataset.copy(name = d.name.trim().ifEmpty { "Imported" }).takeIf { d.intoId == null },
                intoId = d.intoId,
                climbs = climbs,
                clash = d.clash,
            )
            _draft.update { it?.copy(result = count) }
            onDone(id)
        }
    }
}

data class DatasetsUiState(
    val isLoading: Boolean = true,
    val datasets: List<Dataset> = emptyList(),
    val scales: GradeScales = GradeScales(),
    val playlists: List<com.hardtekpt.crux.data.Playlist> = emptyList(),
)

@HiltViewModel
class DatasetsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DatasetRepository,
    preferences: UserPreferencesRepository,
    private val playlists: com.hardtekpt.crux.data.PlaylistRepository,
    private val catalogueImporter: com.hardtekpt.crux.data.CatalogueImporter? = null,
    places: com.hardtekpt.crux.data.PlaceRepository? = null,
) : ViewModel() {
    /** A board catalogue being read or imported. */
    val catalogue: StateFlow<com.hardtekpt.crux.data.CatalogueImporter.State> =
        catalogueImporter?.state ?: MutableStateFlow(com.hardtekpt.crux.data.CatalogueImporter.State.Idle)

    /** Your board sections' boards, so a Kilter import starts at your size. */
    val boards: StateFlow<List<Triple<com.hardtekpt.crux.data.model.BoardModel, String?, com.hardtekpt.crux.data.model.BoardHolds?>>> =
        (places?.observePlaces() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .map { list -> list.flatMap { it.place.sections }.mapNotNull { s -> s.board?.let { Triple(it, s.boardSize, s.boardHolds) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens a file: a Boardsesh snapshot becomes a board catalogue; anything else is read as a dataset or playlist. */
    fun openFile(uri: android.net.Uri) {
        viewModelScope.launch {
            val header = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(16) } }.getOrNull()
            } ?: ByteArray(0)
            val importer = catalogueImporter
            if (importer != null && importer.looksLikeSnapshot(header)) importer.inspect(uri) else this@DatasetsViewModel.importer.open(uri)
        }
    }

    fun importCatalogue(name: String, size: String?, holds: com.hardtekpt.crux.data.model.BoardHolds?) = catalogueImporter?.import(name, size, holds)

    fun dismissCatalogue() = catalogueImporter?.dismiss()

    val uiState: StateFlow<DatasetsUiState> = combine(repository.observeDatasets(), preferences.gradeScales, playlists.observePlaylists()) {
            list,
            scales,
            lists,
        ->
        DatasetsUiState(isLoading = false, datasets = list, scales = scales, playlists = lists)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatasetsUiState())

    val importer = DatasetImport(context, repository, viewModelScope, playlists)

    /** Creates a dataset; then [onCreated] with its id. */
    fun create(dataset: Dataset, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.saveDataset(dataset.copy(id = 0))) }
    }

    /** Creates a playlist; then [onCreated] with its id. */
    fun createPlaylist(name: String, note: String?, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(playlists.savePlaylist(0, name, note)) }
    }
}

/** How a dataset's climbs are listed. */
enum class ClimbSort(val label: String) { GRADE("Grade"), NAME("Name"), NEWEST("Newest") }

data class DatasetUiState(
    val isLoading: Boolean = true,
    val dataset: Dataset? = null,
    val datasets: List<Dataset> = emptyList(),
    val scales: GradeScales = GradeScales(),
    val playlists: List<com.hardtekpt.crux.data.Playlist> = emptyList(),
    /** What you have sent, for [com.hardtekpt.crux.data.isSentIn]. */
    val sent: Set<String> = emptySet(),
)

@HiltViewModel
class DatasetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: DatasetRepository,
    preferences: UserPreferencesRepository,
    private val clock: Clock,
    private val playlists: com.hardtekpt.crux.data.PlaylistRepository,
    browser: com.hardtekpt.crux.data.BoardBrowser,
    store: com.hardtekpt.crux.data.BrowseStore,
) : ViewModel() {
    val datasetId: Long = savedStateHandle.get<Long>("datasetId") ?: 0L

    /** The dataset's search, filters and sorts, as a board's: remembered, with views. */
    val browse = BrowseModel(viewModelScope, browser, store, playlists, preferences)

    val uiState: StateFlow<DatasetUiState> = combine(
        repository.observeDatasets(),
        preferences.gradeScales,
        playlists.observePlaylists(),
        playlists.observeSent(),
    ) {
            list,
            scales,
            lists,
            sent,
        ->
        DatasetUiState(isLoading = false, dataset = list.firstOrNull { it.id == datasetId }, datasets = list, scales = scales, playlists = lists, sent = sent)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatasetUiState())

    val importer = DatasetImport(context, repository, viewModelScope, playlists)

    /** "Added 3 to Tonight", for the snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    /** Adds the dataset's climbs [ids] to a playlist, or to a new one named [newName]. */
    fun addToPlaylist(playlistId: Long?, newName: String?, ids: Collection<Long>) {
        val dataset = uiState.value.dataset ?: return
        viewModelScope.launch {
            val id = playlistId ?: playlists.savePlaylist(0, newName.orEmpty(), null)
            val added = playlists.addClimbs(id, sourcesOf(dataset, ids))
            val name = playlists.observePlaylists().first().firstOrNull { it.id == id }?.name.orEmpty()
            _message.value = when {
                added == 0 -> "Already in $name"
                added == 1 -> "Added 1 climb to $name"
                else -> "Added $added climbs to $name"
            }
        }
    }

    /** The climb deleted last, to put back from the snackbar. */
    private val _undo = MutableStateFlow<DatasetClimb?>(null)
    val undo: StateFlow<DatasetClimb?> = _undo.asStateFlow()

    fun saveDetails(dataset: Dataset) {
        viewModelScope.launch { repository.saveDataset(dataset.copy(id = datasetId)) }
    }

    fun saveClimb(climb: DatasetClimb) {
        viewModelScope.launch { repository.saveClimb(datasetId, climb) }
    }

    fun deleteClimb(climb: DatasetClimb) {
        viewModelScope.launch {
            repository.deleteClimb(climb.id)
            _undo.value = climb
        }
    }

    fun undoDelete() {
        val climb = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { repository.restoreClimb(datasetId, climb) }
    }

    fun clearUndo() {
        _undo.value = null
    }

    fun deleteDataset(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteDataset(datasetId)
            onDone()
        }
    }

    /** Writes the dataset to [uri] as CSV or a Crux file. */
    fun export(uri: Uri, crux: Boolean) {
        val dataset = uiState.value.dataset ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = if (crux) DatasetFiles.writeCrux(dataset, Instant.ofEpochMilli(clock.millis()).toString()) else DatasetFiles.writeCsv(dataset)
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
        }
    }
}

/** Up to [n] bytes from the start of a stream. */
internal fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val buffer = ByteArray(n)
    var read = 0
    while (read < n) {
        val got = read(buffer, read, n - read)
        if (got < 0) break
        read += got
    }
    return buffer.copyOf(read)
}
