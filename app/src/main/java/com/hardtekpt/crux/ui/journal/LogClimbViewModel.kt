package com.hardtekpt.crux.ui.journal

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.ProblemInput
import com.hardtekpt.crux.data.images.ImageFiles
import com.hardtekpt.crux.data.local.MediaKind
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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
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
    /**
     * Today's goes in the order climbed: true for a send, false for a fall. Starts as one send,
     * the common flash; the first tap on a pad replaces it.
     */
    val goes: List<Boolean> = listOf(true),
    /** Whether [goes] came from the climber (a tap, or the saved log) rather than the default. */
    val goesTouched: Boolean = false,
    /** Routes: a first-go send without beta, an onsight. */
    val noBeta: Boolean = false,
    val venue: Venue = Venue.GYM,
    val date: LocalDate,
    val name: String = "",
    /** Free-text place, used when no saved place is picked. */
    val place: String = "",
    val notes: String = "",
    val placeId: Long? = null,
    /** Which part of the place (its main gym, its Moonboard). */
    val sectionId: Long? = null,
    /** The live session the climb belongs to: the one running when it was logged. */
    val sessionId: Long? = null,
    val areaId: Long? = null,
    val problemId: Long? = null,
    val angle: Int? = null,
    /** How hard it felt, 1 to 10; optional. */
    val effort: Int? = null,
    /** An attached photo (file name in app storage), and the one saved before this edit. */
    val imagePath: String? = null,
    val savedImagePath: String? = null,
    val addingImage: Boolean = false,
    val imageFailed: Boolean = false,
    /** An attached video, and the one saved before this edit. */
    val videoPath: String? = null,
    val savedVideoPath: String? = null,
    val addingVideo: Boolean = false,
    val videoFailed: Boolean = false,
    /** Goes logged on the picked climb before this log, so a first-go send can still be a redpoint. */
    val earlierGoes: Int = 0,
    val dateError: String? = null,
    val nameError: String? = null,
    val confirmDelete: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = climbId != 0L
    val gradeScale: GradeScale get() = scaleOverride?.takeIf { it.discipline == discipline } ?: scales.forDiscipline(discipline)
    val system: GradeSystem get() = GradeSystem(gradeScale, local.takeIf { gradeScale.isLocal })
    val sends: Int get() = goes.count { it }
    val falls: Int get() = goes.count { !it }
    val attempts: Int get() = goes.size

    /** Goes on the picked climb before this log, when there are any. */
    val goesBefore: Int get() = if (problemId != null) earlierGoes else 0

    /**
     * The style, worked out from the goes: no send is an attempt; a send on the very first go
     * (today, and none before) is a flash, or an onsight for a route without beta; any other
     * send is a redpoint.
     */
    val style: AscentStyle
        get() = when {
            sends == 0 -> AscentStyle.ATTEMPT
            goesBefore == 0 && goes.first() -> if (noBeta && discipline == Discipline.ROUTE) AscentStyle.ONSIGHT else AscentStyle.FLASH
            else -> AscentStyle.REDPOINT
        }

    /** The No beta switch only matters for a route sent first go. */
    val asksAboutBeta: Boolean get() = discipline == Discipline.ROUTE && sends > 0 && goesBefore == 0 && goes.first()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogClimbViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val climbRepository: ClimbRepository,
    private val placeRepository: PlaceRepository,
    private val clock: Clock,
    private val preferences: UserPreferencesRepository,
    private val images: ImageFiles,
    private val sessions: com.hardtekpt.crux.data.SessionRepository,
) : ViewModel() {

    // Route arguments, read directly so the view model needs no navigation runtime.
    private val climbId: Long = savedStateHandle.get<Long>("climbId") ?: 0L
    private val routePlaceId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val routeProblemId: Long = savedStateHandle.get<Long>("problemId") ?: 0L
    private val routeSectionId: Long = savedStateHandle.get<Long>("sectionId") ?: 0L

    private val _draft = MutableStateFlow(LogClimbDraft(climbId = climbId, date = LocalDate.now(clock)))
    val draft: StateFlow<LogClimbDraft> = _draft.asStateFlow()

    /** Climbs logged with no place, for suggestions when no place is picked. */
    private val unplaced = MutableStateFlow<List<Problem>>(emptyList())

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
        refreshUnplaced()
    }

    private suspend fun prefill() {
        if (climbId != 0L) {
            val climb = climbRepository.getClimb(climbId) ?: return
            _draft.update {
                it.copy(
                    discipline = climb.discipline,
                    scaleOverride = climb.gradeScale,
                    gradeIndex = climb.gradeIndex,
                    goes = goesOf(climb),
                    goesTouched = true,
                    noBeta = climb.style == AscentStyle.ONSIGHT,
                    venue = climb.venue,
                    date = climb.date,
                    name = climb.name.orEmpty(),
                    place = climb.place.orEmpty(),
                    notes = climb.notes.orEmpty(),
                    placeId = climb.placeId,
                    sectionId = climb.sectionId,
                    sessionId = climb.sessionId,
                    areaId = climb.areaId,
                    problemId = climb.problemId,
                    angle = climb.angle,
                    effort = climb.effort,
                    imagePath = climb.imagePath,
                    savedImagePath = climb.imagePath,
                    videoPath = climb.videoPath,
                    savedVideoPath = climb.videoPath,
                )
            }
            climb.problemId?.let { refreshEarlierGoes(it, climb) }
            // Local grades need the place's list to show the strip.
            climb.placeId?.let { placeRepository.getPlace(it) }?.section(climb.sectionId)?.localScale?.let { local -> _draft.update { it.copy(local = local) } }
            return
        }
        val problem = routeProblemId.takeIf { it != 0L }?.let { placeRepository.getProblem(it) }
        // A climb logged while a session runs joins it, and starts at the session's place.
        val running = sessions.running()
        _draft.update { it.copy(sessionId = running?.id) }
        val lastPlaceId = preferences.lastPlaceId.first()
        val placeId = problem?.placeId ?: routePlaceId.takeIf { it != 0L } ?: running?.placeId ?: lastPlaceId
        placeId?.let { applyPlace(it) }
        // Back at the last place: start at the facility and wall the last climb was on.
        if (problem == null && routeSectionId == 0L && running?.sectionId == null && placeId != null && placeId == lastPlaceId) {
            restoreLastSpot(placeId)
        }
        val sessionSection = running?.sectionId?.takeIf { routeSectionId == 0L && running.placeId == placeId }
        if (sessionSection != null && placeId != null) {
            val place = placeRepository.getPlace(placeId)
            place?.sections?.firstOrNull { it.id == sessionSection }?.let { section ->
                _draft.update { regrade(it.copy(sectionId = section.id, venue = section.type.venue), place) }
            }
        }
        // Logging from a facility on the place page starts in that facility.
        if (routeSectionId != 0L && placeId != null) {
            val place = placeRepository.getPlace(placeId)
            place?.sections?.firstOrNull { it.id == routeSectionId }?.let { section ->
                _draft.update { regrade(it.copy(sectionId = section.id, venue = section.type.venue), place) }
            }
        }
        problem?.let(::pickProblem)
    }

    private suspend fun restoreLastSpot(placeId: Long) {
        val (sectionId, areaId) = preferences.lastSpot.first()
        val detail = placeRepository.observePlaceDetail(placeId).first() ?: return
        val place = detail.place
        val area = detail.areas.firstOrNull { it.id == areaId }
        val section = place.sections.firstOrNull { it.id == sectionId } ?: area?.let { place.sectionOf(it) } ?: return
        val keepArea = area != null && place.sectionOf(area)?.id == section.id
        _draft.update {
            regrade(
                it.copy(
                    sectionId = section.id,
                    venue = section.type.venue,
                    areaId = if (keepArea) area?.id else null,
                ),
                place,
            )
        }
    }

    fun selectPlace(id: Long?) {
        viewModelScope.launch { applyPlace(id) }
    }

    private suspend fun applyPlace(id: Long?) {
        run {
            val place = id?.let { placeRepository.getPlace(it) }
            _draft.update { draft ->
                val section = place?.sections?.firstOrNull()
                val override = section?.scaleFor(draft.discipline)
                val next = draft.copy(
                    placeId = place?.id,
                    sectionId = place?.sections?.firstOrNull()?.id,
                    areaId = null,
                    problemId = null,
                    earlierGoes = 0,
                    venue = (place?.sections?.firstOrNull()?.type ?: place?.type)?.venue ?: draft.venue,
                    angle = if (place != null && PlaceType.BOARD in place.types) (draft.angle ?: place.defaultAngle ?: DEFAULT_ANGLE) else null,
                    scaleOverride = override,
                    local = section?.localScale,
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
                PlaceInput(name = name, types = listOf(type), location = null, defaultAngle = null, notes = null),
            )
            applyPlace(id)
        }
    }

    fun selectArea(id: Long?) = _draft.update {
        // Changing the wall drops a problem that is not on it.
        val problemOnWall = it.problemId?.let { pid -> placeDetail.value?.problems?.firstOrNull { p -> p.problem.id == pid } }
        val keepProblem = problemOnWall != null && (id == null || problemOnWall.problem.areaId == id)
        // At a place with several parts, the wall says which part this was.
        val detail = placeDetail.value
        val section = id?.let { areaId -> detail?.areas?.firstOrNull { a -> a.id == areaId } }?.let { a -> detail?.place?.sectionOf(a) }
        regrade(
            it.copy(
                areaId = id,
                problemId = if (keepProblem) it.problemId else null,
                sectionId = section?.id ?: it.sectionId,
                venue = section?.type?.venue ?: it.venue,
            ),
            detail?.place,
        )
    }

    /** At a place with several parts: which one this climb was at. A wall in another part is dropped. */
    fun selectSection(sectionId: Long) = _draft.update {
        val detail = placeDetail.value
        val section = detail?.place?.sections?.firstOrNull { s -> s.id == sectionId } ?: return@update it
        val area = it.areaId?.let { id -> detail.areas.firstOrNull { a -> a.id == id } }
        val keepArea = area != null && detail.place.sectionOf(area)?.id == sectionId
        regrade(
            it.copy(
                sectionId = sectionId,
                venue = section.type.venue,
                areaId = if (keepArea) it.areaId else null,
                problemId = if (keepArea) it.problemId else null,
            ),
            detail.place,
        )
    }

    /**
     * Each part of a place grades in its own scale: after the part changes, the grades follow it.
     * A climb picked from a problem keeps the problem's grade.
     */
    private fun regrade(draft: LogClimbDraft, place: com.hardtekpt.crux.data.model.Place?): LogClimbDraft {
        if (place == null || draft.problemId != null) return draft
        val section = place.section(draft.sectionId)
        val next = draft.copy(scaleOverride = section?.scaleFor(draft.discipline), local = section?.localScale)
        return if (next.gradeScale == draft.gradeScale) next else next.copy(gradeIndex = next.system.defaultIndex)
    }

    /** Continues a climb: the form takes its name, grade and where it is; everything stays editable. */
    fun pickProblem(problem: Problem) {
        applyProblem(problem)
        viewModelScope.launch { refreshEarlierGoes(problem.id, null) }
    }

    /**
     * Adds up the goes on a climb before this log: for an edit, those on an earlier day or logged
     * before it (a log can be dated back after a later one was logged); for a new log, all of them.
     */
    private suspend fun refreshEarlierGoes(problemId: Long, climb: com.hardtekpt.crux.data.model.Climb?) {
        val goes = climbRepository.observeClimbsForProblem(problemId).first().filter { it.id != climb?.id }
        val earlier = if (climb == null) {
            goes
        } else {
            goes.filter { it.date < climb.date || it.id < climb.id }
        }
        _draft.update { if (it.problemId == problemId) it.copy(earlierGoes = earlier.sumOf { g -> g.attempts }) else it }
    }

    private fun applyProblem(problem: Problem) = _draft.update {
        val detail = placeDetail.value?.takeIf { d -> d.place.id == problem.placeId }
        val area = detail?.areas?.firstOrNull { a -> a.id == (problem.areaId ?: it.areaId) }
        val section = detail?.place?.sections?.firstOrNull { s -> s.id == problem.sectionId } ?: detail?.place?.sectionOf(area)
        it.copy(
            problemId = problem.id,
            areaId = problem.areaId ?: it.areaId,
            venue = section?.type?.venue ?: detail?.place?.typeOf(area)?.venue ?: it.venue,
            sectionId = section?.id ?: it.sectionId,
            discipline = problem.discipline,
            scaleOverride = problem.gradeScale,
            gradeIndex = problem.gradeIndex,
            name = problem.name,
        )
    }

    /** Typing a name another climb here already has continues that climb; any other name starts a new one. */
    private fun matchClimb(name: String) {
        val draft = _draft.value
        if (draft.isEditing) return
        val trimmed = name.trim()
        val match = candidates().firstOrNull { it.name.equals(trimmed, ignoreCase = true) && it.discipline == draft.discipline }
        when {
            match != null && match.id != draft.problemId -> pickProblem(match)
            match == null && draft.problemId != null -> _draft.update { it.copy(problemId = null, earlierGoes = 0) }
        }
    }

    /** The climbs a new log can continue: those at the picked place, or those with no place. */
    fun candidates(): List<Problem> {
        val draft = _draft.value
        return if (draft.placeId == null) {
            unplaced.value
        } else {
            placeDetail.value?.takeIf { it.place.id == draft.placeId }?.problems?.map { it.problem }?.filter { !it.retired }.orEmpty()
        }
    }

    fun refreshUnplaced() {
        viewModelScope.launch { unplaced.value = placeRepository.climbsAt(null) }
    }

    /** A fall or a send, in the order climbed. The first tap replaces the default single send. */
    fun addGo(sent: Boolean) = _draft.update {
        when {
            !it.goesTouched -> it.copy(goes = listOf(sent), goesTouched = true)
            it.goes.size >= ATTEMPTS.last -> it
            else -> it.copy(goes = it.goes + sent)
        }
    }

    /** Takes the last go back; taking back the only one returns to the default single send. */
    fun undoGo() = _draft.update {
        if (it.goes.size <= 1) it.copy(goes = listOf(true), goesTouched = false) else it.copy(goes = it.goes.dropLast(1))
    }

    fun setNoBeta(noBeta: Boolean) = _draft.update { it.copy(noBeta = noBeta) }

    fun setEffort(effort: Int?) = _draft.update { it.copy(effort = effort?.coerceIn(1, 10)) }

    fun setAngle(angle: Int) = _draft.update { it.copy(angle = angle.coerceIn(0, 70)) }

    fun setDiscipline(discipline: Discipline) = _draft.update { draft ->
        if (draft.discipline == discipline) return@update draft
        val override = placeDetail.value?.place?.scaleFor(discipline, draft.sectionId)
        val next = draft.copy(discipline = discipline, scaleOverride = override, problemId = null, earlierGoes = 0)
        next.copy(gradeIndex = next.system.defaultIndex)
    }

    fun setGrade(index: Int) = _draft.update {
        it.copy(gradeIndex = index.coerceIn(0, (it.system.labels.size - 1).coerceAtLeast(0)))
    }

    fun setVenue(venue: Venue) = _draft.update { it.copy(venue = venue) }

    fun setDate(date: LocalDate) = _draft.update { it.copy(date = date, dateError = null) }

    fun setName(name: String) {
        _draft.update { it.copy(name = name, nameError = null) }
        matchClimb(name)
    }

    fun setPlace(place: String) = _draft.update { it.copy(place = place.take(MAX_TEXT)) }

    fun setNotes(notes: String) = _draft.update { it.copy(notes = notes) }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }

    /** Copies a picked or captured photo in; it is attached when the climb is saved. */
    fun attachImage(uri: Uri) {
        _draft.update { it.copy(addingImage = true, imageFailed = false) }
        viewModelScope.launch {
            val name = runCatching { images.importFrom(uri) }.getOrNull()
            val replaced = _draft.value.imagePath.takeIf { it != _draft.value.savedImagePath }
            if (name != null) images.delete(replaced)
            _draft.update { it.copy(imagePath = name ?: it.imagePath, addingImage = false, imageFailed = name == null) }
        }
    }

    fun removeImage() {
        val draft = _draft.value
        if (draft.imagePath != draft.savedImagePath) viewModelScope.launch { images.delete(draft.imagePath) }
        _draft.update { it.copy(imagePath = null) }
    }

    fun captureUri(): Uri = images.newCaptureUri()

    /** Copies a picked or recorded video in; it is attached when the climb is saved. */
    fun attachVideo(uri: Uri) {
        _draft.update { it.copy(addingVideo = true, videoFailed = false) }
        viewModelScope.launch {
            val name = runCatching { images.importVideo(uri) }.getOrNull()
            val replaced = _draft.value.videoPath.takeIf { it != _draft.value.savedVideoPath }
            if (name != null) images.delete(replaced)
            _draft.update { it.copy(videoPath = name ?: it.videoPath, addingVideo = false, videoFailed = name == null) }
        }
    }

    fun removeVideo() {
        val draft = _draft.value
        if (draft.videoPath != draft.savedVideoPath) viewModelScope.launch { images.delete(draft.videoPath) }
        _draft.update { it.copy(videoPath = null) }
    }

    fun videoCaptureUri(): Uri = images.newVideoCaptureUri()

    override fun onCleared() {
        // Media added and then abandoned with the form.
        val draft = _draft.value
        if (draft.saved) return
        listOf(draft.imagePath.takeIf { it != draft.savedImagePath }, draft.videoPath.takeIf { it != draft.savedVideoPath })
            .filterNotNull()
            .forEach { orphan -> kotlinx.coroutines.GlobalScope.launch { images.delete(orphan) } }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            images.delete(_draft.value.savedImagePath)
            if (_draft.value.imagePath != _draft.value.savedImagePath) images.delete(_draft.value.imagePath)
            images.delete(_draft.value.savedVideoPath)
            if (_draft.value.videoPath != _draft.value.savedVideoPath) images.delete(_draft.value.videoPath)
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
            else -> null
        }
        if (dateError != null || nameError != null) {
            _draft.update { it.copy(dateError = dateError, nameError = nameError) }
            return
        }
        _draft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val place = draft.placeId?.let { placeRepository.getPlace(it) }
            // A picked climb follows the form: its name, grade and where it is.
            val problemId = draft.problemId
            if (problemId != null) {
                val existing = placeRepository.getProblem(problemId)
                if (existing != null && draft.name.isNotBlank()) {
                    placeRepository.saveProblem(
                        ProblemInput(
                            id = problemId,
                            placeId = place?.id,
                            areaId = draft.areaId.takeIf { place != null },
                            sectionId = draft.sectionId.takeIf { place != null },
                            name = draft.name.trim(),
                            discipline = draft.discipline,
                            gradeScale = draft.gradeScale,
                            gradeIndex = draft.gradeIndex,
                            tape = existing.tape,
                            notes = existing.notes,
                            gradeLabel = draft.system.label(draft.gradeIndex),
                            gradeColour = draft.system.colour(draft.gradeIndex),
                        ),
                    )
                }
            }
            val climb = NewClimb(
                discipline = draft.discipline,
                gradeScale = draft.gradeScale,
                gradeIndex = draft.gradeIndex,
                style = draft.style,
                attempts = draft.attempts,
                sends = draft.sends,
                venue = place?.let { p ->
                    p.sections.firstOrNull { s -> s.id == draft.sectionId }?.type?.venue
                        ?: draft.venue.takeIf { v -> p.types.any { t -> t.venue == v } } ?: p.type.venue
                } ?: draft.venue,
                sectionId = draft.sectionId.takeIf { place != null && place.sections.any { s -> s.id == it } },
                sessionId = draft.sessionId,
                date = draft.date,
                name = draft.name,
                place = place?.name ?: draft.place,
                notes = draft.notes,
                placeId = place?.id,
                areaId = draft.areaId.takeIf { place != null },
                problemId = problemId,
                angle = draft.angle.takeIf { place != null && draft.venue == Venue.BOARD },
                effort = draft.effort,
                gradeLabel = draft.system.label(draft.gradeIndex),
                gradeColour = draft.system.colour(draft.gradeIndex),
            )
            val id = if (draft.isEditing) {
                climbRepository.updateClimb(draft.climbId, climb)
                draft.climbId
            } else {
                climbRepository.logClimb(climb)
            }
            if (draft.imagePath != draft.savedImagePath) {
                climbRepository.setClimbMedia(id, MediaKind.IMAGE, draft.imagePath)
                images.delete(draft.savedImagePath)
            }
            if (draft.videoPath != draft.savedVideoPath) {
                climbRepository.setClimbMedia(id, MediaKind.VIDEO, draft.videoPath)
                images.delete(draft.savedVideoPath)
            }
            _draft.update { it.copy(isSaving = false, saved = true) }
            // The next new climb starts at the same place, facility and wall.
            if (!draft.isEditing) {
                val sectionId = draft.sectionId.takeIf { place != null && place.sections.any { s -> s.id == it } }
                preferences.setLastSpot(place?.id, sectionId, draft.areaId.takeIf { place != null })
            }
        }
    }

    companion object {
        val ATTEMPTS = 1..99
        const val MAX_TEXT = 60
        const val DEFAULT_ANGLE = 40
    }
}

/**
 * A saved log's goes as a trail for the form: a first-go send (flash, onsight) first, then the
 * falls, then any other sends; otherwise the falls, then the sends.
 */
internal fun goesOf(climb: com.hardtekpt.crux.data.model.Climb): List<Boolean> {
    val sends = climb.sends.coerceIn(0, climb.attempts)
    val falls = climb.attempts - sends
    return if (climb.style.singleAttempt && sends > 0) {
        listOf(true) + List(falls) { false } + List(sends - 1) { true }
    } else {
        List(falls) { false } + List(sends) { true }
    }.ifEmpty { listOf(false) }
}
