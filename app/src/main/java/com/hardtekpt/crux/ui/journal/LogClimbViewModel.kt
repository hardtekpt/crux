package com.hardtekpt.crux.ui.journal

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.PlaceRepository
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
import kotlinx.coroutines.flow.combine
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
    /** The note without its angle line: the Angle chip owns that line. */
    val notes: String = "",
    /** Board climbs: the board's angle, written as the note's first line on save. */
    val angle: Int? = null,
    /** Whether the climber set the angle, so picking a wall doesn't overwrite it. */
    val angleTouched: Boolean = false,
    /** The note as it was loaded for an edit, to keep an old-form angle line as it was. */
    val loadedNote: com.hardtekpt.crux.data.model.BoardAngleNote.Parsed? = null,
    val placeId: Long? = null,
    /** Which part of the place (its main gym, its Moonboard). */
    val sectionId: Long? = null,
    /** The live session the climb belongs to: the one running when it was logged. */
    val sessionId: Long? = null,
    val areaId: Long? = null,
    val problemId: Long? = null,
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
    val earlierGoes: List<Boolean> = emptyList(),
    val dateError: String? = null,
    val nameError: String? = null,
    /** The dataset climb this climb was picked from, shown as the linked name row. */
    val source: com.hardtekpt.crux.data.ClimbSource? = null,
    /** Whether the link changed in this form, so saving sets it on the climb. */
    val sourceTouched: Boolean = false,
    /** Log this climb from a dataset used at several sections: the Where sheet opens on those. */
    val whereLimit: Set<Long>? = null,
    /** The picked climb's holds and whose positions they are, copied when it was logged. */
    val problemHolds: Pair<String, String>? = null,
    val openWhere: Boolean = false,
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
    val goesBefore: Int get() = earlier.size

    /** The climb's goes logged before this log, as a trail: falls (false) and sends (true). */
    val earlier: List<Boolean> get() = if (problemId != null) earlierGoes else emptyList()

    /** Falls and sends on the climb in all: the earlier logs' and this one's. */
    val fallsInAll: Int get() = falls + earlier.count { !it }
    val sendsInAll: Int get() = sends + earlier.count { it }

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

    /** "Garage board says V6": the picked dataset climb's own grade, under the grade strip. */
    val gradeHint: String? get() = source?.let { s ->
        s.climb.gradeLabel?.let { grade -> "${s.dataset.name} says $grade" + (angle?.takeIf { s.climb.catalogueUuid != null }?.let { " at $it°" } ?: "") }
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
    private val datasetRepository: com.hardtekpt.crux.data.DatasetRepository? = null,
    playlistRepository: com.hardtekpt.crux.data.PlaylistRepository? = null,
    private val catalogue: com.hardtekpt.crux.data.CatalogueRepository? = null,
) : ViewModel() {

    // Route arguments, read directly so the view model needs no navigation runtime.
    private var climbId: Long = savedStateHandle.get<Long>("climbId") ?: 0L
    private val routePlaceId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val routeProblemId: Long = savedStateHandle.get<Long>("problemId") ?: 0L
    private val routeSectionId: Long = savedStateHandle.get<Long>("sectionId") ?: 0L
    private val routeReuseToday: Boolean = savedStateHandle.get<Boolean>("reuseToday") ?: false
    private val routeDatasetClimbId: Long = savedStateHandle.get<Long>("datasetClimbId") ?: 0L
    private val routeCatalogueUuid: String = savedStateHandle.get<String>("catalogueUuid").orEmpty()

    private val _draft = MutableStateFlow(LogClimbDraft(climbId = climbId, date = LocalDate.now(clock)))
    val draft: StateFlow<LogClimbDraft> = _draft.asStateFlow()

    /** Your datasets, the sections they're linked to by hand, and your playlists, for picking a climb. */
    val datasets: StateFlow<List<com.hardtekpt.crux.data.Dataset>> = (datasetRepository?.observeDatasets() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val datasetLinks: StateFlow<Map<Long, Set<Long>>> = (datasetRepository?.observeLinks() ?: flowOf(emptyMap()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val playlists: StateFlow<List<com.hardtekpt.crux.data.Playlist>> = (playlistRepository?.observePlaylists() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Set when picking a climb finds it logged today: the screen opens that log instead. */
    private val _openLog = MutableStateFlow<Long?>(null)
    val openLog: StateFlow<Long?> = _openLog.asStateFlow()

    /** A climb from Log this climb, kept until Where is a section that uses its dataset. */
    private var routeSource: com.hardtekpt.crux.data.ClimbSource? = null

    /** Climbs logged with no place, for suggestions when no place is picked. */
    private val unplaced = MutableStateFlow<List<Problem>>(emptyList())

    /** Saved places to pick from, most recently visited first. */
    val places: StateFlow<List<PlaceSummary>> = placeRepository.observePlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The climb's other logs, newest first, shown under the form. */
    val earlierLogs: StateFlow<List<com.hardtekpt.crux.data.model.Climb>> = _draft.map { it.problemId to it.climbId }.distinctUntilChanged()
        .flatMapLatest { (problemId, climbId) ->
            if (problemId == null) flowOf(emptyList()) else climbRepository.observeClimbsForProblem(problemId).map { logs -> logs.filter { it.id != climbId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Walls and problems of the picked place. */
    val placeDetail: StateFlow<PlaceDetail?> = _draft.map { it.placeId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else placeRepository.observePlaceDetail(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * A new log on a board section starts at the angle last used there; a Mini MoonBoard, which is
     * only built at 40°, starts at that. Off a board, the angle is cleared.
     */
    private fun followBoard() {
        viewModelScope.launch {
            combine(_draft.map { it.placeId to it.sectionId }.distinctUntilChanged(), placeDetail) { spot, _ -> spot }.collect { (_, sectionId) ->
                val draft = _draft.value
                if (draft.isEditing || draft.angleTouched) return@collect
                val angle = if (onBoard(draft)) {
                    sectionId?.let { preferences.boardAngle(it) } ?: boardOf(draft)?.fixedAngle
                } else {
                    null
                }
                _draft.update { if (it.angleTouched || it.isEditing) it else it.copy(angle = angle) }
            }
        }
    }

    init {
        followBoard()
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
        // Opening a project continues its log from today or from the running session, if it has one.
        if (climbId == 0L && routeReuseToday && routeProblemId != 0L) {
            val running = sessions.running()
            val today = LocalDate.now(clock)
            val existing = climbRepository.observeClimbsForProblem(routeProblemId).first()
                .firstOrNull { it.date == today || (running != null && it.sessionId == running.id) }
            if (existing != null) {
                climbId = existing.id
                _draft.update { it.copy(climbId = existing.id) }
            }
        }
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
                    // The angle line goes to the chip; the field shows the rest.
                    notes = com.hardtekpt.crux.data.model.BoardAngleNote.read(climb.notes).body,
                    angle = com.hardtekpt.crux.data.model.BoardAngleNote.read(climb.notes).angle,
                    angleTouched = true,
                    loadedNote = com.hardtekpt.crux.data.model.BoardAngleNote.read(climb.notes),
                    placeId = climb.placeId,
                    sectionId = climb.sectionId,
                    sessionId = climb.sessionId,
                    areaId = climb.areaId,
                    problemId = climb.problemId,
                    effort = climb.effort,
                    imagePath = climb.imagePath,
                    savedImagePath = climb.imagePath,
                    videoPath = climb.videoPath,
                    savedVideoPath = climb.videoPath,
                )
            }
            climb.problemId?.let { refreshEarlierGoes(it, climb) }
            // Picked from a dataset: the linked name row, and the holds it was logged with.
            val problem = climb.problemId?.let { placeRepository.getProblem(it) }
            val source = sourceOf(problem)
            _draft.update { it.copy(source = source ?: it.source, problemHolds = problem?.holds?.let { h -> problem.holdsBoard?.let { h to it } }) }
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
        problem?.let { p ->
            // The place's walls and facilities, loaded here since the form's own may not be yet.
            applyProblem(p, p.placeId?.let { placeRepository.observePlaceDetail(it).first() })
            refreshEarlierGoes(p.id, null)
            sourceOf(p)?.let { source -> _draft.update { it.copy(source = source) } }
        }
        if (routeDatasetClimbId != 0L || routeCatalogueUuid.isNotEmpty()) logFromDataset(routeDatasetClimbId)
    }

    /** Log this climb from a board catalogue: the climb and its catalogue, graded at the setter's angle. */
    private suspend fun catalogueSource(uuid: String, angle: Int?): com.hardtekpt.crux.data.ClimbSource? {
        val (key, climb) = catalogue?.find(uuid) ?: return null
        val dataset = datasetRepository?.observeDatasets()?.first()?.firstOrNull { it.catalogueKey == key } ?: return null
        return com.hardtekpt.crux.data.ClimbSource(climb.asDatasetClimb(angle ?: climb.setterAngle), dataset)
    }

    /** Searches a board catalogue at the draft's angle, for the Climb step and name suggestions. */
    suspend fun searchCatalogue(dataset: com.hardtekpt.crux.data.Dataset, query: String, limit: Int = 60): List<com.hardtekpt.crux.data.ClimbSource> {
        val repo = catalogue ?: return emptyList()
        val key = dataset.catalogueKey ?: return emptyList()
        val angle = _draft.value.angle ?: repo.angles(key).let { angles -> if (40 in angles) 40 else angles.firstOrNull() } ?: return emptyList()
        return repo.search(key, angle, query, limit = limit).map { row ->
            val index = row.difficulty?.let(com.hardtekpt.crux.data.BoardseshBoards::fontIndex)
            com.hardtekpt.crux.data.ClimbSource(
                com.hardtekpt.crux.data.DatasetClimb(
                    name = row.name,
                    gradeScale = com.hardtekpt.crux.data.model.GradeScale.FONT.takeIf { index != null },
                    gradeIndex = index,
                    setter = row.setter,
                    catalogueUuid = row.uuid,
                    holds = row.frames,
                ),
                dataset,
            )
        }
    }

    /**
     * Log this climb, from a dataset: Where becomes the one section using the dataset (or the
     * route's place), and the climb is picked there. With several sections, the Where sheet opens
     * on them; with none, Where stays empty.
     */
    private suspend fun logFromDataset(datasetClimbId: Long) {
        val repo = datasetRepository ?: return
        val all = repo.observeDatasets().first()
        val source = if (routeCatalogueUuid.isNotEmpty()) {
            catalogueSource(routeCatalogueUuid, _draft.value.angle)
        } else {
            all.firstNotNullOfOrNull { d -> d.climbs.firstOrNull { it.id == datasetClimbId }?.let { com.hardtekpt.crux.data.ClimbSource(it, d) } }
        } ?: return
        routeSource = source
        if (routePlaceId == 0L) {
            val links = repo.observeLinks().first()
            val using = placeRepository.observePlaces().first().map { it.place }.flatMap { place ->
                place.sections.filter { section ->
                    com.hardtekpt.crux.data.datasetsFor(section, all, links[section.id].orEmpty()).any {
                        it.id ==
                            source.dataset.id
                    }
                }
                    .map { place to it }
            }
            when {
                using.size == 1 -> {
                    val (place, section) = using.single()
                    applyPlace(place.id)
                    _draft.update { regrade(it.copy(sectionId = section.id, venue = section.type.venue, areaId = null), place) }
                }

                using.size > 1 -> {
                    applyPlace(null)
                    _draft.update { it.copy(whereLimit = using.map { u -> u.second.id }.toSet(), openWhere = true) }
                }

                else -> applyPlace(null)
            }
        }
        val draft = _draft.value
        val detail = draft.placeId?.let { placeRepository.observePlaceDetail(it).first() }
        pickDatasetClimb(source, here = detail?.let { candidatesIn(draft, it) } ?: placeRepository.climbsAt(null))
        if (draft.placeId != null) routeSource = null
    }

    /** The dataset climb a climb was picked from, while it's still in its dataset. */
    private suspend fun sourceOf(problem: Problem?): com.hardtekpt.crux.data.ClimbSource? {
        problem?.sourceCatalogueUuid?.let { return catalogueSource(it, _draft.value.angle) }
        val id = problem?.sourceDatasetClimbId ?: return null
        val all = datasetRepository?.observeDatasets()?.first() ?: return null
        return all.firstNotNullOfOrNull { d -> d.climbs.firstOrNull { it.id == id }?.let { com.hardtekpt.crux.data.ClimbSource(it, d) } }
    }

    /** The datasets the draft's section uses: those for its board, and any linked to it. */
    fun sectionDatasets(draft: LogClimbDraft = _draft.value): List<com.hardtekpt.crux.data.Dataset> {
        val place = placeDetail.value?.place?.takeIf { it.id == draft.placeId } ?: return emptyList()
        val section = place.sections.firstOrNull { it.id == draft.sectionId } ?: return emptyList()
        return com.hardtekpt.crux.data.datasetsFor(section, datasets.value, datasetLinks.value[section.id].orEmpty())
    }

    /**
     * What the Where sheet's Climb step offers: your climbs at the section, its datasets, and the
     * playlists with climbs from them. Null when the section uses no dataset.
     */
    fun climbChoices(draft: LogClimbDraft = _draft.value): ClimbChoices? {
        val used = sectionDatasets(draft).takeIf { it.isNotEmpty() } ?: return null
        val ids = used.map { it.id }.toSet()
        val detail = placeDetail.value?.takeIf { it.place.id == draft.placeId }
        val several = (detail?.place?.sections?.size ?: 0) > 1
        return ClimbChoices(
            datasets = used,
            playlists = playlists.value.mapNotNull { list ->
                list.copy(items = list.items.filter { it.dataset?.id in ids }).takeIf { it.items.isNotEmpty() }
            },
            yours = detail?.problems.orEmpty().filter { !several || it.problem.sectionId == null || it.problem.sectionId == draft.sectionId },
            system = draft.system,
        )
    }

    /**
     * Picks a climb from a dataset. The climb here of that name and type (or already linked to
     * it) is continued, and opens as its log when it was logged today; otherwise the form takes
     * its type, name and grade (converted to the section's scale) for a new climb.
     */
    fun pickDatasetClimb(source: com.hardtekpt.crux.data.ClimbSource) = pickDatasetClimb(source, candidates())

    private fun pickDatasetClimb(source: com.hardtekpt.crux.data.ClimbSource, here: List<Problem>) {
        val climb = source.climb
        val match = here.firstOrNull { if (climb.catalogueUuid != null) it.sourceCatalogueUuid == climb.catalogueUuid else it.sourceDatasetClimbId == climb.id }
            ?: here.firstOrNull { it.discipline == climb.discipline && it.name.trim().equals(climb.name.trim(), ignoreCase = true) }
        if (match != null && !_draft.value.isEditing) {
            viewModelScope.launch {
                val running = sessions.running()
                val today = LocalDate.now(clock)
                val logged = climbRepository.observeClimbsForProblem(match.id).first()
                    .firstOrNull { it.date == today || (running != null && it.sessionId == running.id) }
                if (logged != null) {
                    _openLog.value = logged.id
                    return@launch
                }
                applyProblem(match)
                refreshEarlierGoes(match.id, null)
                _draft.update {
                    it.copy(
                        source = source,
                        sourceTouched = if (climb.catalogueUuid !=
                            null
                        ) {
                            match.sourceCatalogueUuid != climb.catalogueUuid
                        } else {
                            match.sourceDatasetClimbId != climb.id
                        },
                    )
                }
            }
            return
        }
        if (_draft.value.discipline != climb.discipline) setDiscipline(climb.discipline)
        _draft.update { draft ->
            draft.copy(
                name = climb.name,
                nameError = null,
                problemId = draft.problemId.takeIf { draft.isEditing },
                earlierGoes = if (draft.isEditing) draft.earlierGoes else emptyList(),
                gradeIndex = sourceGrade(draft.copy(source = source)) ?: draft.gradeIndex,
                source = source,
                sourceTouched = true,
            )
        }
    }

    /** The picked dataset climb's grade in the draft's scale, when it converts. */
    private fun sourceGrade(draft: LogClimbDraft): Int? {
        val climb = draft.source?.climb ?: return null
        val scale = climb.gradeScale ?: return null
        val index = climb.gradeIndex ?: return null
        return com.hardtekpt.crux.data.convertGrade(scale, index, draft.gradeScale)
    }

    /** ✕ on the linked name row: a plain name again, the same text. */
    fun unlinkSource() {
        routeSource = null
        _draft.update { it.copy(source = null, sourceTouched = true) }
    }

    /** New climb in the Climb step: nothing picked, an empty name to type. */
    fun newClimb() = _draft.update {
        routeSource = null
        if (it.isEditing) it else it.copy(problemId = null, earlierGoes = emptyList(), name = "", source = null, sourceTouched = false)
    }

    /** The screen opened the log picking found. */
    fun logOpened() {
        _openLog.value = null
    }

    /** The Where sheet opened for Log this climb. */
    fun whereOpened() = _draft.update { it.copy(openWhere = false) }

    /**
     * After Where changes: a link to a dataset the new section doesn't use is dropped (the name
     * stays); a climb from Log this climb is picked once Where is a section that uses it.
     */
    private fun followSource(place: com.hardtekpt.crux.data.model.Place?) {
        val draft = _draft.value
        val source = draft.source ?: routeSource ?: return
        val section = place?.sections?.firstOrNull { it.id == draft.sectionId }
        val fits = place == null ||
            (
                section != null &&
                    com.hardtekpt.crux.data.datasetsFor(section, datasets.value, datasetLinks.value[section.id].orEmpty()).any { it.id == source.dataset.id }
                )
        if (fits) {
            if (draft.source == null && routeSource != null) pickDatasetClimb(source)
            if (place != null) routeSource = null
        } else if (draft.source != null) {
            _draft.update { it.copy(source = null, sourceTouched = false) }
        }
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
                    // Editing a log moves its whole climb; a new log at another place is another climb.
                    problemId = draft.problemId.takeIf { draft.isEditing },
                    earlierGoes = if (draft.isEditing) draft.earlierGoes else emptyList(),
                    venue = (place?.sections?.firstOrNull()?.type ?: place?.type)?.venue ?: draft.venue,
                    scaleOverride = override,
                    local = section?.localScale,
                )
                if (next.gradeScale == draft.gradeScale) next else next.copy(gradeIndex = sourceGrade(next) ?: next.system.defaultIndex)
            }
            followSource(place)
        }
    }

    /** Quick place creation from the form; it becomes the picked place. */
    fun createPlace(name: String, type: PlaceType) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = placeRepository.savePlace(
                PlaceInput(name = name, types = listOf(type), location = null, notes = null),
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
                problemId = if (keepProblem || it.isEditing) it.problemId else null,
                sectionId = section?.id ?: it.sectionId,
                venue = section?.type?.venue ?: it.venue,
            ),
            detail?.place,
        )
    }.also { followSource(placeDetail.value?.place) }

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
                problemId = if (keepArea || it.isEditing) it.problemId else null,
            ),
            detail.place,
        )
    }.also { followSource(placeDetail.value?.place) }

    /**
     * Each part of a place grades in its own scale: after the part changes, the grades follow it.
     * A climb picked from a problem keeps the problem's grade.
     */
    private fun regrade(draft: LogClimbDraft, place: com.hardtekpt.crux.data.model.Place?): LogClimbDraft {
        if (place == null || draft.problemId != null) return draft
        val section = place.section(draft.sectionId)
        val next = draft.copy(scaleOverride = section?.scaleFor(draft.discipline), local = section?.localScale)
        return if (next.gradeScale == draft.gradeScale) next else next.copy(gradeIndex = sourceGrade(next) ?: next.system.defaultIndex)
    }

    /** Continues a climb: the form takes its name, grade and where it is; everything stays editable. */
    fun pickProblem(problem: Problem) {
        applyProblem(problem)
        _draft.update { it.copy(source = null, sourceTouched = false) }
        viewModelScope.launch {
            refreshEarlierGoes(problem.id, null)
            val source = sourceOf(problem)
            _draft.update { if (it.problemId == problem.id && it.source == null) it.copy(source = source) else it }
        }
    }

    /**
     * The goes on a climb before this log, in the order they were logged: for an edit, the logs
     * logged before it, whatever their day (a climb can be repeated any time); for a new log, all
     * of them.
     */
    private suspend fun refreshEarlierGoes(problemId: Long, climb: com.hardtekpt.crux.data.model.Climb?) {
        val goes = climbRepository.observeClimbsForProblem(problemId).first().filter { it.id != climb?.id }
        val earlier = if (climb == null) {
            goes
        } else {
            goes.filter { it.id < climb.id }
        }
        val trail = earlier.sortedBy { it.id }.flatMap(::goesOf)
        _draft.update { if (it.problemId == problemId) it.copy(earlierGoes = trail) else it }
    }

    private fun applyProblem(problem: Problem, loaded: PlaceDetail? = placeDetail.value) = _draft.update {
        val detail = loaded?.takeIf { d -> d.place.id == problem.placeId }
        val area = detail?.areas?.firstOrNull { a -> a.id == (problem.areaId ?: it.areaId) }
        // The climb's own facility, else its wall's; never the place's first by default.
        val section = detail?.place?.sections?.firstOrNull { s -> s.id == problem.sectionId }
            ?: area?.let { a -> detail.place.sections.firstOrNull { s -> s.id == a.sectionId } }
        it.copy(
            problemId = problem.id,
            areaId = problem.areaId ?: it.areaId,
            venue = section?.type?.venue ?: detail?.place?.typeOf(area)?.venue ?: it.venue,
            sectionId = section?.id ?: it.sectionId,
            discipline = problem.discipline,
            scaleOverride = problem.gradeScale,
            gradeIndex = problem.gradeIndex,
            name = problem.name,
            problemHolds = problem.holds?.let { h -> problem.holdsBoard?.let { h to it } },
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
            match == null && draft.problemId != null -> _draft.update { it.copy(problemId = null, earlierGoes = emptyList()) }
        }
    }

    /** The climbs at [detail]'s place a log at the draft's facility can continue. */
    private fun candidatesIn(draft: LogClimbDraft, detail: PlaceDetail): List<Problem> {
        val several = detail.place.sections.size > 1
        return detail.problems.map { it.problem }.filter { !several || it.sectionId == null || it.sectionId == draft.sectionId }
    }

    /** The climbs a new log can continue: those at the picked place, or those with no place. */
    fun candidates(): List<Problem> {
        val draft = _draft.value
        return if (draft.placeId == null) {
            unplaced.value
        } else {
            // At a place with several facilities, the climbs at the picked one (or with none yet).
            val detail = placeDetail.value?.takeIf { it.place.id == draft.placeId }
            val several = (detail?.place?.sections?.size ?: 0) > 1
            detail?.problems?.map { it.problem }.orEmpty().filter { !several || it.sectionId == null || it.sectionId == draft.sectionId }
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

    fun setDiscipline(discipline: Discipline) = _draft.update { draft ->
        if (draft.discipline == discipline) return@update draft
        val override = placeDetail.value?.place?.scaleFor(discipline, draft.sectionId)
        val next = if (draft.isEditing) {
            draft.copy(discipline = discipline, scaleOverride = override)
        } else {
            draft.copy(discipline = discipline, scaleOverride = override, problemId = null, earlierGoes = emptyList())
        }
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

    /** Sets or clears the board angle. */
    fun setAngle(angle: Int?) {
        _draft.update { it.copy(angle = angle, angleTouched = true) }
        // A board catalogue's climb is graded at each angle: its grade follows.
        val uuid = _draft.value.source?.climb?.catalogueUuid ?: return
        viewModelScope.launch {
            val source = catalogueSource(uuid, angle) ?: return@launch
            _draft.update { d ->
                if (d.source?.climb?.catalogueUuid != uuid) return@update d
                val next = d.copy(source = source)
                next.copy(gradeIndex = sourceGrade(next) ?: d.gradeIndex)
            }
        }
    }

    /** The board the climb is on, when its Where is a board section that names one. */
    fun boardOf(draft: LogClimbDraft): com.hardtekpt.crux.data.model.BoardModel? = placeDetail.value?.place?.takeIf {
        it.id == draft.placeId
    }?.sections?.firstOrNull { it.id == draft.sectionId }?.takeIf { it.type == PlaceType.BOARD }?.board

    /** Whether the climb is on a board: a board section, or no saved place with Board picked. */
    fun onBoard(draft: LogClimbDraft): Boolean {
        val place = placeDetail.value?.place?.takeIf { it.id == draft.placeId }
        return if (draft.placeId == null) {
            draft.venue == Venue.BOARD
        } else {
            place?.sections?.firstOrNull { it.id == draft.sectionId }?.type == PlaceType.BOARD ||
                (place != null && place.sections.none { it.id == draft.sectionId } && place.types == listOf(PlaceType.BOARD))
        }
    }

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
            // On a picked climb, the repository makes the form's name, grade and where the climb's.
            val problemId = draft.problemId
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
                // On a board the angle is the note's first line; off a board it goes.
                notes = com.hardtekpt.crux.data.model.BoardAngleNote.write(draft.angle.takeIf { onBoard(draft) }, draft.notes, draft.loadedNote),
                placeId = place?.id,
                areaId = draft.areaId.takeIf { place != null },
                problemId = problemId,
                effort = draft.effort,
                gradeLabel = draft.system.label(draft.gradeIndex),
                gradeColour = draft.system.colour(draft.gradeIndex),
                source = if (draft.sourceTouched) {
                    com.hardtekpt.crux.data.model.SourceChange(
                        draft.source?.climb?.takeIf { it.catalogueUuid == null }?.id,
                        draft.source?.climb?.setter,
                        draft.source?.climb?.catalogueUuid,
                        draft.source?.climb?.holds,
                        draft.source?.dataset?.holdsBoard,
                    )
                } else {
                    null
                },
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
            // The next log on this board section starts at the same angle.
            val angle = draft.angle
            if (angle != null && onBoard(draft)) draft.sectionId?.let { preferences.setBoardAngle(it, angle) }
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

/** The Where sheet's Climb step: your climbs at the section, its datasets and playlists, graded in [system]. */
data class ClimbChoices(
    val datasets: List<com.hardtekpt.crux.data.Dataset>,
    val playlists: List<com.hardtekpt.crux.data.Playlist>,
    val yours: List<com.hardtekpt.crux.data.model.ProblemWithStats>,
    val system: GradeSystem,
)
