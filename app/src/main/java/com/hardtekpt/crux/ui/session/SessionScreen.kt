package com.hardtekpt.crux.ui.session

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.material.icons.rounded.Timer
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the set being logged holds before Done: starts at the target, or at the last set's values. */
data class SetDraft(val itemId: Long = 0, val setIndex: Int = 0, val reps: Int = 0, val seconds: Int = 0, val loadKg: Double = 0.0)

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
    preferences: UserPreferencesRepository,
    private val clock: Clock,
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

    /** The exercise on screen; null follows the first one with sets still to do. */
    private val _currentItem = MutableStateFlow<Long?>(null)
    val currentItem: StateFlow<Long?> = _currentItem.asStateFlow()

    private val _draft = MutableStateFlow(SetDraft())
    val draft: StateFlow<SetDraft> = _draft.asStateFlow()

    private val _rest = MutableStateFlow<Rest?>(null)
    val rest: StateFlow<Rest?> = _rest.asStateFlow()

    private val _timer = MutableStateFlow<SessionTimer?>(null)
    val timer: StateFlow<SessionTimer?> = _timer.asStateFlow()

    /** Counts timers that ran to the end, so the screen can sound the finish. */
    private val _timerEnds = MutableStateFlow(0)
    val timerEnds: StateFlow<Int> = _timerEnds.asStateFlow()

    /** The last free timer, so the next one starts the same. */
    private var lastFreeSpec = IntervalSpec.TABATA

    fun now(): Long = clock.millis()

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

    fun undoSet(item: SessionItem, setIndex: Int) {
        viewModelScope.launch { sessions.undoSet(item.id, setIndex) }
        _currentItem.value = item.id
        _draft.value = SetDraft()
    }

    fun addRest(seconds: Int) = _rest.update { it?.copy(endsAtMillis = it.endsAtMillis + seconds * 1000L, totalSeconds = it.totalSeconds + seconds) }
    fun endRest() {
        _rest.value = null
    }

    /** The timer an interval exercise asks for: its targets, for the cycles still to do. */
    fun timerFor(item: SessionItem?): IntervalSpec {
        if (item == null || !item.exercise.metric.usesIntervals) return lastFreeSpec
        val t = item.target
        return IntervalSpec(
            prepSeconds = item.exercise.intervals?.prepSeconds ?: 10,
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
fun SessionScreen(onLeave: () -> Unit, onLogClimb: () -> Unit, onFinished: () -> Unit, viewModel: SessionViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val soundsOn by viewModel.timerSounds.collectAsStateWithLifecycle()
    val timerEnds by viewModel.timerEnds.collectAsStateWithLifecycle()
    viewModel.currentItem.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(viewModel.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = viewModel.now()
            viewModel.tickTimer()
            delay(100)
        }
    }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var settingUpTimer by rememberSaveable { mutableStateOf(false) }
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
    val item = viewModel.shownItem(current)
    LaunchedEffect(item?.id, item?.logged) { item?.let(viewModel::prepare) }
    val itemSegments = current.items.map { Segment(if (it.target.sets == 0) 0f else it.logged.toFloat() / it.target.sets, colors.primary) }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("screen_Session")) {
        // Top: leave (the session keeps running), the title, a quiet clock, Finish.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = space.s1, end = space.s1, top = space.s1)) {
            IconButton(onClick = onLeave, modifier = Modifier.testTag("session_leave")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back, the session keeps running")
            }
            Text(
                listOfNotNull(current.name, place?.name.takeIf { current.items.isEmpty() }).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                clockLabel(current.durationMillis(now)),
                style = CruxTheme.type.code.copy(fontSize = 14.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = space.s2).testTag("session_clock"),
            )
            TextButton(onClick = { finishing = true }, modifier = Modifier.testTag("session_finish")) { Text("Finish") }
        }

        // The plan's progress, one segment per exercise, always in view; tap one to go there.
        if (current.items.isNotEmpty()) {
            SegmentStrip(
                itemSegments,
                Modifier.padding(horizontal = space.s4).padding(bottom = space.s2).testTag("session_rail"),
                onPick = { index -> viewModel.show(current.items[index].id) },
            )
        }

        // Under it, the band opens while the timer or a rest runs.
        val runningTimer = timer
        val runningRest = rest
        when {
            runningTimer != null -> IntervalBand(
                run = runningTimer.run,
                nowMillis = now,
                onPause = viewModel::pauseTimer,
                onResume = viewModel::resumeTimer,
                onStop = viewModel::stopTimer,
            )

            runningRest != null -> RestBand(
                rest = runningRest,
                nowMillis = now,
                next = item?.let { i -> i.nextSet?.let { "Set ${it + 1} next" } ?: "Next: ${i.exercise.name}" } ?: "",
                segments = emptyList(),
                onAdd = { viewModel.addRest(30) },
                onSkip = viewModel::endRest,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4)
                .padding(top = space.s4, bottom = space.s4)
                .navigationBarsPadding()
                .testTag("session_list"),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (item != null) {
                val index = current.items.indexOf(item)
                ExerciseView(
                    item = item,
                    step = listOfNotNull("${index + 1} of ${current.items.size}", item.blockName.takeIf { it.isNotBlank() }).joinToString(" · "),
                    draft = draft,
                    timerRunning = runningTimer?.itemId == item.id,
                    onDraft = viewModel::updateDraft,
                    onDone = { viewModel.doneSet(item) },
                    onSkip = { viewModel.skipSet(item) },
                    onUndo = { setIndex -> viewModel.undoSet(item, setIndex) },
                    onStartTimer = { settingUpTimer = true },
                    onPrevious = current.items.getOrNull(index - 1)?.let { prev -> { viewModel.show(prev.id) } },
                    onNext = current.items.getOrNull(index + 1)?.let { next -> { viewModel.show(next.id) } },
                )
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(top = space.s2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (current.climbs.size) {
                            0 -> "No climbs logged"
                            1 -> "1 climb logged"
                            else -> "${current.climbs.size} climbs logged"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    CruxButton(
                        "Log climb",
                        onLogClimb,
                        variant = CruxButtonVariant.Text,
                        size = CruxButtonSize.Small,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("session_log_climb"),
                    )
                }
                current.climbs.sortedByDescending { it.id }.forEach { SessionClimbLine(it) }
            } else {
                // A climbing day: the tally, the climbs, Log climb.
                ClimbTally(current.climbs)
                if (current.climbs.isEmpty()) {
                    Text(
                        "Climbs you log now land in this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Column { current.climbs.sortedByDescending { it.id }.forEach { SessionClimbLine(it) } }
                }
                CruxButton(
                    "Log climb",
                    onLogClimb,
                    icon = Icons.Rounded.Add,
                    size = CruxButtonSize.Large,
                    modifier = Modifier.fillMaxWidth().testTag("session_log_climb"),
                )
            }
            // Quiet extras: add an exercise, or run a timer of its own.
            Row(horizontalArrangement = Arrangement.spacedBy(space.s1)) {
                CruxButton(
                    "Exercise",
                    { picking = true },
                    variant = CruxButtonVariant.Text,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.testTag("session_add_exercise"),
                )
                CruxButton(
                    "Timer",
                    { freeTimer = true },
                    variant = CruxButtonVariant.Text,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.Timer,
                    enabled = runningTimer == null,
                    modifier = Modifier.testTag("session_timer"),
                )
            }
        }
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
    if (settingUpTimer && item != null) {
        TimerSetupSheet(
            initial = viewModel.timerFor(item),
            title = item.exercise.name,
            onStart = {
                viewModel.startTimer(it, item.id)
                settingUpTimer = false
            },
            onDismiss = { settingUpTimer = false },
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

/** One exercise: where it sits in the plan, its name, the goal as a sentence, its sets, then Done. */
@Composable
private fun ExerciseView(
    item: SessionItem,
    step: String,
    draft: SetDraft,
    timerRunning: Boolean,
    onDraft: ((SetDraft) -> SetDraft) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onUndo: (Int) -> Unit,
    onStartTimer: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val space = CruxTheme.space
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL
    val metric = item.exercise.metric
    Column(verticalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.testTag("session_exercise")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (onPrevious != null || onNext != null) {
                    SmallArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous exercise", onPrevious)
                    SmallArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next exercise", onNext, Modifier.testTag("session_next"))
                }
            }
            Text(
                item.exercise.name,
                style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
                modifier = Modifier.testTag("session_exercise_name"),
            )
            Text(goalSentence(item, imperial), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }

        // A row per set (a cycle, for intervals): done ones ticked (tap to undo), the next one lit.
        Column(Modifier.testTag("session_sets")) {
            val next = item.nextSet
            (0 until maxOf(item.target.sets, item.logged)).forEach { setIndex ->
                val logged = item.sets.firstOrNull { it.setIndex == setIndex }
                SetLine(
                    number = setIndex + 1,
                    text = when {
                        logged?.skipped == true -> "Skipped"
                        logged != null -> describeSet(metric, logged.reps, logged.seconds, logged.loadKg, imperial)
                        else -> describeSet(metric, item.target.reps, item.target.seconds, item.target.loadKg, imperial)
                    },
                    state = when {
                        logged?.skipped == true -> SetState.SKIPPED
                        logged != null -> SetState.DONE
                        setIndex == next -> SetState.NOW
                        else -> SetState.TO_DO
                    },
                    onUndo = { onUndo(setIndex) }.takeIf { logged != null },
                    modifier = Modifier.testTag("session_set_$setIndex"),
                )
            }
        }

        when {
            item.nextSet == null -> Text(
                "All sets in. Tap a set to undo it.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )

            draft.itemId != item.id || timerRunning -> Unit

            else -> {
                // What this set holds, starting at the target: change what went differently.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space.s2, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (metric.usesReps) {
                        CruxStepper(draft.reps, { v -> onDraft { it.copy(reps = v) } }, 0..200, "reps", testTagPrefix = "set_reps")
                    }
                    if (metric.usesTime && !metric.usesLoad) {
                        CruxStepper(draft.seconds, { v -> onDraft { it.copy(seconds = v) } }, 0..3600, "s", testTagPrefix = "set_seconds")
                    }
                    if (metric.usesLoad) {
                        val step = if (imperial) poundsToKg(2.5) else 1.25
                        val shown = loadValue(draft.loadKg, imperial)
                        CruxValueStepper(
                            display = (
                                if (draft.loadKg > 0) {
                                    "+"
                                } else if (draft.loadKg < 0) {
                                    "−"
                                } else {
                                    ""
                                }
                                ) + formatLoad(draft.loadKg, imperial),
                            unit = loadUnit(imperial),
                            canDecrease = shown > -100,
                            canIncrease = shown < 300,
                            onDecrease = { onDraft { it.copy(loadKg = it.loadKg - step) } },
                            onIncrease = { onDraft { it.copy(loadKg = it.loadKg + step) } },
                            testTagPrefix = "set_load",
                        )
                    }
                }
                if (metric.usesIntervals) {
                    CruxButton(
                        text = "Start timer · cycle ${draft.setIndex + 1}",
                        onClick = onStartTimer,
                        icon = Icons.Rounded.PlayArrow,
                        size = CruxButtonSize.Large,
                        modifier = Modifier.fillMaxWidth().testTag("session_start_timer"),
                    )
                } else {
                    CruxButton(
                        text = "Done · set ${draft.setIndex + 1}",
                        onClick = onDone,
                        icon = Icons.Rounded.Check,
                        size = CruxButtonSize.Large,
                        modifier = Modifier.fillMaxWidth().testTag("session_done_set"),
                    )
                }
                CruxButton(
                    "Skip this ${if (metric.usesIntervals) "cycle" else "set"}",
                    onSkip,
                    variant = CruxButtonVariant.Text,
                    size = CruxButtonSize.Small,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("session_skip_set"),
                )
            }
        }
    }
}

private enum class SetState { DONE, NOW, TO_DO, SKIPPED }

/** A set as a dot and a value: ticked when done, ringed in the accent when it's next. */
@Composable
private fun SetLine(number: Int, text: String, state: SetState, onUndo: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val success = CruxTheme.colors.success
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onUndo != null) Modifier.clickable(onClickLabel = "Undo", onClick = onUndo) else Modifier)
            .padding(vertical = 7.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (state == SetState.DONE) success else Color.Transparent)
                .border(
                    1.5.dp,
                    when (state) {
                        SetState.DONE -> success
                        SetState.NOW -> colors.primary
                        else -> colors.outlineVariant
                    },
                    CircleShape,
                ),
        ) {
            when (state) {
                SetState.DONE -> Icon(Icons.Rounded.Check, contentDescription = "Done", tint = CruxTheme.colors.onSuccess, modifier = Modifier.size(15.dp))

                SetState.SKIPPED -> Text("–", style = SetValue.copy(fontSize = 12.sp), color = colors.outline)

                else -> Text(
                    number.toString(),
                    style = SetValue.copy(fontSize = 11.sp),
                    color = if (state == SetState.NOW) colors.primary else colors.outline,
                )
            }
        }
        Text(
            text,
            style = if (state == SetState.NOW) SetValue.copy(fontWeight = FontWeight.SemiBold) else SetValue,
            color = when (state) {
                SetState.NOW -> colors.onSurface
                SetState.DONE -> colors.onSurfaceVariant
                else -> colors.outline
            },
        )
    }
}

