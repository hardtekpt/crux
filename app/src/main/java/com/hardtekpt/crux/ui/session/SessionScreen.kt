package com.hardtekpt.crux.ui.session

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionItem
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.data.model.formatLoad
import com.hardtekpt.crux.data.model.loadUnit
import com.hardtekpt.crux.data.model.loadValue
import com.hardtekpt.crux.data.model.poundsToKg
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxValueStepper
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.input.EffortScale
import com.hardtekpt.crux.ui.components.input.argb
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the set being logged holds before Done: starts at the target, or at the last set's values. */
data class SetDraft(val itemId: Long = 0, val setIndex: Int = 0, val reps: Int = 0, val seconds: Int = 0, val loadKg: Double = 0.0)

/** What a set holds, or will hold once ticked. */
data class SetValues(val reps: Int, val seconds: Int, val loadKg: Double)

/** A rest running down: when it ends, and the item it follows. */
data class Rest(val endsAtMillis: Long, val totalSeconds: Int)

/** The interval timer, running for an exercise ([itemId]) or on its own; [cyclesLogged] are already sets. */
data class SessionTimer(val run: IntervalRun, val itemId: Long?, val cyclesLogged: Int = 0, val setIndexes: List<Int> = emptyList())

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    exercises: ExerciseRepository,
    private val places: PlaceRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
    private val live: com.hardtekpt.crux.live.LiveTimers,
    records: com.hardtekpt.crux.data.RecordRepository? = null,
    private val templates: com.hardtekpt.crux.data.TemplateRepository? = null,
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    val session: StateFlow<Session?> = sessions.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val place: StateFlow<Place?> = session.map { it?.placeId }.distinctUntilChanged().map { id ->
        id?.let { places.getPlace(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val library: StateFlow<List<Exercise>> = exercises.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timerSounds: StateFlow<Boolean> = preferences.timerSounds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Values changed on sets not done yet, by item and set; ticking a set logs them. */
    private val _pending = MutableStateFlow<Map<Pair<Long, Int>, SetValues>>(emptyMap())
    val pending: StateFlow<Map<Pair<Long, Int>, SetValues>> = _pending.asStateFlow()

    /** Each exercise's sets from the last finished session it was in, for the "Last" column. */
    val previous: StateFlow<Map<Long, List<com.hardtekpt.crux.data.SessionSet>>> = sessions.observeFinished()
        .map { finished ->
            buildMap {
                // Newest first: the first session that has an exercise is its last time.
                finished.sortedByDescending { it.startedAtMillis }.forEach { past ->
                    past.items.forEach { item -> if (item.exercise.id !in this && item.sets.isNotEmpty()) put(item.exercise.id, item.sets) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** What a set shows: as logged, as changed, else like the last set done, else the target. */
    fun valuesFor(item: SessionItem, setIndex: Int): SetValues {
        item.sets.firstOrNull { it.setIndex == setIndex && !it.skipped }?.let { return SetValues(it.reps ?: 0, it.seconds ?: 0, it.loadKg ?: 0.0) }
        _pending.value[item.id to setIndex]?.let { return it }
        val last = item.sets.filter { !it.skipped && it.setIndex < setIndex }.maxByOrNull { it.setIndex }
        return SetValues(last?.reps ?: item.target.reps, last?.seconds ?: item.target.seconds, last?.loadKg ?: item.target.loadKg)
    }

    /** Changes a set in place: a logged one is saved at once, one not done yet when it's ticked. */
    fun setValues(item: SessionItem, setIndex: Int, values: SetValues) {
        val logged = item.sets.firstOrNull { it.setIndex == setIndex && !it.skipped }
        if (logged != null) {
            editSet(item, setIndex, values.reps, values.seconds, values.loadKg)
        } else {
            _pending.update { it + ((item.id to setIndex) to values) }
        }
    }

    /** Ticks a set: logs it as shown and starts its rest; ticking a done set takes it back. */
    fun tick(item: SessionItem, setIndex: Int) {
        val logged = item.sets.firstOrNull { it.setIndex == setIndex }
        if (logged != null && !logged.skipped) {
            undoSet(item, setIndex)
            return
        }
        val values = valuesFor(item, setIndex)
        val metric = item.exercise.metric
        _pending.update { it - (item.id to setIndex) }
        viewModelScope.launch {
            sessions.logSet(
                itemId = item.id,
                setIndex = setIndex,
                reps = values.reps.takeIf { metric.usesReps || metric.usesIntervals },
                seconds = values.seconds.takeIf { metric.usesTime || metric.usesIntervals },
                loadKg = values.loadKg.takeIf { metric.usesLoad },
            )
            afterSet(item)
        }
    }

    /** The next set to do in the session: the first exercise with sets left, and its next set. */
    fun nextUp(session: Session): Pair<SessionItem, Int>? = session.items.firstOrNull { !it.finished }?.let { item -> item.nextSet?.let { item to it } }

    /** One more set on an exercise, for this session. */
    fun addSet(item: SessionItem) = updateTarget(item, item.target.copy(sets = maxOf(item.target.sets, item.logged) + 1))

    /** Skips every set of an exercise not done yet. */
    fun skipExercise(item: SessionItem) {
        viewModelScope.launch {
            (0 until item.target.sets).filter { index -> item.sets.none { it.setIndex == index } }.forEach { sessions.skipSet(item.id, it) }
        }
    }

    /** Takes an exercise with nothing logged out of the session. */
    fun removeExercise(item: SessionItem) {
        viewModelScope.launch { sessions.removeExercise(item.id) }
    }

    /** Exercises whose personal record is a set done in this session: they show "New best". */
    val newBests: StateFlow<Set<Long>> = (records?.observeBests() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .combine(session) { bests, current ->
            val sets = current?.items.orEmpty().flatMap { item -> item.sets.map { -it.id to item.id } }.toMap()
            bests.mapNotNull { best -> best.best.takeIf { it.fromSession }?.let { sets[it.id] } }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The exercise on screen; null follows the first one with sets still to do. */
    private val _currentItem = MutableStateFlow<Long?>(null)
    val currentItem: StateFlow<Long?> = _currentItem.asStateFlow()

    private val _draft = MutableStateFlow(SetDraft())
    val draft: StateFlow<SetDraft> = _draft.asStateFlow()

    // The rest and timer outlive this screen: the live notification shows and changes them too.
    private val _rest = live.rest.also { live.attach(sessionId) }
    val rest: StateFlow<Rest?> = _rest.asStateFlow()

    private val _timer = live.timer
    val timer: StateFlow<SessionTimer?> = _timer.asStateFlow()

    /** Counts timers that ran to the end, so the screen can sound the finish. */
    private val _timerEnds = MutableStateFlow(0)
    val timerEnds: StateFlow<Int> = _timerEnds.asStateFlow()

    init {
        // Logging a climb in the session starts the rest set in Settings, unless a timer runs.
        viewModelScope.launch {
            var seen: Int? = null
            session.collect { current ->
                val count = current?.climbs?.size ?: return@collect
                val rest = preferences.climbRestSeconds.first()
                if (seen != null && count > seen!! && rest > 0 && _timer.value == null) {
                    _rest.value = Rest(clock.millis() + rest * 1000L, rest)
                }
                seen = count
            }
        }
    }

    /** The last free timer, so the next one starts the same. */
    private var lastFreeSpec = IntervalSpec.TABATA

    fun now(): Long = clock.millis()

    /**
     * What the screen shows of the time at [nowMillis], in whole seconds: the session's clock, the
     * rest and the timer. The screen moves its clock only when this changes, not on every tick.
     */
    fun shownTime(nowMillis: Long): List<Any?> = listOf(
        session.value?.durationMillis(nowMillis)?.div(1000),
        _rest.value?.let { secondsLeft(it.endsAtMillis - nowMillis) },
        _timer.value?.run?.position(nowMillis)?.let { it.index to secondsLeft(it.leftMillis) },
    )

    /** The item shown: the picked one, else the first with sets left, else the last. */
    fun shownItem(session: Session): SessionItem? = session.items.firstOrNull { it.id == _currentItem.value }
        ?: session.items.firstOrNull { !it.finished }
        ?: session.items.lastOrNull()

    fun show(itemId: Long) {
        _currentItem.value = itemId
    }

    /** Fills the draft for an item's next set: its target, or what the last set was. */
    fun prepare(item: SessionItem) {
        val setIndex = item.nextSet ?: return
        val current = _draft.value
        if (current.itemId == item.id && current.setIndex == setIndex) return
        val last = item.sets.lastOrNull { !it.skipped }
        _draft.value = SetDraft(
            itemId = item.id,
            setIndex = setIndex,
            reps = last?.reps ?: item.target.reps,
            seconds = last?.seconds ?: item.target.seconds,
            loadKg = last?.loadKg ?: item.target.loadKg,
        )
    }

    fun updateDraft(change: (SetDraft) -> SetDraft) = _draft.update(change)

    /** Whether the session's plan has this exercise, so a change can go to the plan too. */
    fun inPlan(item: SessionItem): Boolean = session.value?.templateId != null && item.blockName.isNotEmpty()

    /**
     * Changes an exercise's targets for this session; with [alsoPlan], in the session's plan as well
     * (the same exercise in the same block), so next time starts from them.
     */
    fun updateTarget(item: SessionItem, target: ExerciseTarget, alsoPlan: Boolean = false) {
        if (alsoPlan) {
            val planId = session.value?.templateId
            if (planId != null && templates != null) {
                viewModelScope.launch {
                    val plan = templates.getTemplate(planId) ?: return@launch
                    var changed = false
                    val blocks = plan.blocks.map { block ->
                        if (block.name != item.blockName || changed) return@map block
                        block.copy(
                            items = block.items.map { planItem ->
                                if (!changed && planItem.exercise.id == item.exercise.id) {
                                    changed = true
                                    planItem.copy(target = target)
                                } else {
                                    planItem
                                }
                            },
                        )
                    }
                    if (changed) templates.saveTemplate(plan.copy(blocks = blocks))
                }
            }
        }
        if (target == item.target) return
        viewModelScope.launch { sessions.updateTarget(item.id, target) }
        _draft.update { draft ->
            if (draft.itemId != item.id || item.sets.any { !it.skipped }) {
                draft
            } else {
                draft.copy(reps = target.reps, seconds = target.seconds, loadKg = target.loadKg)
            }
        }
    }

    /** Logs the set as drafted, then starts the rest; after the last set, moves to the next exercise. */
    fun doneSet(item: SessionItem) {
        val d = _draft.value.takeIf { it.itemId == item.id } ?: return
        val metric = item.exercise.metric
        viewModelScope.launch {
            sessions.logSet(
                itemId = item.id,
                setIndex = d.setIndex,
                reps = d.reps.takeIf { metric.usesReps || metric.usesIntervals },
                seconds = d.seconds.takeIf { metric.usesTime || metric.usesIntervals },
                loadKg = d.loadKg.takeIf { metric.usesLoad },
            )
            afterSet(item)
        }
    }

    fun skipSet(item: SessionItem) {
        val setIndex = item.nextSet ?: return
        viewModelScope.launch {
            sessions.skipSet(item.id, setIndex)
            afterSet(item)
        }
    }

    private fun afterSet(item: SessionItem) {
        val lastOfItem = item.logged + 1 >= item.target.sets
        if (lastOfItem) _currentItem.value = null
        if (item.target.restSeconds > 0) {
            _rest.value = Rest(clock.millis() + item.target.restSeconds * 1000L, item.target.restSeconds)
        }
    }

    /** Changes what a logged set holds; a skipped set becomes done. */
    fun editSet(item: SessionItem, setIndex: Int, reps: Int?, seconds: Int?, loadKg: Double?) {
        viewModelScope.launch { sessions.logSet(item.id, setIndex, reps, seconds, loadKg) }
    }

    fun undoSet(item: SessionItem, setIndex: Int) {
        viewModelScope.launch { sessions.undoSet(item.id, setIndex) }
        _currentItem.value = item.id
        _draft.value = SetDraft()
    }

    fun addRest(seconds: Int) = _rest.update {
        it?.copy(endsAtMillis = it.endsAtMillis + seconds * 1000L, totalSeconds = (it.totalSeconds + seconds).coerceAtLeast(1))
    }
    fun endRest() {
        _rest.value = null
    }

    /** The timer an interval exercise asks for: its targets, for the cycles still to do. */
    fun timerFor(item: SessionItem?): IntervalSpec {
        if (item == null || !item.exercise.metric.usesIntervals) return lastFreeSpec
        val t = item.target
        return IntervalSpec(
            prepSeconds = item.exercise.prepSeconds ?: 10,
            workSeconds = t.seconds.coerceAtLeast(1),
            restSeconds = t.repRestSeconds,
            repeats = t.reps.coerceAtLeast(1),
            cycles = (t.sets - item.logged).coerceAtLeast(1),
            cycleRestSeconds = t.restSeconds,
        )
    }

    fun startTimer(spec: IntervalSpec, itemId: Long?) {
        if (itemId == null) lastFreeSpec = spec
        _rest.value = null
        _timer.value = SessionTimer(IntervalRun(spec, clock.millis()), itemId)
    }

    fun pauseTimer() = _timer.update { it?.copy(run = it.run.pause(clock.millis())) }

    fun resumeTimer() = _timer.update { it?.copy(run = it.run.resume(clock.millis())) }

    /** Forward to the next phase, or back to the start of this one (or the one before, early on). */
    fun skipPhase(forward: Boolean) {
        _timer.update { t ->
            t ?: return@update null
            val now = clock.millis()
            val position = t.run.position(now)
            val index = when {
                forward -> position.index + 1
                t.run.elapsed(now) - t.run.startOf(position.index) < 2_000 -> position.index - 1
                else -> position.index
            }
            t.copy(run = t.run.skipTo(index, now))
        }
        tickTimer()
    }

    /** Stops the timer; whole cycles already done stay logged. */
    fun stopTimer() {
        tickTimer()
        _timer.value = null
    }

    /** Logs cycles finished since the last tick as sets of the exercise, and closes the timer at the end. */
    fun tickTimer() {
        val t = _timer.value ?: return
        val now = clock.millis()
        val done = t.run.cyclesDone(now)
        val finished = t.run.position(now).finished
        if (done > t.cyclesLogged) {
            val item = t.itemId?.let { id -> session.value?.items?.firstOrNull { it.id == id } }
            val indexes = item?.let { logCycles(it, done - t.cyclesLogged, t.run.spec, t.setIndexes) }.orEmpty()
            _timer.value = t.copy(cyclesLogged = done, setIndexes = t.setIndexes + indexes)
        }
        if (finished) {
            _timer.value = null
            _timerEnds.update { it + 1 }
            if (t.itemId != null) _currentItem.value = null
        }
    }

    /** Logs [count] cycles into the next free sets, skipping ones this timer already took; returns the sets used. */
    private fun logCycles(item: SessionItem, count: Int, spec: IntervalSpec, taken: List<Int>): List<Int> {
        val load = _draft.value.takeIf { it.itemId == item.id }?.loadKg ?: item.target.loadKg
        val free = generateSequence(0) { it + 1 }.filter { index -> index !in taken && item.sets.none { it.setIndex == index } }.take(count).toList()
        viewModelScope.launch {
            free.forEach { setIndex ->
                sessions.logSet(
                    itemId = item.id,
                    setIndex = setIndex,
                    reps = spec.repeats,
                    seconds = spec.workSeconds,
                    loadKg = load.takeIf { item.exercise.metric.usesLoad },
                )
            }
        }
        return free
    }

    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            val id = sessions.addExercise(sessionId, exercise)
            _currentItem.value = id
        }
    }

    fun finish(effort: Int?, notes: String, onDone: () -> Unit) {
        _timer.value = null
        viewModelScope.launch {
            sessions.finish(sessionId, effort, notes)
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        _timer.value = null
        viewModelScope.launch {
            sessions.discard(sessionId)
            onDone()
        }
    }
}

private val SetValue = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, lineHeight = 18.sp)

/** "45 min" or "1 h 02 min", for a finished session. */
internal fun durationLabel(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "%d h %02d min".format(minutes / 60, minutes % 60)
}

/** "24:16" or "1:12:40". */
internal fun clockLabel(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * A live session. The top is the title, a quiet clock and a hairline of progress; while a rest
 * or the interval timer runs, a band opens there with one big number. Below, with a plan, one
 * exercise at a time and its sets; without one, the climbs. Leaving keeps the session running.
 */
@Composable
fun SessionScreen(
    onLeave: () -> Unit,
    onLogClimb: () -> Unit,
    onFinished: () -> Unit,
    onLogMore: (problemId: Long) -> Unit = {},
    onOpenExercise: (exerciseId: Long) -> Unit = {},
    viewModel: SessionViewModel = hiltViewModel(),
    /** Opens a running rest or timer big from the start; for screenshots. */
    bigTimer: Boolean = false,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val newBests by viewModel.newBests.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val soundsOn by viewModel.timerSounds.collectAsStateWithLifecycle()
    val timerEnds by viewModel.timerEnds.collectAsStateWithLifecycle()
    viewModel.currentItem.collectAsStateWithLifecycle()
    // Two clocks. `now` moves only when a number on screen changes, so the screen recomposes about
    // once a second; `drainNow` moves every tick but is read only where the bands draw their drain.
    var now by remember { mutableLongStateOf(viewModel.now()) }
    var drainNow by remember { mutableLongStateOf(now) }
    LaunchedEffect(Unit) {
        while (true) {
            val tick = viewModel.now()
            viewModel.tickTimer()
            if (viewModel.shownTime(tick) != viewModel.shownTime(now)) now = tick
            drainNow = tick
            delay(100)
        }
    }
    // A rest or timer that starts, pauses or resumes counts from this moment.
    LaunchedEffect(rest, timer) { now = viewModel.now() }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var timerFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var freeTimer by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val sounds = rememberTimerSounds(soundsOn)

    // The screen stays on while something counts down.
    val view = LocalView.current
    val counting = rest != null || timer != null
    DisposableEffect(counting) {
        view.keepScreenOn = counting
        onDispose { view.keepScreenOn = false }
    }

    // A rest beeps its last three seconds, then ends with a buzz.
    val restLeft = rest?.let { secondsLeft(it.endsAtMillis - now) }
    LaunchedEffect(restLeft) {
        when {
            restLeft == null -> Unit

            restLeft <= 0 -> {
                sounds.change()
                buzz(context)
                viewModel.endRest()
            }

            restLeft <= 3 -> sounds.tick()
        }
    }
    // The interval timer beeps the last three seconds of each phase and buzzes when it changes.
    val position = timer?.run?.position(now)
    val phaseSecondsLeft = position?.let { secondsLeft(it.leftMillis) }
    LaunchedEffect(position?.index, timer != null) {
        if (position != null && position.index > 0) {
            sounds.change()
            buzz(context, short = position.phase.kind != IntervalPhase.Kind.WORK)
        }
    }
    LaunchedEffect(timerEnds) {
        if (timerEnds > 0) {
            sounds.change()
            buzz(context)
        }
    }
    LaunchedEffect(phaseSecondsLeft) {
        if (phaseSecondsLeft != null && phaseSecondsLeft in 1..3 && timer?.run?.paused == false) sounds.tick()
    }

    val current = session ?: return
    if (!current.running && !finishing) {
        LaunchedEffect(Unit) { onLeave() }
        return
    }
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    val pendingValues by viewModel.pending.collectAsStateWithLifecycle()
    val previous by viewModel.previous.collectAsStateWithLifecycle()
    val folds = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    var open by remember { mutableStateOf<OpenCell?>(null) }
    var menuFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    // The big timer view opens on a tap of the dock and stays big for the next rest, until made small.
    var big by rememberSaveable { mutableStateOf(bigTimer) }
    val next = viewModel.nextUp(current)
    val runningTimer = timer
    val runningRest = rest
    val done = current.items.count { it.finished }
    val imperial = com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("screen_Session")) {
        // Top: leave (the session keeps running), the title over a quiet clock, + and Finish.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = space.s1, end = space.s1, top = space.s1)) {
            IconButton(onClick = onLeave, modifier = Modifier.testTag("session_leave")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back, the session keeps running")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(current.name, place?.name.takeIf { current.items.isEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        clockLabel(current.durationMillis(now)),
                        "$done of ${current.items.size} done".takeIf { current.items.isNotEmpty() },
                    ).joinToString(" · "),
                    style = CruxTheme.type.code,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("session_clock"),
                )
            }
            IconButton(onClick = { adding = true }, modifier = Modifier.testTag("session_add")) {
                Icon(Icons.Rounded.Add, contentDescription = "Add to this session")
            }
            TextButton(onClick = { finishing = true }, modifier = Modifier.testTag("session_finish")) { Text("Finish") }
        }
        // The plan as a rail: a segment per exercise, filling as its sets go in, green once done.
        // Tap one to scroll to that exercise.
        val listScroll = rememberScrollState()
        val tops = remember { androidx.compose.runtime.mutableStateMapOf<Long, Int>() }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        if (current.items.isNotEmpty()) {
            val success = CruxTheme.colors.success
            SegmentStrip(
                segments = current.items.map { item ->
                    Segment(
                        filled = item.logged.toFloat() / item.target.sets.coerceAtLeast(1),
                        color = if (item.finished) success else colors.primary,
                    )
                },
                modifier = Modifier.padding(horizontal = space.s4).padding(bottom = space.s1).testTag("session_rail"),
                height = 6,
                onPick = { index ->
                    current.items.getOrNull(index)?.let { item -> tops[item.id]?.let { y -> scope.launch { listScroll.animateScrollTo(y) } } }
                },
            )
        }
        HorizontalDivider(color = colors.outlineVariant)

        // Big, a running rest or timer takes the top of the screen; the dock then makes way.
        val nextLine = next?.let { (item, setIndex) -> "${item.exercise.name}, set ${setIndex + 1}" }
        AnimatedVisibility(visible = big && (runningTimer != null || runningRest != null)) {
            Box(Modifier.fillMaxWidth().height(BigTimerHeight)) {
                when {
                    runningTimer != null -> BigInterval(
                        run = runningTimer.run,
                        nowMillis = now,
                        title = current.items.firstOrNull { it.id == runningTimer.itemId }?.let { item ->
                            listOfNotNull(item.exercise.name, item.target.loadKg.takeIf { it != 0.0 }?.let { loadLabel(it, imperial) }).joinToString(" · ")
                        } ?: "Timer of its own",
                        onPause = viewModel::pauseTimer,
                        onResume = viewModel::resumeTimer,
                        onSkip = viewModel::skipPhase,
                        onStop = viewModel::stopTimer,
                        onSmall = { big = false },
                    )

                    runningRest != null -> BigRest(
                        rest = runningRest,
                        nowMillis = now,
                        drainNowMillis = { drainNow },
                        detail = next?.let { (item, setIndex) ->
                            listOfNotNull(
                                "then ${item.exercise.name}",
                                "set ${setIndex + 1} of ${maxOf(item.target.sets, item.logged)}",
                                item.target.prescription(item.exercise.metric, imperial).takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                        } ?: "before your next go",
                        onAdd = viewModel::addRest,
                        onSkip = viewModel::endRest,
                        onSmall = { big = false },
                    )
                }
            }
        }

        // Everything in the session, in order: the exercises, then the climbs.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(listScroll)
                .padding(horizontal = space.s4)
                .padding(top = space.s3, bottom = space.s4)
                .testTag("session_list"),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (current.items.isEmpty()) ClimbTally(current.climbGroups)
            current.items.forEach { item ->
                key(item.id) {
                    // Where the card sits, for the rail to scroll to.
                    Box(Modifier.onGloballyPositioned { tops[item.id] = it.positionInParent().y.toInt() }) {
                        ExerciseCard(
                            item = item,
                            values = { setIndex ->
                                pendingValues
                                viewModel.valuesFor(item, setIndex)
                            },
                            previous = previous[item.exercise.id],
                            newBest = item.id in newBests,
                            folded = folds[item.id] ?: item.finished,
                            open = open,
                            timerRunning = runningTimer?.itemId == item.id,
                            current = next?.first?.id == item.id && runningTimer == null,
                            onToggleFold = { folds[item.id] = !(folds[item.id] ?: item.finished) },
                            onOpen = { open = it },
                            onValues = { setIndex, values -> viewModel.setValues(item, setIndex, values) },
                            onTick = { setIndex ->
                                open = null
                                viewModel.tick(item, setIndex)
                            },
                            onAddSet = { viewModel.addSet(item) },
                            onMenu = { menuFor = item.id },
                        )
                    }
                }
            }
            if (current.climbGroups.isNotEmpty() || current.items.isEmpty()) {
                ClimbsCard(current.climbGroups, onLogMore, onLogClimb)
            }
        }

        // The dock: what's happening now. The next set's Done, a rest, or a running timer.
        Box(Modifier.fillMaxWidth().navigationBarsPadding().testTag("session_dock")) {
            when {
                big && (runningTimer != null || runningRest != null) -> Unit

                runningTimer != null -> Box(Modifier.padding(horizontal = space.s4, vertical = space.s2)) {
                    IntervalDock(
                        run = runningTimer.run,
                        nowMillis = now,
                        onPause = viewModel::pauseTimer,
                        onResume = viewModel::resumeTimer,
                        onSkip = { viewModel.skipPhase(true) },
                        onStop = viewModel::stopTimer,
                        onOpen = { big = true },
                    )
                }

                runningRest != null -> Box(Modifier.padding(horizontal = space.s4, vertical = space.s2)) {
                    RestDock(
                        rest = runningRest,
                        nowMillis = now,
                        drainNowMillis = { drainNow },
                        label = nextLine?.let { "Rest · then $it" } ?: "Rest before your next go",
                        onAdd = { viewModel.addRest(30) },
                        onSkip = viewModel::endRest,
                        onOpen = { big = true },
                    )
                }

                else -> Box(Modifier.padding(horizontal = space.s4, vertical = space.s2)) {
                    when {
                        current.items.isEmpty() -> CruxButton(
                            "Log climb",
                            onLogClimb,
                            icon = Icons.Rounded.Add,
                            size = CruxButtonSize.Large,
                            modifier = Modifier.fillMaxWidth().testTag("session_dock_log_climb"),
                        )

                        next == null -> CruxButton(
                            "Every set in · Finish",
                            { finishing = true },
                            icon = Icons.Rounded.Check,
                            size = CruxButtonSize.Large,
                            variant = CruxButtonVariant.Tonal,
                            modifier = Modifier.fillMaxWidth().testTag("session_dock_finish"),
                        )

                        next.first.exercise.metric.usesIntervals -> CruxButton(
                            "Start timer · ${next.first.exercise.name}, cycle ${next.second + 1}",
                            { timerFor = next.first.id },
                            icon = Icons.Rounded.PlayArrow,
                            size = CruxButtonSize.Large,
                            modifier = Modifier.fillMaxWidth().testTag("session_dock_timer"),
                        )

                        else -> CruxButton(
                            "Done · ${next.first.exercise.name}, set ${next.second + 1}",
                            {
                                open = null
                                viewModel.tick(next.first, next.second)
                            },
                            icon = Icons.Rounded.Check,
                            size = CruxButtonSize.Large,
                            modifier = Modifier.fillMaxWidth().testTag("session_done_set"),
                        )
                    }
                }
            }
        }
    }

    current.items.firstOrNull { it.id == menuFor }?.let { item ->
        ExerciseMenuSheet(
            item = item,
            onChangeTargets = {
                menuFor = null
                editingTarget = item.id
            },
            onStartTimer = {
                menuFor = null
                timerFor = item.id
            }.takeIf { item.exercise.metric.usesIntervals && item.nextSet != null && runningTimer == null },
            onSkip = {
                menuFor = null
                viewModel.skipExercise(item)
            }.takeIf { item.nextSet != null },
            onRemove = {
                menuFor = null
                viewModel.removeExercise(item)
            }.takeIf { item.sets.isEmpty() },
            onDismiss = { menuFor = null },
            onAbout = {
                menuFor = null
                onOpenExercise(item.exercise.id)
            },
        )
    }
    if (adding) {
        AddToSessionSheet(
            onLogClimb = {
                adding = false
                onLogClimb()
            },
            onAddExercise = {
                adding = false
                picking = true
            },
            onTimer = {
                adding = false
                freeTimer = true
            }.takeIf { runningTimer == null },
            onDismiss = { adding = false },
        )
    }
    if (picking) {
        ExercisePicker(
            exercises = library,
            onPick = {
                viewModel.addExercise(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
    // Changing an exercise for this session only: the plan stays as it is.
    current.items.firstOrNull { it.id == editingTarget }?.let { edited ->
        var target by remember(edited.id) { mutableStateOf(edited.target) }
        var alsoPlan by remember(edited.id) { mutableStateOf(false) }
        val inPlan = viewModel.inPlan(edited)
        com.hardtekpt.crux.ui.train.TargetSheet(
            exercise = edited.exercise,
            target = target,
            onChange = { target = it.copy(sets = it.sets.coerceAtLeast(maxOf(1, edited.logged))) },
            onDismiss = {
                viewModel.updateTarget(edited, target, alsoPlan)
                editingTarget = null
            },
            note = when {
                !inPlan -> "For this session only."
                alsoPlan -> "For this session and in ${current.name}, so next time starts here."
                else -> "For this session only. Your plan stays as it is."
            },
            // Progressing a load usually happens mid-session: save it to the plan in the same step.
            extra = if (inPlan) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoPlan = !alsoPlan }
                            .testTag("target_also_plan"),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Also change the plan", style = MaterialTheme.typography.titleSmall)
                            Text(current.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = alsoPlan, onCheckedChange = { alsoPlan = it })
                    }
                }
            } else {
                null
            },
        )
    }
    current.items.firstOrNull { it.id == timerFor }?.let { item ->
        TimerSetupSheet(
            initial = viewModel.timerFor(item),
            title = item.exercise.name,
            onStart = {
                viewModel.startTimer(it, item.id)
                timerFor = null
            },
            onDismiss = { timerFor = null },
        )
    }
    if (freeTimer) {
        TimerSetupSheet(
            initial = viewModel.timerFor(null),
            title = null,
            onStart = {
                viewModel.startTimer(it, null)
                freeTimer = false
            },
            onDismiss = { freeTimer = false },
        )
    }
    if (finishing) {
        FinishSheet(
            session = current,
            nowMillis = now,
            onSave = { effort, notes ->
                viewModel.finish(effort, notes) {
                    finishing = false
                    onFinished()
                }
            },
            onDiscard = {
                viewModel.discard {
                    finishing = false
                    onFinished()
                }
            },
            onDismiss = { finishing = false },
        )
    }
}

/**
 * What an exercise asks for, as a sentence: the bold part is the work, then the load and rest.
 * `6 hangs of 10 s with +17.5 kg, 3 min rest`, `8 repeats of 20 s, 10 s rest, 2 cycles, 1 min between`.
 */
internal fun goalSentence(item: SessionItem, imperial: Boolean): AnnotatedString {
    val t = item.target
    val metric = item.exercise.metric
    val load = if (metric.usesLoad && t.loadKg != 0.0) " with ${com.hardtekpt.crux.data.model.signedLoad(t.loadKg, imperial)}" else ""
    return buildAnnotatedString {
        if (metric.usesIntervals) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("${t.reps} ${if (t.reps == 1) "repeat" else "repeats"} of ${formatDuration(t.seconds)}")
            }
            append(load)
            if (t.repRestSeconds > 0) append(", ${formatDuration(t.repRestSeconds)} rest")
            if (t.sets > 1) append(", ${t.sets} cycles" + (if (t.restSeconds > 0) ", ${formatDuration(t.restSeconds)} between" else ""))
        } else {
            val noun = when {
                metric.usesTime && item.exercise.category == ExerciseCategory.FINGERS -> if (t.sets == 1) "hang" else "hangs"
                else -> if (t.sets == 1) "set" else "sets"
            }
            val work = if (metric.usesTime) formatDuration(t.seconds) else "${t.reps} reps"
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("${t.sets} $noun of $work") }
            append(load)
            if (t.restSeconds > 0 && t.sets > 1) append(", ${formatDuration(t.restSeconds)} rest")
        }
    }
}

