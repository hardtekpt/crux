package com.hardtekpt.crux.ui.timer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.session.IntervalFields
import com.hardtekpt.crux.ui.session.IntervalPhase
import com.hardtekpt.crux.ui.session.IntervalPosition
import com.hardtekpt.crux.ui.session.IntervalRun
import com.hardtekpt.crux.ui.session.IntervalSpec
import com.hardtekpt.crux.ui.session.Segment
import com.hardtekpt.crux.ui.session.SegmentStrip
import com.hardtekpt.crux.ui.session.TimerColors
import com.hardtekpt.crux.ui.session.buzz
import com.hardtekpt.crux.ui.session.clockLabel
import com.hardtekpt.crux.ui.session.describe
import com.hardtekpt.crux.ui.session.rememberTimerSounds
import com.hardtekpt.crux.ui.session.secondsLeft
import com.hardtekpt.crux.ui.session.toSpec
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import kotlinx.coroutines.delay

/** The presets offered above the setup, by name. */
private val PRESETS = listOf(
    "Tabata" to IntervalSpec.TABATA,
    "Repeaters" to IntervalSpec.REPEATERS,
    "Max hangs" to IntervalSpec.MAX_HANGS,
    "4×4s" to IntervalSpec.FOUR_BY_FOURS,
)

/**
 * A standalone interval timer, from the You page. Set it up (or pick a preset), then it takes
 * the whole screen: the phase in its colour, a number to read from across the room, and big
 * buttons for chalky hands. Nothing is logged.
 */
