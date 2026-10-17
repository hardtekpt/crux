package com.hardtekpt.crux.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.SectionInput
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of first-run setup, in order. Units to Start are numbered 1 to 4. */
enum class SetupStep { WELCOME, UNITS, PLACE, BODY, START, RESTORE }

/** The board a first place's Board section is: Kilter Original, MoonBoard, or one Crux doesn't know. */
enum class SetupBoard(val label: String, val model: BoardModel?) {
    KILTER("Kilter Original", BoardModel.KILTER_ORIGINAL),
    MOON("MoonBoard", BoardModel.MOON_2016),
    OTHER("Other board", null),
}

/** What setup has gathered. Nothing is saved until Start; every step left alone keeps the default. */
data class SetupState(
    val step: SetupStep = SetupStep.WELCOME,
    /** Moving back through the steps, for the transition. */
    val back: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val boulder: GradeScale = Discipline.BOULDER.defaultScale,
    val route: GradeScale = Discipline.ROUTE.defaultScale,
    val placeName: String = "",
    val kinds: Set<PlaceType> = setOf(PlaceType.GYM),
    val board: SetupBoard = SetupBoard.KILTER,
    /** Added on the place step; skipped leaves no place. */
    val placeAdded: Boolean = false,
    /** In kg and cm; null until the body step's Next. */
    val weight: Double? = null,
    val height: Double? = null,
    val demo: Boolean = false,
    val saving: Boolean = false,
) {
    val canAddPlace: Boolean get() = placeName.isNotBlank() && kinds.isNotEmpty()

    /** The place as it will be saved: its kinds in Gym, Crag, Board order, the board named. */
    fun place(): PlaceInput? = if (!placeAdded || !canAddPlace) {
        null
    } else {
        PlaceInput(
            name = placeName.trim(),
            location = null,
            notes = null,
            sections = PlaceType.entries.filter { it in kinds }.map { kind ->
                if (kind == PlaceType.BOARD) {
                    SectionInput(type = kind, name = board.model?.let { board.label } ?: kind.label, board = board.model)
                } else {
                    SectionInput(type = kind, name = kind.label)
                }
            },
        )
    }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val places: PlaceRepository,
    private val body: BodyRepository,
    private val clock: Clock,
    private val firstRun: com.hardtekpt.crux.data.FirstRun,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    private fun go(step: SetupStep, back: Boolean = false) = _state.update { it.copy(step = step, back = back) }

    fun start() = go(SetupStep.UNITS)

    fun restore() = go(SetupStep.RESTORE)

    /** One step back; from Welcome there is nowhere to go. */
    fun back(): Boolean {
        val previous = when (_state.value.step) {
            SetupStep.WELCOME -> return false
            SetupStep.UNITS, SetupStep.RESTORE -> SetupStep.WELCOME
            SetupStep.PLACE -> SetupStep.UNITS
            SetupStep.BODY -> SetupStep.PLACE
            SetupStep.START -> SetupStep.BODY
        }
        go(previous, back = true)
        return true
    }

    fun setUnits(units: UnitSystem) = _state.update { it.copy(units = units) }

    fun setScale(scale: GradeScale) = _state.update { if (scale.discipline == Discipline.BOULDER) it.copy(boulder = scale) else it.copy(route = scale) }

    /** Units: Next and Skip both move on; Skip puts the defaults back. */
    fun unitsDone(skip: Boolean) {
        if (skip) _state.update { it.copy(units = UnitSystem.METRIC, boulder = Discipline.BOULDER.defaultScale, route = Discipline.ROUTE.defaultScale) }
        go(SetupStep.PLACE)
    }

    fun setPlaceName(name: String) = _state.update { it.copy(placeName = name) }

    fun toggleKind(kind: PlaceType) = _state.update { it.copy(kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind) }

    fun setBoard(board: SetupBoard) = _state.update { it.copy(board = board) }

    fun placeDone(skip: Boolean) {
        _state.update { it.copy(placeAdded = !skip && it.canAddPlace) }
        go(SetupStep.BODY)
    }

    /** Body: Next keeps what the rulers show, Skip leaves both unset. */
    fun bodyDone(weight: Double?, height: Double?) {
        _state.update { it.copy(weight = weight, height = height) }
        go(SetupStep.START)
    }

    fun setDemo(demo: Boolean) = _state.update { it.copy(demo = demo) }

    /** Skip setup: the defaults stay and the app opens. */
    fun skip(onDone: () -> Unit) = viewModelScope.launch {
        preferences.setSetupDone()
        onDone()
    }

    /** Start (or Open demo): saves everything into the climber's own data set, then opens the app. */
    fun finish(onDone: () -> Unit): kotlinx.coroutines.Job? {
        val s = _state.value
        if (s.saving) return null
        _state.update { it.copy(saving = true) }
        return viewModelScope.launch {
            preferences.setUnits(s.units)
            preferences.setGradeScale(s.boulder)
            preferences.setGradeScale(s.route)
            s.place()?.let { place ->
                val id = places.savePlace(place)
                preferences.setLastPlaceId(id)
            }
            val today = LocalDate.now(clock)
            s.weight?.let { body.logWeight(it, today) }
            s.height?.let { body.setMeasurement(MeasurementType.HEIGHT, it) }
            // Demo mode last, so all of the above went into the climber's own data.
            if (s.demo) preferences.setDemoMode(true)
            preferences.setSetupDone()
            onDone()
        }
    }

    /** Back from Backups: with a backup restored the app has data, so setup is done; otherwise Welcome again. */
    fun leaveRestore(onDone: () -> Unit) = viewModelScope.launch {
        if (firstRun.hasOwnData()) {
            preferences.setSetupDone()
            onDone()
        } else {
            back()
        }
    }
}