/** `5 reps · +10 kg`, `10 s · +17.5 kg`, `6 × 7 s` (no load when there is none). */
internal fun describeSet(metric: com.hardtekpt.crux.data.model.MetricType, reps: Int?, seconds: Int?, loadKg: Double?, imperial: Boolean): String {
    val load = loadKg?.takeIf { metric.usesLoad && it != 0.0 }?.let { com.hardtekpt.crux.data.model.signedLoad(it, imperial) }
    val work = when {
        metric.usesIntervals -> "${reps ?: 0} × ${formatDuration(seconds ?: 0)}"
        metric.usesTime -> formatDuration(seconds ?: 0)
        else -> "${reps ?: 0} reps"
    }
    return listOfNotNull(work, load).joinToString(" · ")
}

/** Climbs, sends and the hardest send so far, as three plain figures. */
@Composable
private fun ClimbTally(climbs: List<Climb>) {
    val sent = climbs.filter { it.style.isSend }
    val hardest = sent.maxByOrNull { it.gradeIndex }?.grade ?: "–"
    Row(
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).testTag("session_tally"),
    ) {
        TallyFigure(climbs.size.toString(), if (climbs.size == 1) "climb" else "climbs", Modifier.weight(1f))
        TallyFigure(sent.size.toString(), "sent", Modifier.weight(1f))
        TallyFigure(hardest, "hardest send", Modifier.weight(1f), accent = true)
    }
}

