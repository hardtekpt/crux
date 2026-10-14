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
import kotlinx.coroutines.flow.flowOn
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
class DatasetImport(private val context: Context, private val repository: DatasetRepository, private val scope: kotlinx.coroutines.CoroutineScope) {
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
                    name to DatasetFiles.parse(name, text)
                }
            }
            read.onSuccess { (name, parsed) ->
                val base = name?.substringBefore('.')?.ifBlank { null } ?: "Imported"
                val dataset = parsed.dataset ?: Dataset(name = base)
                _draft.value = ImportDraft(fileName = name ?: "file", parsed = parsed, intoId = intoId, name = dataset.name, dataset = dataset)
                _error.value = null
            }.onFailure {
                _error.value = (it as? DatasetFiles.Unreadable)?.message ?: "This file can't be read. Pick a CSV or a Crux dataset file."
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

data class DatasetsUiState(val isLoading: Boolean = true, val datasets: List<Dataset> = emptyList(), val scales: GradeScales = GradeScales())

@HiltViewModel
class DatasetsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: DatasetRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<DatasetsUiState> = combine(repository.observeDatasets(), preferences.gradeScales) { list, scales ->
        DatasetsUiState(isLoading = false, datasets = list, scales = scales)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatasetsUiState())

    val importer = DatasetImport(context, repository, viewModelScope)

    /** Creates a dataset; then [onCreated] with its id. */
    fun create(dataset: Dataset, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.saveDataset(dataset.copy(id = 0))) }
    }
}

/** How a dataset's climbs are listed. */
enum class ClimbSort(val label: String) { GRADE("Grade"), NAME("Name"), NEWEST("Newest") }

data class DatasetUiState(
    val isLoading: Boolean = true,
    val dataset: Dataset? = null,
    val datasets: List<Dataset> = emptyList(),
    val scales: GradeScales = GradeScales(),
)

@HiltViewModel
class DatasetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: DatasetRepository,
    preferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    val datasetId: Long = savedStateHandle.get<Long>("datasetId") ?: 0L

    val uiState: StateFlow<DatasetUiState> = combine(repository.observeDatasets(), preferences.gradeScales) { list, scales ->
        DatasetUiState(isLoading = false, dataset = list.firstOrNull { it.id == datasetId }, datasets = list, scales = scales)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatasetUiState())

    val importer = DatasetImport(context, repository, viewModelScope)

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
