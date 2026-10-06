package com.hardtekpt.crux.ui.places

import com.hardtekpt.crux.data.model.GradeSystem
import com.hardtekpt.crux.data.model.LocalGrade
import com.hardtekpt.crux.data.model.LocalKind
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.input.TapeSwatch
import com.hardtekpt.crux.ui.components.input.argb
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.ProblemInput
import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.input.GradeStrip
import com.hardtekpt.crux.ui.components.input.bleed
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_NAME = 40

// ---- Place editor --------------------------------------------------------------------

data class PlaceDraft(
    val id: Long = 0,
    val name: String = "",
    val type: PlaceType = PlaceType.GYM,
    val location: String = "",
    /** Null = use my settings. */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    val defaultAngle: Int = 40,
    val notes: String = "",
    /** The place's own grades; kept while editing even if no discipline uses them. */
    val localScale: LocalScale = LocalScale.DEFAULT_COLOURS,
    val localError: String? = null,
    val favourite: Boolean = false,
    val nameError: String? = null,
    val confirmDelete: Boolean = false,
    /** Set once saved or deleted: the id to open, or 0 after a delete. */
    val doneId: Long? = null,
) {
    val isNew: Boolean get() = id == 0L
    val usesLocal: Boolean get() = boulderScale?.isLocal == true || (type != PlaceType.BOARD && routeScale?.isLocal == true)
}

@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaceRepository,
) : ViewModel() {
    private val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val _draft = MutableStateFlow(PlaceDraft(id = placeId))
    val draft: StateFlow<PlaceDraft> = _draft.asStateFlow()

    init {
        if (placeId != 0L) {
            viewModelScope.launch {
                repository.getPlace(placeId)?.let { p ->
                    _draft.update {
                        it.copy(
                            name = p.name, type = p.type, location = p.location.orEmpty(),
                            boulderScale = p.boulderScale, routeScale = p.routeScale,
                            defaultAngle = p.defaultAngle ?: 40, notes = p.notes.orEmpty(),
                            localScale = p.localScale ?: LocalScale.DEFAULT_COLOURS,
                            favourite = p.favourite,
                        )
                    }
                }
            }
        }
    }

    fun update(change: (PlaceDraft) -> PlaceDraft) = _draft.update { change(it).copy(nameError = null, localError = null) }

    fun save() {
        val d = _draft.value
        val error = when {
            d.name.isBlank() -> "Give the place a name"
            d.name.trim().length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            else -> null
        }
        val local = d.localScale.copy(grades = d.localScale.grades.map { it.copy(name = it.name.trim()) })
        val localError = when {
            !d.usesLocal -> null
            local.grades.size < 2 -> "A local scale needs at least two grades"
            local.grades.any { it.name.isBlank() } -> "Name every grade"
            local.grades.map { it.name.lowercase() }.toSet().size < local.grades.size -> "Each grade needs a different name"
            else -> null
        }
        if (error != null || localError != null) {
            _draft.update { it.copy(nameError = error, localError = localError) }
            return
        }
        viewModelScope.launch {
            val id = repository.savePlace(
                PlaceInput(
                    id = d.id,
                    name = d.name,
                    type = d.type,
                    location = d.location,
                    boulderScale = d.boulderScale,
                    routeScale = d.routeScale,
                    defaultAngle = d.defaultAngle.takeIf { d.type == PlaceType.BOARD },
                    notes = d.notes,
                    localScale = local.takeIf { d.usesLocal },
                    favourite = d.favourite,
                ),
            )
            _draft.update { it.copy(doneId = id) }
        }
    }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        viewModelScope.launch {
            repository.deletePlace(placeId)
            _draft.update { it.copy(confirmDelete = false, doneId = 0) }
        }
    }
}

/**
 * Create or edit a gym, crag or board. [onSaved] gets the place id, or 0 when it was deleted.
 */