@Composable
private fun TallyFigure(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) colors.primaryContainer else colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, if (accent) colors.primaryContainer else colors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, style = CruxTheme.type.metricMedium.copy(fontSize = 22.sp), color = if (accent) colors.onPrimaryContainer else colors.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (accent) colors.onPrimaryContainer else colors.onSurfaceVariant)
    }
}

/** A climb logged in the session; tapping it logs more goes on it. */
@Composable
internal fun SessionClimbLine(climb: Climb, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val sent = climb.style.isSend
    val tape = climb.gradeColour?.let { argb(it) } ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Log more goes", onClick = onClick) else Modifier)
            .padding(vertical = 9.dp)
            .testTag("session_climb"),
    ) {
        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(tape))
        Text(climb.grade, style = CruxTheme.type.grade, modifier = Modifier.width(44.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(climb.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                climb.style.label + if (!climb.style.singleAttempt) " · ${climb.attempts} ${if (climb.attempts == 1) "go" else "goes"}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (sent) CruxTheme.colors.success else colors.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Text(
                "+ go",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(CruxTheme.size.borderHairline, colors.outline, CircleShape)
                    .clickable(onClickLabel = "Log another go", onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(exercises: List<Exercise>, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("exercise_picker"),
    ) {
        Column(Modifier.padding(horizontal = CruxTheme.space.s4).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            Text("Add an exercise", style = MaterialTheme.typography.headlineSmall)
            CruxTextField(label = "", value = query, onValueChange = { query = it }, placeholder = "Search the library")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), contentPadding = PaddingValues(bottom = CruxTheme.space.s6)) {
                items(exercises.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { it.id }) { exercise ->
                    CruxListRow(
                        title = exercise.name,
                        supporting = exercise.metric.label,
                        onClick = { onPick(exercise) },
                        modifier = Modifier.testTag("pick_${exercise.name}"),
                    )
                }
            }
        }
    }
}

/** How it went, how hard it felt and a note; then save, or throw it away after asking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishSheet(session: Session, nowMillis: Long, onSave: (Int?, String) -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    var effort by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val space = CruxTheme.space
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("finish_sheet"),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = space.s4).padding(bottom = space.s4).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            Text("Finish session", style = MaterialTheme.typography.headlineSmall)
            Eyebrow(session.name)
            Text(clockLabel(session.durationMillis(nowMillis)), style = CruxTheme.type.metricMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min)) {
                if (session.items.isNotEmpty()) TallyFigure("${session.setsDone}/${session.setsPlanned}", "sets done", Modifier.weight(1f))
                TallyFigure(session.climbGroups.size.toString(), if (session.climbGroups.size == 1) "climb" else "climbs", Modifier.weight(1f))
                TallyFigure(session.climbGroups.count { it.style.isSend }.toString(), "sent", Modifier.weight(1f))
            }
            EffortScale(effort, { effort = it })
            CruxTextField(label = "Notes", value = notes, onValueChange = {
                notes = it.take(1000)
            }, placeholder = "How it went", singleLine = false, minLines = 2)
            CruxButton("Save session", {
                onSave(effort, notes)
            }, icon = Icons.Rounded.Check, size = CruxButtonSize.Large, modifier = Modifier.fillMaxWidth().testTag("session_save"))
            CruxButton("Discard session", {
                confirmDiscard = true
            }, variant = CruxButtonVariant.Text, modifier = Modifier.fillMaxWidth().testTag("session_discard"))
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Discard this session?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Its sets are deleted. Climbs you logged stay in the journal.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = onDiscard, modifier = Modifier.testTag("confirm_discard")) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}
