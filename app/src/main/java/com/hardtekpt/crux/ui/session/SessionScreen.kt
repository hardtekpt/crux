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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.data.model.formatLoad
import com.hardtekpt.crux.data.model.loadUnit
import com.hardtekpt.crux.data.model.loadValue
import com.hardtekpt.crux.data.model.poundsToKg
import com.hardtekpt.crux.data.prefs.UnitSystem
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

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    exercises: ExerciseRepository,
    private val places: PlaceRepository,
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

    /** The exercise on screen; null follows the first one with sets still to do. */
    private val _currentItem = MutableStateFlow<Long?>(null)
    val currentItem: StateFlow<Long?> = _currentItem.asStateFlow()

    private val _draft = MutableStateFlow(SetDraft())
    val draft: StateFlow<SetDraft> = _draft.asStateFlow()

    private val _rest = MutableStateFlow<Rest?>(null)
    val rest: StateFlow<Rest?> = _rest.asStateFlow()

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

    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            val id = sessions.addExercise(sessionId, exercise)
            _currentItem.value = id
        }
    }

    fun finish(effort: Int?, notes: String, onDone: () -> Unit) {
        viewModelScope.launch {
            sessions.finish(sessionId, effort, notes)
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            sessions.discard(sessionId)
            onDone()
        }
    }
}

private val MonoLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)

