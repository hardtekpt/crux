package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ExerciseRecord
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.Note
import com.hardtekpt.crux.data.NoteRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.best
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.formatKg
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.input.DayStrip
import com.hardtekpt.crux.ui.components.input.DurationWheel
import com.hardtekpt.crux.ui.components.input.InputRow
import com.hardtekpt.crux.ui.components.input.LoadWheel
import com.hardtekpt.crux.ui.components.input.NumberWheel
import com.hardtekpt.crux.ui.components.input.PastDayDialog
import com.hardtekpt.crux.ui.components.input.formatDuration
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- Notes ---------------------------------------------------------------------------

@HiltViewModel
class NotesViewModel @Inject constructor(repository: NoteRepository) : ViewModel() {
    val notes: StateFlow<List<Note>?> = repository.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Every note, pinned first, then newest first. */
@Composable
fun NotesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit, viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val space = CruxTheme.space
    Column(Modifier.fillMaxSize().testTag("screen_Notes")) {
        CruxTopAppBar(
            title = "Notes",
            onBack = onBack,
            actions = { IconButton(onClick = onNew, modifier = Modifier.testTag("new_note")) { Icon(Icons.Rounded.Add, contentDescription = "New note") } },
        )
        val list = notes ?: return@Column
        if (list.isEmpty()) {
            EmptyState(icon = Icons.AutoMirrored.Rounded.Notes, headline = "No notes yet", sentence = "Add one with the + above or from the Log button.")
            return@Column
        }
        val tags = list.mapNotNull { it.tag }.distinct().sorted()
        var tag by rememberSaveable { mutableStateOf<String?>(null) }
        val shown = list.filter { tag == null || it.tag == tag }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.testTag("notes_list"),
        ) {
            if (tags.isNotEmpty()) {
                // Tapping the picked tag again shows every note.
                item(key = "tag_filters") {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        tags.forEach { t ->
                            CruxFilterChip(label = t, selected = tag == t, onClick = {
                                tag = if (tag ==
                                    t
                                ) {
                                    null
                                } else {
                                    t
                                }
                            }, modifier = Modifier.testTag("note_filter_$t"))
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { note -> NoteCard(note, onClick = { onOpen(note.id) }) }
        }
    }
}

data class NoteDraft(
    val id: Long = 0,
    val text: String = "",
    val pinned: Boolean = false,
    val tag: String? = null,
    val confirmDelete: Boolean = false,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: NoteRepository) : ViewModel() {
    private val noteId: Long = savedStateHandle.get<Long>("noteId") ?: 0L
    private val _draft = MutableStateFlow(NoteDraft(id = noteId))
    val draft: StateFlow<NoteDraft> = _draft.asStateFlow()

    /** Tags already used on other notes, offered as one-tap picks. */
    val knownTags: StateFlow<List<String>> = repository.observeNotes()
        .map { notes -> notes.mapNotNull { it.tag }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (noteId != 0L) {
            viewModelScope.launch {
                repository.getNote(noteId)?.let { note -> _draft.update { it.copy(text = note.text, pinned = note.pinned, tag = note.tag) } }
            }
        }
    }

    fun setText(text: String) = _draft.update { it.copy(text = text.take(MAX_NOTE)) }
    fun togglePin() = _draft.update { it.copy(pinned = !it.pinned) }
    fun setTag(tag: String?) = _draft.update { it.copy(tag = tag?.trim()?.lowercase()?.take(MAX_TAG)?.takeIf { t -> t.isNotEmpty() }) }

    fun save() {
        val d = _draft.value
        if (d.text.isBlank()) {
            _draft.update { it.copy(done = true) }
            return
        }
        viewModelScope.launch {
            repository.saveNote(d.id, d.text, d.pinned, d.tag)
            _draft.update { it.copy(done = true) }
        }
    }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            _draft.update { it.copy(confirmDelete = false, done = true) }
        }
    }

    companion object {
        const val MAX_NOTE = 4_000
        const val MAX_TAG = 24
    }
}

