package com.hardtekpt.crux.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.data.seed.StarterData
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.navigation.swipeBetweenTabs
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TrainView(val label: String) { Week("Week"), Plans("Plans"), Exercises("Exercises") }

data class TrainUiState(
    val isLoading: Boolean = true,
    val plans: List<WorkoutTemplate> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    /** This week from the schedule, Monday first. */
    val week: List<WeekDay> = emptyList(),
    val today: java.time.LocalDate = java.time.LocalDate.now(),
    /** Places for a climbing day. */
    val places: List<com.hardtekpt.crux.data.model.PlaceSummary> = emptyList(),
) {
    /** Library grouped by category, categories in their defined order. */
    val exercisesByCategory: Map<ExerciseCategory, List<Exercise>>
        get() = exercises.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })
}

@HiltViewModel
class TrainViewModel @Inject constructor(
    templates: TemplateRepository,
    exercises: ExerciseRepository,
    private val starterData: StarterData,
    private val schedule: com.hardtekpt.crux.data.ScheduleRepository,
    sessions: com.hardtekpt.crux.data.SessionRepository,
    climbs: com.hardtekpt.crux.data.ClimbRepository,
    places: com.hardtekpt.crux.data.PlaceRepository,
    private val clock: java.time.Clock,
) : ViewModel() {
    /** Sets what a day of the week holds. */
    fun setDay(day: java.time.DayOfWeek, plan: com.hardtekpt.crux.data.DayPlan) {
        viewModelScope.launch { schedule.setDay(day, plan) }
    }

    /** Copies the starter exercises and plans into the active data set. */
    fun addStarterLibrary() {
        viewModelScope.launch { starterData.addStarterLibraryToCurrent() }
    }

    private val week = combine(
        schedule.observeWeek(),
        sessions.observeFinished(),
        climbs.observeClimbs(),
    ) { slots, finished, logged ->
        val today = java.time.LocalDate.now(clock)
        buildWeek(today, slots, finished, logged.map { it.date }.toSet(), clock.zone)
    }

    val uiState: StateFlow<TrainUiState> = combine(
        templates.observeTemplates(),
        exercises.observeExercises(),
        week,
        places.observePlaces(),
    ) { plans, library, days, placeList ->
        TrainUiState(isLoading = false, plans = plans, exercises = library, week = days, today = java.time.LocalDate.now(clock), places = placeList)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUiState())
}

@Composable
fun TrainScreen(
    onOpenPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onNewExercise: () -> Unit,
    viewModel: TrainViewModel = hiltViewModel(),
    initialView: TrainView = TrainView.Week,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(initialView) }
    var editingDay by rememberSaveable { mutableStateOf<java.time.DayOfWeek?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // Swipe sideways to move between Week, Plans and Exercises.
            .swipeBetweenTabs { direction -> TrainView.entries.getOrNull(view.ordinal + direction)?.let { view = it } }
            .testTag("screen_Train"),
    ) {
        CruxTopAppBar(title = "Train", scrollBehavior = scrollBehavior)
        CruxSegmentedButtons(
            options = TrainView.entries,
            selected = view,
            label = { it.label },
            onSelect = { view = it },
            modifier = Modifier.padding(horizontal = space.s4, vertical = space.s2),
        )
        LazyColumn(
            contentPadding = PaddingValues(
                start = space.s4,
                end = space.s4,
                top = space.s2,
                bottom = space.s4 + LocalNavBarClearance.current,
            ),
            verticalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.testTag("train_list"),
        ) {
            when (view) {
                TrainView.Week -> week(uiState) { day -> editingDay = day.date.dayOfWeek }
                TrainView.Plans -> plans(uiState, onOpenPlan, onNewPlan, viewModel::addStarterLibrary)
                TrainView.Exercises -> exercises(uiState, onOpenExercise, onNewExercise, viewModel::addStarterLibrary)
            }
        }
    }
    uiState.week.firstOrNull { it.date.dayOfWeek == editingDay }?.let { day ->
        DaySheet(
            day = day,
            plans = uiState.plans,
            places = uiState.places,
            onSave = { plan ->
                viewModel.setDay(day.date.dayOfWeek, plan)
                editingDay = null
            },
            onDismiss = { editingDay = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.plans(
    uiState: TrainUiState,
    onOpenPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    onAddStarters: () -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Eyebrow("Session plans · ${uiState.plans.size}", Modifier.weight(1f))
            CruxButton(
                text = "New plan",
                onClick = onNewPlan,
                variant = CruxButtonVariant.Tonal,
                icon = Icons.Rounded.Add,
                modifier = Modifier.testTag("new_plan"),
            )
        }
    }
    if (!uiState.isLoading && uiState.plans.isEmpty()) {
        item {
            InlineEmptyState(
                icon = Icons.Rounded.FitnessCenter,
                text = "No plans yet. Tap New plan and build one from your exercises.",
            )
        }
        item { StarterButton(onAddStarters) }
    }
    items(uiState.plans, key = { "plan_${it.id}" }) { plan ->
        CruxCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable { onOpenPlan(plan.id) }
                .testTag("template_card"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (plan.description.isNotBlank()) {
                Text(
                    plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${plan.blocks.size} blocks · ${plan.exerciseCount} exercises · ~${plan.estimatedMinutes} min",
                style = CruxTheme.type.code,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CruxTheme.space.s1),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.exercises(
    uiState: TrainUiState,
    onOpenExercise: (Long) -> Unit,
    onNewExercise: () -> Unit,
    onAddStarters: () -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Eyebrow("Library · ${uiState.exercises.size}", Modifier.weight(1f))
            CruxButton(
                text = "New exercise",
                onClick = onNewExercise,
                variant = CruxButtonVariant.Tonal,
                icon = Icons.Rounded.Add,
                modifier = Modifier.testTag("new_exercise"),
            )
        }
    }
    if (!uiState.isLoading && uiState.exercises.isEmpty()) {
        item {
            InlineEmptyState(
                icon = Icons.AutoMirrored.Rounded.List,
                text = "No exercises yet. Tap New exercise to start your library.",
            )
        }
        item { StarterButton(onAddStarters) }
    }
    uiState.exercisesByCategory.forEach { (category, exercises) ->
        item(key = "cat_${category.name}") {
            Eyebrow(category.label, Modifier.padding(top = CruxTheme.space.s2))
        }
        items(exercises, key = { "ex_${it.id}" }) { exercise ->
            // What plans and sessions start it at: "5 × 5 · +10 kg · 3 min rest", then any note.
            val imperial = com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL
            val defaults = com.hardtekpt.crux.data.model.ExerciseTarget.defaultFor(exercise)
            val params = listOfNotNull(
                defaults.prescription(exercise.metric, imperial),
                defaults.restLabel()?.let { if (exercise.metric.usesIntervals) "$it between cycles" else "$it rest" },
            ).joinToString(" · ")
            CruxListRow(
                title = exercise.name,
                supporting = listOfNotNull(params, exercise.notes).joinToString(" · "),
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onOpenExercise(exercise.id) },
                modifier = Modifier.testTag("exercise_row"),
            )
        }
    }
}

/** Offered on an empty library: 13 common exercises and 3 plans to start from. */
@Composable
private fun StarterButton(onClick: () -> Unit) {
    CruxButton(
        text = "Add starter exercises and plans",
        onClick = onClick,
        variant = CruxButtonVariant.Text,
        modifier = Modifier.testTag("add_starters"),
    )
}
