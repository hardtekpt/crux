package com.hardtekpt.crux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.dashboard.DashboardRepository
import com.hardtekpt.crux.data.dashboard.DashboardWidget
import com.hardtekpt.crux.data.dashboard.WidgetType
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.Project
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.WeightSummary
import com.hardtekpt.crux.ui.progress.ProgressCharts
import com.hardtekpt.crux.ui.progress.progressCharts
import com.hardtekpt.crux.ui.weightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeekSummary(val climbs: Int = 0, val sends: Int = 0, val daysClimbed: Int = 0)

/** Everything the dashboard's widgets can draw from. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val todaysPlan: WorkoutTemplate? = null,
    val week: WeekSummary = WeekSummary(),
    val latestBest: PersonalBest? = null,
    val weight: WeightSummary? = null,
    val weights: List<Measurement> = emptyList(),
    val recentClimbs: List<Climb> = emptyList(),
    val charts: ProgressCharts = ProgressCharts(),
    val projects: List<Project> = emptyList(),
    /** Climbs per day, for the consistency grid. */
    val activity: Map<LocalDate, Int> = emptyMap(),
    /** Weeks in a row, up to this one, with a day on the wall; and the longest run. */
    val weekStreak: Int = 0,
    val bestWeekStreak: Int = 0,
    val daysLast30: Int = 0,
)

/** The layout being shown, and while editing, the working copy. */
data class DashboardState(val widgets: List<DashboardWidget> = emptyList(), val editing: Boolean = false, val addingWidget: Boolean = false)

@HiltViewModel
class HomeViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    bodyRepository: BodyRepository,
    templateRepository: TemplateRepository,
    placeRepository: PlaceRepository,
    preferences: UserPreferencesRepository,
    private val dashboardRepository: DashboardRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private data class Inputs(
        val templates: List<WorkoutTemplate>,
        val climbs: List<Climb>,
        val bests: List<PersonalBest>,
        val weights: List<Measurement>,
        val scales: GradeScales,
        val projects: List<Project> = emptyList(),
    )

    val uiState: StateFlow<HomeUiState> = combine(
        templateRepository.observeTemplates(),
        climbRepository.observeClimbs(),
        climbRepository.observePersonalBests(),
        bodyRepository.observeWeights(),
        preferences.gradeScales,
    ) { templates, climbs, bests, weights, scales -> Inputs(templates, climbs, bests, weights, scales) }
        .combine(placeRepository.observeProjects()) { inputs, projects -> inputs.copy(projects = projects) }
        .map(::buildState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today))

    val demoMode: StateFlow<Boolean> = preferences.demoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _dashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = _dashboard.asStateFlow()

    init {
        viewModelScope.launch {
            dashboardRepository.layout.collect { saved ->
                // While editing, the working copy wins; saved changes land on Done.
                _dashboard.update { if (it.editing) it else it.copy(widgets = saved) }
            }
        }
    }

    private fun buildState(inputs: Inputs): HomeUiState {
        val weekClimbs = inputs.climbs.filter { !it.date.isBefore(weekStart) }
        val climbDays = inputs.climbs.groupingBy { it.date }.eachCount()
        val (streak, bestStreak) = com.hardtekpt.crux.ui.you.weekStreaks(climbDays.keys, today)
        return HomeUiState(
            isLoading = false,
            today = today,
            // A fixed rotation through the plans by day of the week until scheduling exists.
            todaysPlan = inputs.templates.takeIf { it.isNotEmpty() }?.let { it[today.dayOfWeek.ordinal % it.size] },
            week = WeekSummary(
                climbs = weekClimbs.size,
                sends = weekClimbs.count { it.style.isSend },
                daysClimbed = weekClimbs.map { it.date }.distinct().size,
            ),
            latestBest = inputs.bests.maxWithOrNull(compareBy<PersonalBest> { it.date }.thenBy { it.gradeIndex }),
            weight = inputs.weights.weightSummary(),
            weights = inputs.weights,
            recentClimbs = inputs.climbs.take(RECENT_LIMIT),
            charts = progressCharts(inputs.climbs, inputs.scales, today),
            projects = inputs.projects,
            activity = climbDays,
            weekStreak = streak,
            bestWeekStreak = bestStreak,
            daysLast30 = climbDays.keys.count { !it.isBefore(today.minusDays(29)) && !it.isAfter(today) },
        )
    }

    fun startEditing() = _dashboard.update { it.copy(editing = true) }

    fun finishEditing() {
        val widgets = _dashboard.value.widgets
        _dashboard.update { it.copy(editing = false, addingWidget = false) }
        viewModelScope.launch { dashboardRepository.save(widgets) }
    }

    fun move(fromId: String, toId: String) = _dashboard.update { state ->
        val from = state.widgets.indexOfFirst { it.id == fromId }
        val to = state.widgets.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return@update state
        state.copy(widgets = state.widgets.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun moveBy(id: String, delta: Int) = _dashboard.update { state ->
        val from = state.widgets.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in state.widgets.indices) return@update state
        state.copy(widgets = state.widgets.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun resize(id: String) = _dashboard.update { state ->
        state.copy(widgets = state.widgets.map { if (it.id == id) it.nextSize() else it })
    }

    fun remove(id: String) = _dashboard.update { state ->
        state.copy(widgets = state.widgets.filterNot { it.id == id })
    }

    fun openAddWidget() = _dashboard.update { it.copy(addingWidget = true) }
    fun closeAddWidget() = _dashboard.update { it.copy(addingWidget = false) }

    fun add(type: WidgetType) = _dashboard.update { state ->
        state.copy(widgets = state.widgets + DashboardWidget(type = type), addingWidget = false)
    }

    fun resetToDefault() {
        viewModelScope.launch {
            dashboardRepository.reset()
            val defaults = dashboardRepository.layout.first()
            _dashboard.update { it.copy(widgets = defaults, addingWidget = false) }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 6
    }
}
