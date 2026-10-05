package com.hardtekpt.crux.ui.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.ProblemInput
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.GradeSystem
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The draft lives here so rotation keeps what was typed. */
data class LogClimbDraft(
    /** Non-zero when editing an existing climb. */
    val climbId: Long = 0,
    val discipline: Discipline = Discipline.BOULDER,
    /** The climber's chosen scale per discipline, from Settings. */
    val scales: GradeScales = GradeScales(),
    /** A scale set by the place or problem, which wins over Settings. */
    val scaleOverride: GradeScale? = null,
    /** The picked place's own grades, used when its scale for this discipline is local. */
    val local: LocalScale? = null,
    val gradeIndex: Int = scales.boulder.defaultIndex,
    val style: AscentStyle = AscentStyle.FLASH,
    val attempts: Int = 1,
    val venue: Venue = Venue.GYM,
    val date: LocalDate,
    val name: String = "",
    /** Free-text place, used when no saved place is picked. */
    val place: String = "",
    val notes: String = "",
    val placeId: Long? = null,
    val areaId: Long? = null,
    val problemId: Long? = null,
    val angle: Int? = null,
    /** How hard it felt, 1 to 10; optional. */
    val effort: Int? = null,
    /** Save the named climb as a problem at the picked place. */
    val saveAsProblem: Boolean = false,
    val dateError: String? = null,
    val nameError: String? = null,
    val confirmDelete: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = climbId != 0L
    val gradeScale: GradeScale get() = scaleOverride?.takeIf { it.discipline == discipline } ?: scales.forDiscipline(discipline)
    val system: GradeSystem get() = GradeSystem(gradeScale, local.takeIf { gradeScale.isLocal })
    val styles: List<AscentStyle> get() = AscentStyle.forDiscipline(discipline)
    val attemptsLocked: Boolean get() = style.singleAttempt
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogClimbViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val climbRepository: ClimbRepository,
    private val placeRepository: PlaceRepository,
    private val clock: Clock,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    // Route arguments, read directly so the view model needs no navigation runtime.
    private val climbId: Long = savedStateHandle.get<Long>("climbId") ?: 0L
    private val routePlaceId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val routeProblemId: Long = savedStateHandle.get<Long>("problemId") ?: 0L

    private val _draft = MutableStateFlow(LogClimbDraft(climbId = climbId, date = LocalDate.now(clock)))
    val draft: StateFlow<LogClimbDraft> = _draft.asStateFlow()

    /** Saved places to pick from, most recently visited first. */
    val places: StateFlow<List<PlaceSummary>> = placeRepository.observePlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Walls and problems of the picked place. */
    val placeDetail: StateFlow<PlaceDetail?> = _draft.map { it.placeId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else placeRepository.observePlaceDetail(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // Follow the scales chosen in Settings. A grade already picked in the same scale is kept.
            preferences.gradeScales.collect { scales ->
                _draft.update { draft ->
                    val before = draft.gradeScale
                    val next = draft.copy(scales = scales)
                    if (next.gradeScale == before) next else next.copy(gradeIndex = next.system.defaultIndex)
                }
            }
        }
        viewModelScope.launch { prefill() }
    }

    private suspend fun prefill() {
        if (climbId != 0L) {
            val climb = climbRepository.getClimb(climbId) ?: return
            _draft.update {
                it.copy(
                    discipline = climb.discipline,
                    scaleOverride = climb.gradeScale,
                    gradeIndex = climb.gradeIndex,
                    style = climb.style,
                    attempts = climb.attempts,
                    venue = climb.venue,
                    date = climb.date,
                    name = climb.name.orEmpty(),
                    place = climb.place.orEmpty(),
                    notes = climb.notes.orEmpty(),
                    placeId = climb.placeId,
                    areaId = climb.areaId,
                    problemId = climb.problemId,
                    angle = climb.angle,
                    effort = climb.effort,
                )
            }
            // Local grades need the place's list to show the strip.
            climb.placeId?.let { placeRepository.getPlace(it) }?.localScale?.let { local -> _draft.update { it.copy(local = local) } }
            return
        }
        val problem = routeProblemId.takeIf { it != 0L }?.let { placeRepository.getProblem(it) }
        val placeId = problem?.placeId ?: routePlaceId.takeIf { it != 0L } ?: preferences.lastPlaceId.first()
        placeId?.let { applyPlace(it) }
        problem?.let(::pickProblem)
    }

    fun selectPlace(id: Long?) {
        viewModelScope.launch { applyPlace(id) }
    }

    private suspend fun applyPlace(id: Long?) {
        run {
            val place = id?.let { placeRepository.getPlace(it) }
            _draft.update { draft ->
                val override = place?.scaleFor(draft.discipline)
                val next = draft.copy(
                    placeId = place?.id,
                    areaId = null,
                    problemId = null,
                    venue = place?.type?.venue ?: draft.venue,
                    angle = if (place?.type == PlaceType.BOARD) (draft.angle ?: place.defaultAngle ?: DEFAULT_ANGLE) else null,
                    scaleOverride = override,
                    local = place?.localScale,
                    saveAsProblem = false,
                )
                if (next.gradeScale == draft.gradeScale) next else next.copy(gradeIndex = next.system.defaultIndex)
            }
        }
    }

    /** Quick place creation from the form; it becomes the picked place. */
    fun createPlace(name: String, type: PlaceType) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = placeRepository.savePlace(
                PlaceInput(name = name, type = type, location = null, boulderScale = null, routeScale = null, defaultAngle = null, notes = null),
            )
            applyPlace(id)
        }
    }

    fun selectArea(id: Long?) = _draft.update {
        // Changing the wall drops a problem that is not on it.
        val problemOnWall = it.problemId?.let { pid -> placeDetail.value?.problems?.firstOrNull { p -> p.problem.id == pid } }
        val keepProblem = problemOnWall != null && (id == null || problemOnWall.problem.areaId == id)
        it.copy(areaId = id, problemId = if (keepProblem) it.problemId else null)
    }

    /** Fills the form from a problem; everything stays editable. */
    fun pickProblem(problem: Problem) = _draft.update {
        val style = it.style.takeIf { s -> s in AscentStyle.forDiscipline(problem.discipline) } ?: AscentStyle.FLASH
        it.copy(
            problemId = problem.id,
            areaId = problem.areaId ?: it.areaId,
            discipline = problem.discipline,
            scaleOverride = problem.gradeScale,
            gradeIndex = problem.gradeIndex,
            name = problem.name,
            style = style,
            saveAsProblem = false,
        )
    }

    fun clearProblem() = _draft.update { it.copy(problemId = null) }

    fun setSaveAsProblem(save: Boolean) = _draft.update { it.copy(saveAsProblem = save) }

    fun setEffort(effort: Int?) = _draft.update { it.copy(effort = effort?.coerceIn(1, 10)) }

    fun setAngle(angle: Int) = _draft.update { it.copy(angle = angle.coerceIn(0, 70)) }

    fun setDiscipline(discipline: Discipline) = _draft.update { draft ->
        if (draft.discipline == discipline) return@update draft
        val style = draft.style.takeIf { it in AscentStyle.forDiscipline(discipline) } ?: AscentStyle.FLASH
        val override = placeDetail.value?.place?.scaleFor(discipline)
        val next = draft.copy(discipline = discipline, style = style, scaleOverride = override, problemId = null)
        next.copy(gradeIndex = next.system.defaultIndex)
    }

    fun setGrade(index: Int) = _draft.update {
        it.copy(gradeIndex = index.coerceIn(0, (it.system.labels.size - 1).coerceAtLeast(0)))
    }

    fun setStyle(style: AscentStyle) = _draft.update {
        // Flash and onsight are one attempt by definition; a redpoint took at least two.
        val attempts = when {
            style.singleAttempt -> 1
            style == AscentStyle.REDPOINT -> maxOf(it.attempts, 2)
            else -> it.attempts
        }
        it.copy(style = style, attempts = attempts)
    }

    fun setAttempts(attempts: Int) = _draft.update { it.copy(attempts = attempts.coerceIn(ATTEMPTS)) }

    fun setVenue(venue: Venue) = _draft.update { it.copy(venue = venue) }

    fun setDate(date: LocalDate) = _draft.update { it.copy(date = date, dateError = null) }

    fun setName(name: String) = _draft.update { it.copy(name = name, nameError = null) }

    fun setPlace(place: String) = _draft.update { it.copy(place = place.take(MAX_TEXT)) }

    fun setNotes(notes: String) = _draft.update { it.copy(notes = notes) }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        viewModelScope.launch {
            climbRepository.deleteClimb(climbId)
            _draft.update { it.copy(confirmDelete = false, saved = true) }
        }
    }

    fun save() {
        val draft = _draft.value
        if (draft.isSaving || draft.saved) return
        val today = LocalDate.now(clock)
        val dateError = if (draft.date.isAfter(today)) "Pick today or an earlier day" else null
        val nameError = when {
            draft.name.length > MAX_TEXT -> "Keep the name under $MAX_TEXT characters"
            draft.saveAsProblem && draft.name.isBlank() -> "Name the problem to save it"
            else -> null
        }
        if (dateError != null || nameError != null) {
            _draft.update { it.copy(dateError = dateError, nameError = nameError) }
            return
        }
        _draft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val place = draft.placeId?.let { placeRepository.getPlace(it) }
            val problemId = draft.problemId ?: if (draft.saveAsProblem && place != null) {
                placeRepository.saveProblem(
                    ProblemInput(
                        placeId = place.id,
                        areaId = draft.areaId,
                        name = draft.name.trim(),
                        discipline = draft.discipline,
                        gradeScale = draft.gradeScale,
                        gradeIndex = draft.gradeIndex,
                        tape = null,
                        notes = null,
                        gradeLabel = draft.system.label(draft.gradeIndex),
                        gradeColour = draft.system.colour(draft.gradeIndex),
                    ),
                )
            } else {
                null
            }
            val climb = NewClimb(
                discipline = draft.discipline,
                gradeScale = draft.gradeScale,
                gradeIndex = draft.gradeIndex,
                style = draft.style,
                attempts = if (draft.style.singleAttempt) 1 else draft.attempts,
                venue = place?.type?.venue ?: draft.venue,
                date = draft.date,
                name = draft.name,
                place = place?.name ?: draft.place,
                notes = draft.notes,
                placeId = place?.id,
                areaId = draft.areaId.takeIf { place != null },
                problemId = problemId,
                angle = draft.angle.takeIf { place?.type == PlaceType.BOARD },
                effort = draft.effort,
                gradeLabel = draft.system.label(draft.gradeIndex),
                gradeColour = draft.system.colour(draft.gradeIndex),
            )
            if (draft.isEditing) climbRepository.updateClimb(draft.climbId, climb) else climbRepository.logClimb(climb)
            _draft.update { it.copy(isSaving = false, saved = true) }
            // The next new climb starts at the same place.
            if (!draft.isEditing) preferences.setLastPlaceId(place?.id)
        }
    }

    companion object {
        val ATTEMPTS = 1..99
        const val MAX_TEXT = 60
        const val DEFAULT_ANGLE = 40
    }
}