@Composable
fun IntervalTimerScreen(onBack: () -> Unit, viewModel: IntervalTimerViewModel = hiltViewModel()) {
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val soundsOn by viewModel.sounds.collectAsStateWithLifecycle()
    val current = run
    if (current == null) {
        IntervalTimerSetup(spec, onSpec = viewModel::setSpec, onStart = viewModel::start, onBack = onBack)
        return
    }

    // Two clocks, as in a session: `now` moves when the number on screen changes, `drainNow`
    // every frame or so, read only where the fill is drawn.
    var now by remember { mutableLongStateOf(viewModel.now()) }
    var drainNow by remember { mutableLongStateOf(now) }
    LaunchedEffect(current) {
        now = viewModel.now()
        while (true) {
            val tick = viewModel.now()
            if (current.shown(tick) != current.shown(now)) now = tick
            drainNow = tick
            delay(50)
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Beeps for the last three seconds of each phase, a buzz when the phase changes, and both at the end.
    val context = LocalContext.current
    val sounds = rememberTimerSounds(soundsOn)
    val position = current.position(now)
    val left = secondsLeft(position.leftMillis)
    LaunchedEffect(position.index, position.finished) {
        if (position.finished || position.index > 0) {
            sounds.change()
            buzz(context, short = !position.finished && position.phase.kind != IntervalPhase.Kind.WORK)
        }
    }
    LaunchedEffect(left, position.index) {
        if (!current.paused && !position.finished && left in 1..3) sounds.tick()
    }

    // Back pauses a running timer first, so a stray swipe never throws a set away.
    BackHandler { if (!current.paused && !position.finished) viewModel.pause() else viewModel.stop() }

    IntervalTimerRunning(
        run = current,
        nowMillis = now,
        drainNowMillis = { drainNow },
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onStop = viewModel::stop,
        onRestart = viewModel::restart,
        onSkip = viewModel::skip,
    )
}

/** What the running screen shows at [nowMillis], to the second: it moves its clock only when this changes. */
private fun IntervalRun.shown(nowMillis: Long): Any = position(nowMillis).let { Triple(it.index, secondsLeft(it.leftMillis), it.finished) }

/** Setting the timer up: a summary with the shape of the whole timer, presets, and the six settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalTimerSetup(spec: IntervalSpec, onSpec: (IntervalSpec) -> Unit, onStart: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().testTag("screen_IntervalTimer")) {
        CruxTopAppBar(title = "Interval timer", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = space.s4).padding(bottom = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            CruxCard {
                Text(spec.describe(), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                    Text(clockLabel(spec.totalSeconds * 1000L), style = CruxTheme.type.metricMedium)
                    Text("in all", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                PhaseTimeline(spec, Modifier.padding(top = space.s2))
            }
            Eyebrow("Presets", Modifier.padding(top = space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                PRESETS.forEach { (name, preset) ->
                    CruxButton(
                        name,
                        { onSpec(preset) },
                        variant = if (spec == preset) CruxButtonVariant.Tonal else CruxButtonVariant.Outlined,
                        size = CruxButtonSize.Small,
                        modifier = Modifier.testTag("timer_preset_$name"),
                    )
                }
            }
            Eyebrow("Your timer", Modifier.padding(top = space.s2))
            CruxCard { IntervalFields(spec.toSettings()) { onSpec(it.toSpec()) } }
            Text(
                "Nothing is logged. The beeps follow Timer sounds in Settings; the phone buzzes either way.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        CruxButton(
            "Start",
            onStart,
            icon = Icons.Rounded.PlayArrow,
            size = CruxButtonSize.Large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(space.s4).testTag("timer_start"),
        )
    }
}

/** The whole timer as one bar: each phase as wide as it is long, in its colour. */
@Composable
private fun PhaseTimeline(spec: IntervalSpec, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        spec.phases.forEach { phase ->
            Box(Modifier.weight(phase.seconds.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(TimerColors.of(phase.kind)))
        }
    }
}

/**
 * The running timer, full screen. The phase's colour fills the screen from the bottom and drains
 * as the phase runs; tapping the number pauses or resumes. Laid out side by side when the phone
 * is on its side, for a phone propped against the wall.
 */
@Composable
fun IntervalTimerRunning(
    run: IntervalRun,
    nowMillis: Long,
    drainNowMillis: () -> Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onSkip: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val position = run.position(nowMillis)
    val accent = if (position.finished) colors.primary else TimerColors.of(position.phase.kind)
    val fill = accent.copy(alpha = if (CruxTheme.isDark) 0.22f else 0.16f)
    val toggle = if (run.paused) onResume else onPause
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .drawBehind {
                val drain = run.position(drainNowMillis())
                val fraction = if (drain.finished) 1f else drain.leftMillis.toFloat() / (drain.phase.seconds * 1000L).coerceAtLeast(1)
                val height = size.height * fraction.coerceIn(0f, 1f)
                drawRect(fill, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
            }
            .testTag("screen_IntervalTimerRunning"),
    ) {
        val landscape = maxWidth > maxHeight
        val content = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = CruxTheme.space.s4, vertical = CruxTheme.space.s2)
        if (landscape) {
            Row(content, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s6)) {
                Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    PhaseLabel(run, position, accent)
                    BigNumber(run, position, accent, toggle, Modifier.weight(1f))
                    Counts(run, position)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s4)) {
                    TopRow(run, nowMillis, onStop)
                    Spacer(Modifier.weight(1f))
                    UpNext(run, position)
                    Controls(run, position, accent, onPause, onResume, onRestart, onStop, onSkip)
                    Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(content, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
                TopRow(run, nowMillis, onStop)
                Spacer(Modifier.weight(0.3f))
                PhaseLabel(run, position, accent)
                BigNumber(run, position, accent, toggle, Modifier.weight(1f))
                Counts(run, position)
                UpNext(run, position)
                Spacer(Modifier.weight(0.3f))
                Controls(run, position, accent, onPause, onResume, onRestart, onStop, onSkip)
            }
        }
    }
}

