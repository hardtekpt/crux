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
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeekSummary(val climbs: Int = 0, val sends: Int = 0, val daysClimbed: Int = 0)

/** The place you climb at most lately: how often, when last, and the projects open there. */
data class MyGym(val placeId: Long, val name: String, val daysLast30: Int, val climbsLast30: Int, val lastVisit: LocalDate, val projects: List<Project>)

/** The place with the most days climbed in the last 90 days (else ever), with its open projects. */
fun myGym(climbs: List<Climb>, projects: List<Project>, today: LocalDate): MyGym? {
    val placed = climbs.filter { it.placeId != null }
    if (placed.isEmpty()) return null
    val recent = placed.filter { !it.date.isBefore(today.minusDays(89)) }.ifEmpty { placed }
    val (placeId, atPlace) = recent.groupBy { it.placeId!! }.maxBy { (_, list) -> list.map { it.date }.distinct().size }
    val all = placed.filter { it.placeId == placeId }
    val month = all.filter { !it.date.isBefore(today.minusDays(29)) }
    return MyGym(
        placeId = placeId,
        name = atPlace.first().place.orEmpty(),
        daysLast30 = month.map { it.date }.distinct().size,
        climbsLast30 = month.size,
        lastVisit = all.maxOf { it.date },
        projects = projects.filter { it.problem.placeId == placeId },
    )
}

/** Everything the dashboard's widgets can draw from. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val todaysPlan: WorkoutTemplate? = null,
    /** Today's plans from the weekly schedule, in order; [todaysPlan] is the first. */
    val todaysPlans: List<WorkoutTemplate> = emptyList(),
    /** Today is a climbing day on the schedule, at this place if it has one. */
    val climbingToday: Boolean = false,
    val climbingPlace: String? = null,
    /** This week from the schedule; empty when nothing is scheduled. */
    val schedule: List<com.hardtekpt.crux.ui.train.WeekDay> = emptyList(),
    /** On a day with nothing planned: the next planned thing and its day. */
    val nextUp: Pair<LocalDate, String>? = null,
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
    val myGym: MyGym? = null,
    /** Training week by week and per exercise, for the Training and Exercise widgets. */
    val training: com.hardtekpt.crux.ui.progress.TrainingProgress = com.hardtekpt.crux.ui.progress.TrainingProgress(),
)

/** The layout being shown, and while editing, the working copy. */
data class DashboardState(
    val widgets: List<DashboardWidget> = emptyList(),
    val editing: Boolean = false,
    val addingWidget: Boolean = false,
    /** A widget being told what to show: a new one of [WidgetSetup.type], or the one with [WidgetSetup.widgetId]. */
    val setup: WidgetSetup? = null,
)