/** A plain page to write on: the first line becomes the note's title in lists. */
@Composable
fun NoteEditorScreen(onDone: () -> Unit, viewModel: NoteEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val knownTags by viewModel.knownTags.collectAsStateWithLifecycle()
    var typingTag by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(draft.done) { if (draft.done) onDone() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (draft.isNew) runCatching { focus.requestFocus() } }
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().imePadding().testTag("screen_NoteEditor")) {
        CruxTopAppBar(
            title = if (draft.isNew) "New note" else "Note",
            onBack = onDone,
            actions = {
                IconButton(onClick = viewModel::togglePin, modifier = Modifier.testTag("pin_note")) {
                    Icon(
                        if (draft.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (draft.pinned) "Unpin" else "Pin to the top",
                        tint = if (draft.pinned) colors.primary else colors.onSurfaceVariant,
                    )
                }
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_note")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete note")
                    }
                }
            },
        )
        // Optional tag: earlier tags as one-tap chips, or a new one typed in.
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.s2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = space.s4, vertical = space.s1)
                .testTag("note_tags"),
        ) {
            Icon(Icons.Rounded.Sell, contentDescription = "Tag", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            (listOfNotNull(draft.tag) + knownTags).distinct().forEach { t ->
                CruxFilterChip(
                    label = t,
                    selected = draft.tag == t,
                    onClick = { viewModel.setTag(if (draft.tag == t) null else t) },
                    modifier = Modifier.testTag("tag_$t"),
                )
            }
            CruxFilterChip(label = "New tag", selected = false, onClick = { typingTag = true }, modifier = Modifier.testTag("new_tag"))
        }
        OutlinedTextField(
            value = draft.text,
            onValueChange = viewModel::setText,
            placeholder = { Text("First line is the title…", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.surface,
                unfocusedBorderColor = colors.surface,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = space.s2)
                .focusRequester(focus)
                .testTag("field_note"),
        )
        CruxButton(
            text = "Save note",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            enabled = draft.text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_note"),
        )
    }
    if (typingTag) {
        var text by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { typingTag = false },
            containerColor = colors.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("New tag", style = MaterialTheme.typography.headlineSmall) },
            text = {
                CruxTextField(
                    label = "",
                    value = text,
                    onValueChange = { text = it.take(NoteEditorViewModel.MAX_TAG) },
                    placeholder = "injury, beta, training…",
                    modifier = Modifier.testTag("field_tag"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTag(text)
                        typingTag = false
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.testTag("confirm_tag"),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { typingTag = false }) { Text("Cancel") } },
        )
    }
    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = colors.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete this note?", style = MaterialTheme.typography.headlineSmall) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) { Text("Delete", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

// ---- Personal records ----------------------------------------------------------------

data class RecordDraft(
    val exerciseId: Long = 0,
    val date: LocalDate,
    val today: LocalDate,
    val reps: Int = 5,
    val seconds: Int = 10,
    val loadKg: Double = 0.0,
    val notes: String = "",
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class RecordEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exercises: ExerciseRepository,
    private val records: RecordRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val _draft = MutableStateFlow(RecordDraft(exerciseId = savedStateHandle.get<Long>("exerciseId") ?: 0L, date = today, today = today))
    val draft: StateFlow<RecordDraft> = _draft.asStateFlow()
    val exercises: StateFlow<List<Exercise>> = exercises.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(change: (RecordDraft) -> RecordDraft) = _draft.update { change(it).copy(error = null) }

    fun save(exercise: Exercise?) {
        val d = _draft.value
        if (exercise == null) {
            _draft.update { it.copy(error = "Pick an exercise") }
            return
        }
        val m = exercise.metric
        viewModelScope.launch {
            records.addRecord(
                exerciseId = exercise.id,
                date = d.date,
                reps = d.reps.takeIf { m.usesReps || m.usesIntervals },
                seconds = d.seconds.takeIf { m.usesTime },
                loadKg = d.loadKg.takeIf { m.usesLoad },
                notes = d.notes,
            )
            _draft.update { it.copy(done = true) }
        }
    }
}