/** Close on the left, time left in the whole timer on the right. */
@Composable
private fun TopRow(run: IntervalRun, nowMillis: Long, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onStop, modifier = Modifier.testTag("timer_stop")) {
            Icon(Icons.Rounded.Close, contentDescription = "Stop the timer")
        }
        Spacer(Modifier.weight(1f))
        val totalLeft = (run.spec.totalSeconds * 1000L - run.elapsed(nowMillis)).coerceAtLeast(0)
        Text(
            "${clockLabel(secondsLeft(totalLeft) * 1000L)} left",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(end = CruxTheme.space.s2).testTag("timer_total_left"),
        )
    }
}

private val IntervalPhase.Kind.label: String
    get() = when (this) {
        IntervalPhase.Kind.PREP -> "Get ready"
        IntervalPhase.Kind.WORK -> "Work"
        IntervalPhase.Kind.REST -> "Rest"
        IntervalPhase.Kind.CYCLE_REST -> "Long rest"
    }

/** The phase's name, large, in its colour; "Paused" beside it while paused. */
@Composable
private fun PhaseLabel(run: IntervalRun, position: IntervalPosition, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
        Text(
            if (position.finished) "DONE" else position.phase.kind.label.uppercase(),
            style = PhaseStyle,
            color = if (position.phase.kind == IntervalPhase.Kind.PREP && !position.finished) MaterialTheme.colorScheme.onSurface else accent,
            maxLines = 1,
            modifier = Modifier.testTag("timer_phase"),
        )
        if (run.paused && !position.finished) {
            Text(
                "PAUSED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private val PhaseStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.08.em)

/**
 * The seconds left in the phase, as big as the space allows. The size is set from the longest the
 * label gets in this phase ("0:00" or "00"), so it doesn't jump as the seconds count down.
 */
@Composable
private fun BigNumber(run: IntervalRun, position: IntervalPosition, accent: Color, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val seconds = secondsLeft(position.leftMillis)
    val label = when {
        position.finished -> clockLabel(run.spec.totalSeconds * 1000L)
        seconds >= 60 -> clockLabel(seconds * 1000L)
        else -> seconds.toString()
    }
    val widest = when {
        position.finished -> label
        position.phase.seconds >= 600 -> "00:00"
        position.phase.seconds >= 60 -> "0:00"
        else -> "00"
    }
    val color = when {
        position.finished -> MaterialTheme.colorScheme.onSurface
        run.paused -> accent.copy(alpha = 0.45f)
        position.phase.kind == IntervalPhase.Kind.PREP -> MaterialTheme.colorScheme.onSurface
        else -> accent
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clickable(enabled = !position.finished, onClickLabel = if (run.paused) "Resume" else "Pause", onClick = onToggle)
            .semantics { contentDescription = if (position.finished) "Done" else "$seconds seconds left" },
        contentAlignment = Alignment.Center,
    ) {
        // JetBrains Mono's figures and colon are 0.6 em wide; the figures stand about 0.73 em tall.
        val byWidth = maxWidth / (widest.length * 0.6f)
        val byHeight = maxHeight / 0.95f
        val size: Dp = minOf(byWidth, byHeight) * 0.96f
        val fontSize = with(LocalDensity.current) { size.toSp() }
        Text(
            label,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                lineHeight = fontSize,
                fontFeatureSettings = "tnum",
            ),
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("timer_left"),
        )
    }
}

/** Which repeat and cycle it is, large enough to read at a glance, with the cycle's repeats as a strip. */
@Composable
private fun Counts(run: IntervalRun, position: IntervalPosition) {
    val spec = run.spec
    val phase = position.phase
    // The repeat being worked, or the next one to work.
    val repeat = when {
        position.finished -> spec.repeats
        phase.kind == IntervalPhase.Kind.WORK -> phase.repeat + 1
        phase.kind == IntervalPhase.Kind.REST -> phase.repeat + 2
        else -> 1
    }
    val cycle = when {
        position.finished -> spec.cycles
        phase.kind == IntervalPhase.Kind.CYCLE_REST -> phase.cycle + 2
        else -> phase.cycle + 1
    }
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s6)) {
            Count("Repeat", repeat, spec.repeats, Modifier.testTag("timer_repeat"))
            if (spec.cycles > 1) Count("Cycle", cycle, spec.cycles, Modifier.testTag("timer_cycle"))
        }
        // One piece per repeat of this cycle: done ones full, the one being worked filling.
        val work = TimerColors.work
        val segments = List(spec.repeats) { r ->
            val filled = when {
                position.finished -> 1f

                phase.kind == IntervalPhase.Kind.WORK -> when {
                    r < phase.repeat -> 1f
                    r == phase.repeat -> 1f - position.leftMillis.toFloat() / (phase.seconds * 1000L).coerceAtLeast(1)
                    else -> 0f
                }

                phase.kind == IntervalPhase.Kind.REST -> if (r <= phase.repeat) 1f else 0f

                // Before the first repeat and between cycles, the cycle ahead is still empty.
                else -> 0f
            }
            Segment(filled, work)
        }
        SegmentStrip(segments, height = 8)
    }
}