@Composable
fun PlaceEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: PlaceEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft.doneId) { draft.doneId?.let(onSaved) }
    val space = CruxTheme.space

    Column(Modifier.fillMaxSize().imePadding().testTag("screen_PlaceEditor")) {
        CruxTopAppBar(
            title = if (draft.isNew) "New place" else "Edit place",
            onBack = onBack,
            actions = {
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_place")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete place")
                    }
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            CruxSegmentedButtons(PlaceType.entries, draft.type, { it.label }, { t -> viewModel.update { it.copy(type = t) } })
            CruxTextField(
                label = "Name",
                value = draft.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                placeholder = when (draft.type) {
                    PlaceType.GYM -> "Block Lab"
                    PlaceType.CRAG -> "Arco"
                    PlaceType.BOARD -> "Home Kilter"
                },
                error = draft.nameError,
                modifier = Modifier.testTag("field_place_name"),
            )
            CruxTextField(
                label = if (draft.type == PlaceType.BOARD) "Where it is" else "City or area",
                value = draft.location,
                onValueChange = { v -> viewModel.update { it.copy(location = v.take(MAX_NAME)) } },
                placeholder = if (draft.type == PlaceType.BOARD) "Home" else "Lisbon",
                helper = "Optional",
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Favourite", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Shown as a quick pick when you log a climb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = draft.favourite,
                    onCheckedChange = { f -> viewModel.update { it.copy(favourite = f) } },
                    modifier = Modifier.testTag("place_favourite"),
                )
            }
            Eyebrow("Grades here")
            ScaleChoice("Boulders", Discipline.BOULDER, draft.boulderScale) { s -> viewModel.update { it.copy(boulderScale = s) } }
            if (draft.type != PlaceType.BOARD) {
                ScaleChoice("Routes", Discipline.ROUTE, draft.routeScale) { s -> viewModel.update { it.copy(routeScale = s) } }
            }
            if (draft.usesLocal) {
                LocalScaleEditor(
                    scale = draft.localScale,
                    error = draft.localError,
                    onChange = { scale -> viewModel.update { it.copy(localScale = scale) } },
                )
            }
            if (draft.type == PlaceType.BOARD) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Usual angle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    CruxStepper(draft.defaultAngle, { a -> viewModel.update { it.copy(defaultAngle = a) } }, 0..70, "deg", step = 5)
                }
            }
            CruxTextField(
                label = "Notes",
                value = draft.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                placeholder = "Opening hours, parking, conditions",
                helper = "Optional",
                singleLine = false,
                minLines = 2,
            )
        }
        CruxButton(
            text = if (draft.isNew) "Add place" else "Save place",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_place"),
        )
    }

    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${draft.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Its walls and problems go. Climbs you logged here stay in your journal under the same name.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

/** "Use my settings", one of the discipline's scales, or the place's own local grades. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleChoice(label: String, discipline: Discipline, selected: GradeScale?, onSelect: (GradeScale?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            CruxFilterChip("My settings", selected == null, { onSelect(null) })
            discipline.scales.forEach { scale ->
                CruxFilterChip(scale.label, selected == scale, { onSelect(scale) })
            }
            CruxFilterChip(
                "Local",
                selected == discipline.localScale,
                { onSelect(discipline.localScale) },
                modifier = Modifier.testTag("local_${discipline.name}"),
            )
        }
    }
}

/**
 * The place's own grades, easiest first: a run of numbers with a range, or a list of named
 * tape colours that can be recoloured, renamed, reordered, added and removed.
 */
@Composable
private fun LocalScaleEditor(scale: LocalScale, error: String?, onChange: (LocalScale) -> Unit) {
    val space = CruxTheme.space
    var pickingColourFor by remember { mutableStateOf<Int?>(null) }
    CruxCard(modifier = Modifier.testTag("local_scale_editor")) {
        Text("Local grades", style = MaterialTheme.typography.titleMedium)
        Text(
            "Easiest first. Climbs here keep these grades; they are never converted to Font or French.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = space.s2),
        )
        CruxSegmentedButtons(
            options = LocalKind.entries,
            selected = scale.kind,
            label = { it.label },
            onSelect = { kind ->
                if (kind != scale.kind) onChange(if (kind == LocalKind.NUMBERS) LocalScale.DEFAULT_NUMBERS else LocalScale.DEFAULT_COLOURS)
            },
        )
        when (scale.kind) {
            LocalKind.NUMBERS -> {
                val from = scale.grades.firstOrNull()?.name?.toIntOrNull() ?: 1
                val to = scale.grades.lastOrNull()?.name?.toIntOrNull() ?: 10
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = space.s3)) {
                    Text("Easiest", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    CruxStepper(from, { onChange(LocalScale.numbers(it, maxOf(to, it + 1))) }, 0..49, "from", testTagPrefix = "local_from")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = space.s2)) {
                    Text("Hardest", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    CruxStepper(to, { onChange(LocalScale.numbers(from, it)) }, (from + 1)..50, "to", testTagPrefix = "local_to")
                }
                Text(
                    scale.labels.joinToString("  "),
                    style = CruxTheme.type.code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = space.s2),
                )
            }
            LocalKind.COLOURS -> {
                Column(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.padding(top = space.s3)) {
                    scale.grades.forEachIndexed { index, grade ->
                        val set = { next: LocalGrade -> onChange(scale.copy(grades = scale.grades.toMutableList().also { it[index] = next })) }
                        val move = { by: Int ->
                            val list = scale.grades.toMutableList()
                            list.add(index + by, list.removeAt(index))
                            onChange(scale.copy(grades = list))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s1)) {
                            Text("${index + 1}", style = CruxTheme.type.gradeSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(onClickLabel = "Change colour") { pickingColourFor = index }
                                    .testTag("local_colour_$index"),
                            ) { TapeSwatch(argb(grade.colour ?: 0xFF9E9E9EL), 28.dp) }
                            OutlinedTextField(
                                value = grade.name,
                                onValueChange = { set(grade.copy(name = it.take(16))) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("local_name_$index"),
                            )
                            IconButton(onClick = { move(-1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Easier")
                            }
                            IconButton(onClick = { move(1) }, enabled = index < scale.grades.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Harder")
                            }
                            IconButton(onClick = { onChange(scale.copy(grades = scale.grades.filterIndexed { i, _ -> i != index })) }, enabled = scale.grades.size > 2) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove ${grade.name}")
                            }
                        }
                    }
                    CruxButton(
                        text = "Add a colour",
                        onClick = {
                            val unused = LocalScale.PALETTE.firstOrNull { (_, c) -> scale.grades.none { it.colour == c } } ?: LocalScale.PALETTE.last()
                            onChange(scale.copy(grades = scale.grades + LocalGrade(unused.first, unused.second)))
                        },
                        variant = CruxButtonVariant.Text,
                        icon = Icons.Rounded.Add,
                        enabled = scale.grades.size < 20,
                        modifier = Modifier.testTag("local_add_colour"),
                    )
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    pickingColourFor?.let { index ->
        AlertDialog(
            onDismissRequest = { pickingColourFor = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Tape colour", style = MaterialTheme.typography.headlineSmall) },
            text = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                    LocalScale.PALETTE.forEach { (name, colour) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    val grade = scale.grades[index]
                                    // A grade still named after its old colour takes the new name too.
                                    val renamed = if (LocalScale.PALETTE.any { it.first == grade.name } || grade.name.isBlank()) name else grade.name
                                    onChange(scale.copy(grades = scale.grades.toMutableList().also { it[index] = LocalGrade(renamed, colour) }))
                                    pickingColourFor = null
                                }
                                .padding(CruxTheme.space.s1),
                        ) {
                            TapeSwatch(argb(colour), 32.dp)
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingColourFor = null }) { Text("Cancel") } },
        )
    }
}

// ---- Problem editor ------------------------------------------------------------------

data class ProblemDraft(
    val id: Long = 0,
    val placeId: Long,
    val areaId: Long? = null,
    val name: String = "",
    val discipline: Discipline = Discipline.BOULDER,
    val gradeScale: GradeScale = GradeScale.FONT,
    val gradeIndex: Int = GradeScale.FONT.defaultIndex,
    /** The place's local grades, when [gradeScale] is local. */
    val local: LocalScale? = null,
    val tape: Int? = null,
    val notes: String = "",
    val retired: Boolean = false,
    val nameError: String? = null,
    val confirmDelete: Boolean = false,
    val done: Boolean = false,
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val system: GradeSystem get() = GradeSystem(gradeScale, local.takeIf { gradeScale.isLocal })
}

@HiltViewModel
class ProblemEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaceRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    private val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val problemId: Long = savedStateHandle.get<Long>("problemId") ?: 0L
    private var settings = GradeScales()
    private val _draft = MutableStateFlow(ProblemDraft(id = problemId, placeId = placeId))
    val draft: StateFlow<ProblemDraft> = _draft.asStateFlow()

    val areas: StateFlow<List<Area>> = repository.observePlaceDetail(placeId).map { it?.areas.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val placeType: StateFlow<PlaceType?> = repository.observePlaceDetail(placeId).map { it?.place?.type }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            settings = preferences.gradeScales.first()
            val local = repository.getPlace(placeId)?.localScale
            _draft.update { it.copy(local = local) }
            val problem = problemId.takeIf { it != 0L }?.let { repository.getProblem(it) }
            if (problem != null) {
                _draft.update {
                    it.copy(
                        placeId = problem.placeId, areaId = problem.areaId, name = problem.name,
                        discipline = problem.discipline, gradeScale = problem.gradeScale, gradeIndex = problem.gradeIndex,
                        tape = problem.tape, notes = problem.notes.orEmpty(), retired = problem.retired,
                    )
                }
            } else {
                val scale = scaleFor(Discipline.BOULDER)
                _draft.update { it.copy(gradeScale = scale).let { d -> d.copy(gradeIndex = d.system.defaultIndex) } }
            }
        }
    }

    /** The place's scale for a discipline, else the climber's setting. */
    private suspend fun scaleFor(discipline: Discipline): GradeScale =
        repository.getPlace(placeId)?.scaleFor(discipline) ?: settings.forDiscipline(discipline)

    fun setDiscipline(discipline: Discipline) {
        viewModelScope.launch {
            val scale = scaleFor(discipline)
            _draft.update { it.copy(discipline = discipline, gradeScale = scale).let { d -> d.copy(gradeIndex = d.system.defaultIndex) } }
        }
    }

    fun update(change: (ProblemDraft) -> ProblemDraft) = _draft.update { change(it).copy(nameError = null) }

    fun save() {
        val d = _draft.value
        val error = when {
            d.name.isBlank() -> "Give it a name, even just the colour and wall"
            d.name.trim().length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            else -> null
        }
        if (error != null) {
            _draft.update { it.copy(nameError = error) }
            return
        }
        viewModelScope.launch {
            val id = repository.saveProblem(
                ProblemInput(
                    id = d.id, placeId = d.placeId, areaId = d.areaId, name = d.name, discipline = d.discipline,
                    gradeScale = d.gradeScale, gradeIndex = d.gradeIndex, tape = d.tape, notes = d.notes,
                    gradeLabel = d.system.label(d.gradeIndex), gradeColour = d.system.colour(d.gradeIndex),
                ),
            )
            if (!d.isNew) repository.setRetired(id, d.retired)
            _draft.update { it.copy(done = true) }
        }
    }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        viewModelScope.launch {
            repository.deleteProblem(problemId)
            _draft.update { it.copy(confirmDelete = false, done = true, deleted = true) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProblemEditorScreen(
    onDone: (deleted: Boolean) -> Unit,
    viewModel: ProblemEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val type by viewModel.placeType.collectAsStateWithLifecycle()
    LaunchedEffect(draft.done) { if (draft.done) onDone(draft.deleted) }
    val space = CruxTheme.space
    val noun = if (draft.discipline == Discipline.ROUTE) "route" else "problem"

    Column(Modifier.fillMaxSize().imePadding().testTag("screen_ProblemEditor")) {
        CruxTopAppBar(
            title = if (draft.isNew) "New $noun" else "Edit $noun",
            onBack = { onDone(false) },
            actions = {
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_problem")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete $noun")
                    }
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (type != PlaceType.BOARD) {
                CruxSegmentedButtons(Discipline.entries, draft.discipline, { it.label }, viewModel::setDiscipline)
            }
            CruxTextField(
                label = "Name",
                value = draft.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v.take(MAX_NAME)) } },
                placeholder = "Yellow dyno",
                error = draft.nameError,
                modifier = Modifier.testTag("field_problem_name"),
            )
            Eyebrow("Grade · ${draft.system.name}")
            GradeStrip(
                grades = draft.system.labels,
                colours = draft.system.local?.grades?.map { it.colour },
                selectedIndex = draft.gradeIndex,
                onSelect = { index -> viewModel.update { it.copy(gradeIndex = index) } },
                tagPrefix = "problem_grade",
                modifier = Modifier.bleed(space.s4),
            )
            if (areas.isNotEmpty()) {
                Eyebrow(type?.areaLabel ?: "Wall")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    CruxFilterChip("None", draft.areaId == null, { viewModel.update { it.copy(areaId = null) } })
                    areas.forEach { area ->
                        CruxFilterChip(area.name, draft.areaId == area.id, { viewModel.update { it.copy(areaId = area.id) } })
                    }
                }
            }
            Eyebrow("Tape colour")
            TapePicker(draft.tape) { t -> viewModel.update { it.copy(tape = t) } }
            CruxTextField(
                label = "Notes",
                value = draft.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                placeholder = "Beta, start holds, who set it",
                helper = "Optional",
                singleLine = false,
                minLines = 2,
            )
            if (!draft.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Retired (taken down)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = draft.retired, onCheckedChange = { r -> viewModel.update { it.copy(retired = r) } })
                }
            }
        }
        CruxButton(
            text = if (draft.isNew) "Add $noun" else "Save $noun",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_problem"),
        )
    }

    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${draft.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Your logged goes on it stay in the journal.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

private val TAPE_NAMES = listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple")

/** The gym's route tape, as swatches; tap again to clear. */
@Composable
private fun TapePicker(selected: Int?, onSelect: (Int?) -> Unit) {
    val tape = CruxTheme.colors.tape
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        tape.forEachIndexed { index, color ->
            val isSelected = selected == index
            Box(
                Modifier
                    .size(36.dp)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .padding(4.dp)
                    .border(18.dp, color, CircleShape)
                    .clickable { onSelect(if (isSelected) null else index) }
                    .testTag("tape_${TAPE_NAMES[index]}"),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = TAPE_NAMES[index], modifier = Modifier.size(16.dp))
            }
        }
    }
}