@Composable
private fun SmallArrow(icon: ImageVector, description: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    IconButton(onClick = { onClick?.invoke() }, enabled = onClick != null, modifier = modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
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
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s5), modifier = Modifier.testTag("session_tally")) {
        PlainFigure(climbs.size.toString(), if (climbs.size == 1) "climb" else "climbs")
        PlainFigure(sent.size.toString(), "sent")
        PlainFigure(hardest, "hardest send")
    }
}

@Composable
private fun PlainFigure(value: String, label: String) {
    Column {
        Text(value, style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 28.sp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TallyFigure(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, style = CruxTheme.type.metricMedium.copy(fontSize = 22.sp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun SessionClimbLine(climb: Climb) {
    val colors = MaterialTheme.colorScheme
    val sent = climb.style.isSend
    val tape = climb.gradeColour?.let { argb(it) } ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp).testTag("session_climb"),
    ) {
        Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(tape))
        Text(climb.grade, style = CruxTheme.type.grade, modifier = Modifier.width(44.dp), maxLines = 1)
        Text(climb.displayName(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            climb.style.label + if (!climb.style.singleAttempt) " · ${climb.attempts}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (sent) CruxTheme.colors.success else colors.onSurfaceVariant,
        )
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
                TallyFigure(session.climbs.size.toString(), if (session.climbs.size == 1) "climb" else "climbs", Modifier.weight(1f))
                TallyFigure(session.climbs.count { it.style.isSend }.toString(), "sent", Modifier.weight(1f))
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