/** Log a result: pick the exercise, then only the numbers its metric uses. */
@Composable
fun RecordEditorScreen(onDone: () -> Unit, viewModel: RecordEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    LaunchedEffect(draft.done) { if (draft.done) onDone() }
    val exercise = exercises.firstOrNull { it.id == draft.exerciseId }
    var open by rememberSaveable { mutableStateOf<String?>(if (draft.exerciseId == 0L) "exercise" else null) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    val toggle = { key: String -> open = if (open == key) null else key }
    val space = CruxTheme.space

    Column(Modifier.fillMaxSize().imePadding().testTag("screen_RecordEditor")) {
        CruxTopAppBar(title = "Log a result", onBack = onDone)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InputRow("Exercise", exercise?.name ?: "Pick one", open == "exercise", { toggle("exercise") }, testTag = "record_exercise") {
                if (exercises.isEmpty()) {
                    Text("Your exercise library is empty. Add exercises in Train › Exercises.", style = MaterialTheme.typography.bodyMedium)
                }
                exercises.forEach { ex ->
                    CruxListRow(
                        title = ex.name,
                        supporting = ex.metric.label,
                        selected = ex.id == draft.exerciseId,
                        onClick = {
                            viewModel.update { it.copy(exerciseId = ex.id) }
                            open = null
                        },
                        modifier = Modifier.testTag("record_pick_${ex.name}"),
                    )
                }
            }
            if (exercise != null) {
                val m = exercise.metric
                if (m.usesLoad) {
                    InputRow(
                        "Added load",
                        (
                            if (draft.loadKg > 0) {
                                "+"
                            } else if (draft.loadKg < 0) {
                                "−"
                            } else {
                                ""
                            }
                            ) +
                            com.hardtekpt.crux.data.model.formatLoad(
                                draft.loadKg,
                                com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL,
                            ),
                        open == "load",
                        { toggle("load") },
                        unit = com.hardtekpt.crux.data.model.loadUnit(
                            com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL,
                        ),
                        testTag = "record_load",
                    ) { LoadWheel(draft.loadKg, { v -> viewModel.update { it.copy(loadKg = v) } }) }
                }
                if (m.usesReps || m.usesIntervals) {
                    InputRow(if (m.usesIntervals) "Repeats" else "Reps", draft.reps.toString(), open == "reps", {
                        toggle("reps")
                    }, unit = "reps", testTag = "record_reps") {
                        NumberWheel(draft.reps, { v -> viewModel.update { it.copy(reps = v) } }, 1..100, "reps", "Reps")
                    }
                }
                if (m.usesTime) {
                    InputRow("Time", formatDuration(draft.seconds), open == "time", { toggle("time") }, mono = true, testTag = "record_time") {
                        DurationWheel(draft.seconds, { v ->
                            viewModel.update { it.copy(seconds = v) }
                        }, "Time", maxMinutes = 30, minSeconds = 1, presets = listOf(7, 10, 15, 30, 60, 120))
                    }
                }
            }
            Eyebrow("Day · ${draft.date.dayLabel()}", Modifier.padding(top = space.s3))
            DayStrip(draft.date, draft.today, { d -> viewModel.update { it.copy(date = d) } }, { pickingDay = true })
            CruxTextField(
                label = "Notes",
                value = draft.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v.take(200)) } },
                placeholder = "Edge, grip, how it felt",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.padding(top = space.s3),
            )
            draft.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        CruxButton(
            text = "Save result",
            onClick = { viewModel.save(exercise) },
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_record"),
        )
    }
    if (pickingDay) PastDayDialog(draft.date, draft.today, { d -> viewModel.update { it.copy(date = d) } }) { pickingDay = false }
}

@HiltViewModel
class ExerciseRecordsViewModel @Inject constructor(savedStateHandle: SavedStateHandle, exercises: ExerciseRepository, private val records: RecordRepository) :
    ViewModel() {
    val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: 0L
    val state: StateFlow<Pair<Exercise?, List<ExerciseRecord>>> =
        combine(exercises.observeExercises(), records.observeRecords(exerciseId)) { list, mine -> list.firstOrNull { it.id == exerciseId } to mine }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { records.deleteRecord(id) }
    }
}

/** One exercise's results, newest first, with its PR on top. */
@Composable
fun ExerciseRecordsScreen(onBack: () -> Unit, onAdd: (Long) -> Unit, viewModel: ExerciseRecordsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (exercise, results) = state
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().testTag("screen_ExerciseRecords")) {
        CruxTopAppBar(title = exercise?.name.orEmpty(), onBack = onBack)
        val ex = exercise ?: return@Column
        val pr = ex.metric.best(results)
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = colors.secondary)
                    Column(Modifier.weight(1f)) {
                        Text("PERSONAL RECORD", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Text(
                            pr?.describe(ex.metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL) ?: "–",
                            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                            modifier = Modifier.testTag("exercise_pr"),
                        )
                        pr?.let { Text("Set ${it.date.dayLabel()}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                }
            }
            item {
                CruxButton("Log a result", {
                    onAdd(ex.id)
                }, icon = Icons.Rounded.Add, variant = CruxButtonVariant.Tonal, modifier = Modifier.testTag("add_record"))
            }
            item { Eyebrow("All results · ${results.size}", Modifier.padding(top = space.s3)) }
            items(results, key = { it.id }) { record ->
                CruxListRow(
                    title = record.describe(ex.metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL),
                    supporting = listOfNotNull(record.date.dayLabel(), record.notes, "PR".takeIf { record.id == pr?.id }).joinToString(" · "),
                    trailing = {
                        IconButton(onClick = { viewModel.delete(record.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete result") }
                    },
                    selected = record.id == pr?.id,
                    modifier = Modifier.heightIn(min = 56.dp).testTag("record_result"),
                )
            }
        }
    }
}
