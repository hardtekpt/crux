package com.hardtekpt.crux.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hardtekpt.crux.data.ExerciseInput
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.IntervalSettings
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.data.model.formatLoad
import com.hardtekpt.crux.data.model.loadUnit
import com.hardtekpt.crux.data.model.loadValue
import com.hardtekpt.crux.data.model.poundsToKg
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.CruxValueStepper
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.ExerciseEditorRoute
import com.hardtekpt.crux.ui.session.IntervalFields
import com.hardtekpt.crux.ui.session.toSpec
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseDraft(
    val id: Long = 0,
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.FINGERS,
    val metric: MetricType = MetricType.REPS,
    val notes: String = "",
    /** What plans and sessions start the exercise at. */
    val defaults: ExerciseTarget = ExerciseTarget.defaultFor(metric),
    /** Interval exercises: the timer's preparation. */
    val prepSeconds: Int = 10,
    val nameError: String? = null,
    /** Set when the climber asks to delete: how many plans would lose this exercise. */
    val confirmDeleteInPlans: Int? = null,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
}

@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: ExerciseRepository) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseEditorRoute>().exerciseId

    private val _draft = MutableStateFlow(ExerciseDraft(id = exerciseId))
    val draft: StateFlow<ExerciseDraft> = _draft.asStateFlow()

    init {
        if (exerciseId != 0L) {
            viewModelScope.launch {
                repository.getExercise(exerciseId)?.let { exercise ->
                    _draft.update {
                        it.copy(
                            name = exercise.name,
                            category = exercise.category,
                            metric = exercise.metric,
                            notes = exercise.notes.orEmpty(),
                            defaults = ExerciseTarget.defaultFor(exercise),
                            prepSeconds = exercise.prepSeconds ?: 10,
                        )
                    }
                }
            }
        }
    }

    fun setName(name: String) = _draft.update { it.copy(name = name, nameError = null) }
    fun setCategory(category: ExerciseCategory) = _draft.update { it.copy(category = category) }

    /** A new way of measuring starts from that way's defaults. */
    fun setMetric(metric: MetricType) = _draft.update {
        if (it.metric == metric) it else it.copy(metric = metric, defaults = ExerciseTarget.defaultFor(metric))
    }
    fun setDefaults(defaults: ExerciseTarget) = _draft.update { it.copy(defaults = defaults) }
    fun setIntervals(intervals: IntervalSettings) = _draft.update {
        it.copy(defaults = intervals.toTarget(it.defaults.loadKg), prepSeconds = intervals.prepSeconds)
    }
    fun setNotes(notes: String) = _draft.update { it.copy(notes = notes) }

    fun save() {
        val draft = _draft.value
        val name = draft.name.trim()
        val error = when {
            name.isEmpty() -> "Give the exercise a name"
            name.length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            else -> null
        }
        if (error != null) {
            _draft.update { it.copy(nameError = error) }
            return
        }
        viewModelScope.launch {
            repository.saveExercise(
                ExerciseInput(
                    id = draft.id,
                    name = name,
                    category = draft.category,
                    metric = draft.metric,
                    notes = draft.notes,
                    defaults = draft.defaults,
                    prepSeconds = draft.prepSeconds,
                ),
            )
            _draft.update { it.copy(done = true) }
        }
    }

    fun requestDelete() {
        viewModelScope.launch {
            val plans = repository.planCount(exerciseId)
            _draft.update { it.copy(confirmDeleteInPlans = plans) }
        }
    }

    fun cancelDelete() = _draft.update { it.copy(confirmDeleteInPlans = null) }

    fun confirmDelete() {
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
            _draft.update { it.copy(confirmDeleteInPlans = null, done = true) }
        }
    }

    companion object {
        const val MAX_NAME = 40
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(onDone: () -> Unit, viewModel: ExerciseEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft.done) { if (draft.done) onDone() }
    val space = CruxTheme.space

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("screen_ExerciseEditor"),
    ) {
        CruxTopAppBar(
            title = if (draft.isNew) "New exercise" else "Edit exercise",
            onBack = onDone,
            actions = {
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_exercise")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete exercise")
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            CruxTextField(
                label = "Name",
                value = draft.name,
                onValueChange = viewModel::setName,
                placeholder = "Half-crimp hang",
                error = draft.nameError,
                modifier = Modifier.testTag("field_exercise_name"),
            )
            Eyebrow("Category")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(space.s2),
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                ExerciseCategory.entries.forEach { category ->
                    CruxFilterChip(
                        label = category.label,
                        selected = draft.category == category,
                        onClick = { viewModel.setCategory(category) },
                        modifier = Modifier.testTag("category_${category.name}"),
                    )
                }
            }
            Eyebrow("Measured by", Modifier.padding(top = space.s3))
            // More than four types, so chips rather than segmented buttons.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(space.s2),
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                MetricType.entries.forEach { metric ->
                    CruxFilterChip(
                        label = metric.label,
                        selected = draft.metric == metric,
                        onClick = { viewModel.setMetric(metric) },
                        modifier = Modifier.testTag("metric_${metric.name}"),
                    )
                }
            }
            Text(
                metricHelp(draft.metric),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // What plans and sessions start it at.
            Eyebrow(if (draft.metric.usesIntervals) "Interval timer" else "Defaults", Modifier.padding(top = space.s3))
            Column(Modifier.testTag("exercise_defaults")) {
                if (draft.metric.usesIntervals) {
                    IntervalFields(draft.defaults.toIntervals(draft.prepSeconds), viewModel::setIntervals)
                } else {
                    TargetFields(draft.metric, draft.defaults, viewModel::setDefaults)
                }
                if (draft.metric.usesLoad) {
                    LoadRow(draft.defaults.loadKg) { viewModel.setDefaults(draft.defaults.copy(loadKg = it)) }
                }
            }
            Text(
                if (draft.metric.usesIntervals) {
                    "Plans start from these, and the session timer runs them. Takes " +
                        formatDuration(draft.defaults.toIntervals(draft.prepSeconds).toSpec().totalSeconds) + "."
                } else {
                    "Plans and sessions start from these; change them there for a given day."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CruxTextField(
                label = "Notes",
                value = draft.notes,
                onValueChange = viewModel::setNotes,
                placeholder = "Edge size, grip, cues",
                helper = "Optional",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.padding(top = space.s3),
            )
        }
        CruxButton(
            text = if (draft.isNew) "Add exercise" else "Save exercise",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_exercise"),
        )
    }

    draft.confirmDeleteInPlans?.let { plans ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${draft.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    when (plans) {
                        0 -> "It is not used in any plan."
                        1 -> "It is removed from the 1 plan that uses it."
                        else -> "It is removed from the $plans plans that use it."
                    },
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

private fun metricHelp(metric: MetricType): String = when (metric) {
    MetricType.REPS -> "Plans set sets and reps. Pull-ups, push-ups, problems."
    MetricType.WEIGHTED_REPS -> "Plans set sets, reps and added load. Weighted pull-ups, dips."
    MetricType.TIME -> "Plans set sets and seconds. Lever holds, traversing, stretches."
    MetricType.WEIGHTED_TIME -> "Plans set sets, seconds and added load. Hangboard hangs."
    MetricType.INTERVALS -> "Timed work and rest, repeated in cycles. Tabata, circuits."
    MetricType.WEIGHTED_INTERVALS -> "Intervals with added load. Hangboard repeaters."
}

/** Sets, reps or time, and rest, for exercises counted in sets. */
@Composable
private fun TargetFields(metric: MetricType, target: ExerciseTarget, onChange: (ExerciseTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        DefaultRow("Sets") { CruxStepper(target.sets, { onChange(target.copy(sets = it)) }, 1..30, "", testTagPrefix = "default_sets") }
        if (metric.usesReps) {
            DefaultRow("Reps", "each set") { CruxStepper(target.reps, { onChange(target.copy(reps = it)) }, 1..200, "", testTagPrefix = "default_reps") }
        }
        if (metric.usesTime) {
            DefaultRow("Time", "each set") {
                CruxStepper(target.seconds, { onChange(target.copy(seconds = it)) }, 1..3600, "s", step = 5, testTagPrefix = "default_seconds")
            }
        }
        DefaultRow("Rest", "between sets") {
            CruxStepper(target.restSeconds, { onChange(target.copy(restSeconds = it)) }, 0..900, "s", step = 15, testTagPrefix = "default_rest")
        }
    }
}

/** Added load, in the climber's units; negative is assisted. */
@Composable
private fun LoadRow(loadKg: Double, onChange: (Double) -> Unit) {
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL
    val step = if (imperial) poundsToKg(2.5) else 1.25
    val shown = loadValue(loadKg, imperial)
    Column(Modifier.padding(top = CruxTheme.space.s2)) {
        DefaultRow("Added load", "negative is assisted") {
            CruxValueStepper(
                display = (
                    if (loadKg > 0) {
                        "+"
                    } else if (loadKg < 0) {
                        "−"
                    } else {
                        ""
                    }
                    ) + formatLoad(loadKg, imperial),
                unit = loadUnit(imperial),
                canDecrease = shown > -100,
                canIncrease = shown < 300,
                onDecrease = { onChange(loadKg - step) },
                onIncrease = { onChange(loadKg + step) },
                testTagPrefix = "default_load",
            )
        }
    }
}

@Composable
private fun DefaultRow(label: String, hint: String? = null, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        field()
    }
}