@Composable
private fun Count(label: String, value: Int, of: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$value", style = CruxTheme.type.metricLarge)
            Text(" / $of", style = CruxTheme.type.metricMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** What comes after this phase. */
@Composable
private fun UpNext(run: IntervalRun, position: IntervalPosition) {
    val next = run.spec.phases.getOrNull(position.index + 1)
    val text = when {
        position.finished -> run.spec.describe()
        next == null -> "Last one"
        else -> "Next · ${next.kind.label} ${formatDuration(next.seconds)}"
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("timer_next"))
}

/**
 * Big buttons: back a phase, pause or resume in the middle, on a phase. Paused, Restart joins
 * them; done, they give way to Again and Close.
 */
@Composable
private fun Controls(
    run: IntervalRun,
    position: IntervalPosition,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onSkip: (Boolean) -> Unit,
) {
    val space = CruxTheme.space
    if (position.finished) {
        Column(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.fillMaxWidth().padding(bottom = space.s2)) {
            CruxButton("Again", onRestart, icon = Icons.Rounded.Replay, size = CruxButtonSize.Large, modifier = Modifier.fillMaxWidth().testTag("timer_again"))
            CruxButton(
                "Close",
                onStop,
                variant = CruxButtonVariant.Outlined,
                size = CruxButtonSize.Large,
                modifier = Modifier.fillMaxWidth().testTag("timer_close"),
            )
        }
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.s3),
        modifier = Modifier.fillMaxWidth().padding(bottom = space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            RoundButton(Icons.Rounded.SkipPrevious, "Back a phase", { onSkip(false) }, 72.dp, null, Modifier.testTag("timer_skip_back"))
            RoundButton(
                if (run.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                if (run.paused) "Resume" else "Pause",
                if (run.paused) onResume else onPause,
                112.dp,
                if (position.phase.kind == IntervalPhase.Kind.PREP) MaterialTheme.colorScheme.onSurface else accent,
                Modifier.testTag(if (run.paused) "timer_resume" else "timer_pause"),
            )
            RoundButton(Icons.Rounded.SkipNext, "Next phase", { onSkip(true) }, 72.dp, null, Modifier.testTag("timer_skip_next"))
        }
        if (run.paused) {
            CruxButton("Restart", onRestart, variant = CruxButtonVariant.Outlined, icon = Icons.Rounded.Replay, modifier = Modifier.testTag("timer_restart"))
        }
    }
}

/** A round button: filled with [fill] when given, otherwise outlined. */
@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp, fill: Color?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (fill !=
                    null
                ) {
                    Modifier.background(fill)
                } else {
                    Modifier.background(colors.surfaceContainerLow).border(CruxTheme.size.borderHairline, colors.outline, CircleShape)
                },
            )
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = if (fill != null) colors.surface else colors.onSurface, modifier = Modifier.size(size * 0.45f))
    }
}