/** Choosing what a configurable widget shows. */
data class WidgetSetup(val type: WidgetType, val widgetId: String? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    bodyRepository: BodyRepository,
    templateRepository: TemplateRepository,
    placeRepository: PlaceRepository,
    scheduleRepository: com.hardtekpt.crux.data.ScheduleRepository,
    sessionRepository: com.hardtekpt.crux.data.SessionRepository,
    preferences: UserPreferencesRepository,
    private val dashboardRepository: DashboardRepository,
    clock: Clock,
    training: com.hardtekpt.crux.ui.progress.TrainingProgressSource? = null,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val zone = clock.zone
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private data class Inputs(
        val templates: List<WorkoutTemplate>,
        val climbs: List<Climb>,
        val weights: List<Measurement>,
        val scales: GradeScales,
        val projects: List<Project> = emptyList(),
        val slots: List<com.hardtekpt.crux.data.ScheduleSlot> = emptyList(),
        val sessions: List<com.hardtekpt.crux.data.Session> = emptyList(),
    )

    val uiState: StateFlow<HomeUiState> = combine(
        templateRepository.observeTemplates(),
        climbRepository.observeClimbs(),
        bodyRepository.observeWeights(),
        preferences.gradeScales,
    ) { templates, climbs, weights, scales -> Inputs(templates, climbs, weights, scales) }
        .combine(placeRepository.observeProjects()) { inputs, projects -> inputs.copy(projects = projects) }
        .combine(scheduleRepository.observeWeek()) { inputs, slots -> inputs.copy(slots = slots) }
        .combine(sessionRepository.observeFinished()) { inputs, sessions -> inputs.copy(sessions = sessions) }
        .map(::buildState)
        .combine(training?.observe() ?: kotlinx.coroutines.flow.flowOf(com.hardtekpt.crux.ui.progress.TrainingProgress())) { state, progress ->
            state.copy(training = progress)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today))

    val demoMode: StateFlow<Boolean> = preferences.demoMode
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
        // Today from the weekly schedule; with nothing scheduled at all, a rotation through the plans.
        val scheduled = inputs.slots.isNotEmpty()
        val schedule = if (scheduled) {
            com.hardtekpt.crux.ui.train.buildWeek(today, inputs.slots, inputs.sessions, climbDays.keys, zone)
        } else {
            emptyList()
        }
        val todaySlots = inputs.slots.filter { it.day == today.dayOfWeek }
        val byId = inputs.templates.associateBy { it.id }
        val todaysPlans = if (scheduled) {
            todaySlots.mapNotNull { slot -> slot.planId?.let(byId::get) }
        } else {
            listOfNotNull(inputs.templates.takeIf { it.isNotEmpty() }?.let { it[today.dayOfWeek.ordinal % it.size] })
        }
        val climbSlot = todaySlots.firstOrNull { it.kind == com.hardtekpt.crux.data.local.ScheduleKind.CLIMB }
        val nextUp = if (scheduled && todaySlots.isEmpty()) {
            (1L..7L).asSequence().map { today.plusDays(it) }.firstNotNullOfOrNull { date ->
                inputs.slots.firstOrNull { it.day == date.dayOfWeek }?.let { slot ->
                    date to (slot.planName ?: "Climbing day")
                }
            }
        } else {
            null
        }
        return HomeUiState(
            isLoading = false,
            today = today,
            todaysPlan = todaysPlans.firstOrNull(),
            todaysPlans = todaysPlans,
            climbingToday = climbSlot != null,
            climbingPlace = climbSlot?.placeName,
            schedule = schedule,
            nextUp = nextUp,
            week = WeekSummary(
                climbs = weekClimbs.size,
                sends = weekClimbs.count { it.style.isSend },
                daysClimbed = weekClimbs.map { it.date }.distinct().size,
            ),
            latestBest = newestHardestSend(inputs.climbs),
            weight = inputs.weights.weightSummary(),
            weights = inputs.weights,
            recentClimbs = inputs.climbs.take(RECENT_LIMIT),
            charts = progressCharts(inputs.climbs, inputs.scales, today),
            projects = inputs.projects,
            activity = climbDays,
            weekStreak = streak,
            bestWeekStreak = bestStreak,
            daysLast30 = climbDays.keys.count { !it.isBefore(today.minusDays(29)) && !it.isAfter(today) },
            myGym = myGym(inputs.climbs, inputs.projects, today),
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

    /** Adds a widget; one that shows a figure or an exercise asks which first. */
    fun add(type: WidgetType) = _dashboard.update { state ->
        if (type.configurable) {
            state.copy(addingWidget = false, setup = WidgetSetup(type))
        } else {
            state.copy(widgets = state.widgets + DashboardWidget(type = type), addingWidget = false)
        }
    }

    /** Opens the choice of what a widget shows. */
    fun configure(id: String) = _dashboard.update { state ->
        state.widgets.firstOrNull { it.id == id }?.let { state.copy(setup = WidgetSetup(it.type, it.id)) } ?: state
    }

    /** Sets what the widget being set up shows, adding it if it's new. */
    fun applySetup(stat: com.hardtekpt.crux.data.TrainingStat?, exerciseId: Long?) = _dashboard.update { state ->
        val setup = state.setup ?: return@update state
        val widgets = if (setup.widgetId == null) {
            state.widgets + DashboardWidget(type = setup.type, stat = stat, exerciseId = exerciseId)
        } else {
            state.widgets.map { if (it.id == setup.widgetId) it.copy(stat = stat, exerciseId = exerciseId) else it }
        }
        state.copy(widgets = widgets, setup = null)
    }

    fun cancelSetup() = _dashboard.update { it.copy(setup = null) }

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

/**
 * The newest send that was harder than every send before it, in any style: a 7A redpoint
 * stays the latest best until something harder goes, whatever was flashed since. Grades are
 * compared only within a discipline and scale (and local grades within their place).
 */
fun newestHardestSend(climbs: List<Climb>): PersonalBest? {
    val hardest = mutableMapOf<Triple<Discipline, GradeScale, Long?>, Int>()
    var newest: Climb? = null
    climbs.filter { it.style.isSend }.sortedWith(compareBy<Climb> { it.date }.thenBy { it.id }).forEach { climb ->
        val key = Triple(climb.discipline, climb.gradeScale, climb.placeId.takeIf { climb.gradeScale.isLocal })
        val before = hardest[key]
        if (before == null || climb.gradeIndex > before) {
            hardest[key] = climb.gradeIndex
            newest = climb
        }
    }
    return newest?.let {
        PersonalBest(
            discipline = it.discipline,
            style = it.style,
            gradeScale = it.gradeScale,
            gradeIndex = it.gradeIndex,
            name = it.name,
            place = it.place,
            date = it.date,
            placeId = it.placeId,
            gradeLabel = it.gradeLabel,
            gradeColour = it.gradeColour,
        )
    }
}