/** "24:16" or "1:12:40". */
internal fun clockLabel(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * A live session. With a plan: one exercise at a time, its sets against their targets, a rest
 * timer after each set. Without one: the climbs logged so far and any exercises added. Both can
 * log climbs and add exercises; leaving keeps the session running.
 */
@Composable
fun SessionScreen(onLeave: () -> Unit, onLogClimb: () -> Unit, onFinished: () -> Unit, viewModel: SessionViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    viewModel.currentItem.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(viewModel.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = viewModel.now()
            delay(250)
        }
    }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // The rest ends with a buzz, and the next set is ready.
    val restLeft = rest?.let { ((it.endsAtMillis - now) / 1000).toInt() + 1 }
    LaunchedEffect(restLeft != null && restLeft <= 0) {
        if (restLeft != null && restLeft <= 0) {
            buzz(context)
            viewModel.endRest()
        }
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

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("screen_Session")) {
        // Top: leave (the session keeps running), what and where, the clock, Finish.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = space.s1, vertical = space.s1)) {
            IconButton(onClick = onLeave, modifier = Modifier.testTag("session_leave")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back, the session keeps running")
            }
            Column(Modifier.weight(1f)) {
                Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = listOfNotNull(
                    place?.name,
                    place?.sections?.firstOrNull { it.id == current.sectionId }?.name?.takeIf {
                        place?.hasSeveralTypes ==
                            true
                    },
                )
                Text(
                    where.joinToString(" · ").ifEmpty { "No place" }.uppercase(),
                    style = MonoLabel,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                clockLabel(current.durationMillis(now)),
                style = CruxTheme.type.grade.copy(fontSize = 16.sp),
                color = colors.primary,
                modifier = Modifier.testTag("session_clock"),
            )
            TextButton(onClick = { finishing = true }, modifier = Modifier.testTag("session_finish")) { Text("Finish") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).testTag("session_list"),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (current.items.isNotEmpty() && item != null) {
                item(key = "rail") { ItemRail(current, item, onPick = viewModel::show) }
                item(key = "exercise") {
                    ExercisePanel(
                        item = item,
                        draft = draft,
                        onDraft = viewModel::updateDraft,
                        onDone = { viewModel.doneSet(item) },
                        onSkip = { viewModel.skipSet(item) },
                        onUndo = { setIndex -> viewModel.undoSet(item, setIndex) },
                        onPrevious = current.items.getOrNull(current.items.indexOf(item) - 1)?.let { prev -> { viewModel.show(prev.id) } },
                        onNext = current.items.getOrNull(current.items.indexOf(item) + 1)?.let { next -> { viewModel.show(next.id) } },
                    )
                }
            }
            rest?.let { r ->
                item(key = "rest") {
                    RestBar(
                        secondsLeft = (((r.endsAtMillis - now) / 1000).toInt() + 1).coerceAtLeast(0),
                        onAdd = { viewModel.addRest(30) },
                        onSkip = viewModel::endRest,
                    )
                }
            }
            // Climbs logged in this session, with a running tally.
            item(key = "climbs_head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Climbs this session", Modifier.weight(1f))
                    CruxButton(
                        "Log climb",
                        onLogClimb,
                        variant = CruxButtonVariant.Tonal,
                        size = CruxButtonSize.Small,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("session_log_climb"),
                    )
                }
            }
            item(key = "tally") { ClimbTally(current.climbs) }
            if (current.climbs.isNotEmpty()) {
                item(key = "climbs") {
                    FlatPanel {
                        current.climbs.sortedByDescending { it.id }.forEachIndexed { index, climb ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                            SessionClimbLine(climb)
                        }
                    }
                }
            }
            // Exercises: without a plan, the ones added; with a plan, the add button.
            item(key = "exercises_head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(if (current.hasPlan) "Add to this session" else "Exercises", Modifier.weight(1f))
                    CruxButton(
                        "Exercise",
                        {
                            picking = true
                        },
                        variant = CruxButtonVariant.Text,
                        size = CruxButtonSize.Small,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag(
                            "session_add_exercise",
                        ),
                    )
                }
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

private fun buzz(context: Context) {
    runCatching {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }
}

/** One segment per exercise, filled by how much of it is done; tap one to go there. */
@Composable
private fun ItemRail(session: Session, current: SessionItem, onPick: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().testTag("session_rail")) {
            session.items.forEach { item ->
                val done = if (item.target.sets == 0) 0f else (item.logged.toFloat() / item.target.sets).coerceAtMost(1f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (item.id == current.id) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceContainerHighest)
                        .clickable { onPick(item.id) },
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(colors.primary))
                }
            }
        }
        val index = session.items.indexOf(current)
        val next = session.items.getOrNull(index + 1)
        Row {
            Text(
                listOfNotNull("Exercise ${index + 1} of ${session.items.size}", current.blockName.takeIf { it.isNotBlank() }).joinToString(" · ").uppercase(),
                style = MonoLabel,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            next?.let {
                Text(
                    "NEXT: ${it.exercise.name.uppercase()}",
                    style = MonoLabel,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The current exercise: its target, a row per set, wheels for the set being logged and Done. */
@Composable
private fun ExercisePanel(
    item: SessionItem,
    draft: SetDraft,
    onDraft: ((SetDraft) -> SetDraft) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onUndo: (Int) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val space = CruxTheme.space
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL
    val metric = item.exercise.metric
    Column(verticalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.testTag("session_exercise")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.exercise.name,
                    style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
                    modifier = Modifier.testTag("session_exercise_name"),
                )
                Text(
                    ("Target " + item.target.prescription(metric, imperial) + (item.target.restLabel()?.let { " · rest $it" } ?: "")).uppercase(),
                    style = MonoLabel,
                    color = colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous exercise")
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null, modifier = Modifier.testTag("session_next")) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next exercise")
            }
        }
        // A row per set: logged ones with what was done (tap to undo), the next one lit.
        FlatPanel(Modifier.testTag("session_sets")) {
            val next = item.nextSet
            (0 until maxOf(item.target.sets, item.logged)).forEachIndexed { index, setIndex ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                val logged = item.sets.firstOrNull { it.setIndex == setIndex }
                val isNext = setIndex == next
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.s3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isNext) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(enabled = logged != null) { onUndo(setIndex) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("session_set_$setIndex"),
                ) {
                    Text("SET ${setIndex + 1}", style = MonoLabel, color = colors.onSurfaceVariant, modifier = Modifier.width(52.dp))
                    Text(
                        when {
                            logged?.skipped == true -> "Skipped"
                            logged != null -> describeSet(metric, logged.reps, logged.seconds, logged.loadKg, imperial)
                            else -> describeSet(metric, item.target.reps, item.target.seconds, item.target.loadKg, imperial)
                        },
                        style = CruxTheme.type.gradeSmall,
                        color = if (logged == null && !isNext) colors.onSurfaceVariant else colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        logged?.skipped == true -> Text("–", color = colors.onSurfaceVariant)

                        logged != null -> Box(
                            Modifier.size(22.dp).clip(CircleShape).background(CruxTheme.colors.success),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = "Done", tint = CruxTheme.colors.onSuccess, modifier = Modifier.size(14.dp))
                        }

                        else -> Box(Modifier.size(22.dp).border(1.5.dp, colors.outline, CircleShape))
                    }
                }
            }
        }
        if (item.nextSet != null && draft.itemId == item.id) {
            // The set being logged: starts at the target, change what went differently.
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (metric.usesReps || metric.usesIntervals) {
                    CruxStepper(draft.reps, { v ->
                        onDraft { it.copy(reps = v) }
                    }, 0..200, if (metric.usesIntervals) "reps" else "reps", testTagPrefix = "set_reps")
                }
                if (metric.usesTime || metric.usesIntervals) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
                CruxButton(
                    text = "Done set ${draft.setIndex + 1}",
                    onClick = onDone,
                    icon = Icons.Rounded.Check,
                    size = CruxButtonSize.Large,
                    modifier = Modifier.weight(1f).testTag("session_done_set"),
                )
                CruxButton("Skip", onSkip, variant = CruxButtonVariant.Text, modifier = Modifier.testTag("session_skip_set"))
            }
        } else if (item.nextSet == null) {
            Text("All sets in. Tap a set to undo it, or go to the next exercise.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** `5 reps · +10 kg`, `10 s · +17.5 kg`, `6 × 7 s`. */
internal fun describeSet(metric: com.hardtekpt.crux.data.model.MetricType, reps: Int?, seconds: Int?, loadKg: Double?, imperial: Boolean): String {
    val load = loadKg?.takeIf { metric.usesLoad }?.let { com.hardtekpt.crux.data.model.signedLoad(it, imperial) }
    val work = when {
        metric.usesIntervals -> "${reps ?: 0} × ${formatDuration(seconds ?: 0)}"
        metric.usesTime -> formatDuration(seconds ?: 0)
        else -> "${reps ?: 0} reps"
    }
    return listOfNotNull(work, load).joinToString(" · ")
}

/** The rest between sets, counting down, with +30 s and Skip. */
@Composable
private fun RestBar(secondsLeft: Int, onAdd: () -> Unit, onSkip: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rest = colors.tertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rest.copy(alpha = 0.14f))
            .border(CruxTheme.size.borderHairline, rest, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("session_rest"),
    ) {
        Text(
            clockLabel(secondsLeft * 1000L),
            style = CruxTheme.type.grade.copy(fontSize = 22.sp),
            color = rest,
            modifier = Modifier.testTag("session_rest_left"),
        )
        Text("Rest", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        CruxButton("+30 s", onAdd, variant = CruxButtonVariant.Outlined, size = CruxButtonSize.Small)
        CruxButton("Skip", onSkip, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small, modifier = Modifier.testTag("session_rest_skip"))
    }
}

/** Climbs, sends and the hardest send so far. */
@Composable
private fun ClimbTally(climbs: List<Climb>) {
    val sent = climbs.filter { it.style.isSend }
    val hardest = sent.maxByOrNull { it.gradeIndex }?.grade ?: "–"
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.height(IntrinsicSize.Min).testTag("session_tally")) {
        TallyFigure(climbs.size.toString(), if (climbs.size == 1) "climb" else "climbs", Modifier.weight(1f))
        TallyFigure(sent.size.toString(), "sent", Modifier.weight(1f))
        TallyFigure(hardest, "hardest send", Modifier.weight(1f))
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
private fun FlatPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = CruxTheme.space.s3),
    ) { content() }
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
