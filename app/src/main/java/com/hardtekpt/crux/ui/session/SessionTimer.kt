package com.hardtekpt.crux.ui.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.model.IntervalSettings
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono

/** The colours a timer phase is drawn in: work warm, rest cool, preparation neutral. */
internal object TimerColors {
    val work: Color
        @Composable get() = if (dark()) Color(0xFFFF8A65) else Color(0xFFC2410C)
    val rest: Color
        @Composable get() = if (dark()) Color(0xFF8FB3FF) else Color(0xFF2F5BB7)
    val prep: Color
        @Composable get() = MaterialTheme.colorScheme.outline

    /** The longer rest between cycles, apart from the rest between repeats. */
    val cycleRest: Color
        @Composable get() = if (dark()) Color(0xFFC9A7FF) else Color(0xFF6D45C4)

    /** The rest between sets, which isn't part of an interval timer. */
    val setRest: Color
        @Composable get() = MaterialTheme.colorScheme.secondary

    @Composable
    private fun dark() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    @Composable
    fun of(kind: IntervalPhase.Kind): Color = when (kind) {
        IntervalPhase.Kind.PREP -> prep
        IntervalPhase.Kind.WORK -> work
        IntervalPhase.Kind.REST -> rest
        IntervalPhase.Kind.CYCLE_REST -> cycleRest
    }
}

private val BigNumber = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 52.sp)
private val CountNumber = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp)

/** Whole seconds left, rounded up, so the last second reads 1 and not 0. */
internal fun secondsLeft(millis: Long): Int = ((millis.coerceAtLeast(0) + 999) / 1000).toInt()

/**
 * The band under the title while something counts down: [accent] fills it and drains from the
 * right as [fraction] (left of the whole) runs out; [segments] run along the bottom edge.
 * [fraction] is read only while drawing, so the drain moves without recomposing the screen.
 */
@Composable
internal fun TimerBand(accent: Color, fraction: () -> Float, segments: List<Segment>, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().height(96.dp).background(colors.surfaceContainerLow).drawBehind {
            val filled = size.width * fraction().coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRect(accent.copy(alpha = 0.2f), size = Size(filled, size.height))
                val edge = 3.dp.toPx().coerceAtMost(filled)
                drawRect(accent, topLeft = Offset(filled - edge, 0f), size = Size(edge, size.height))
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            content = content,
        )
        if (segments.isNotEmpty()) SegmentStrip(segments, Modifier.align(Alignment.BottomStart))
    }
}

/** One piece of a progress strip: how much of it is filled, and with what. */
internal data class Segment(val filled: Float, val color: Color)

@Composable
internal fun SegmentStrip(segments: List<Segment>, modifier: Modifier = Modifier, height: Int = 3, onPick: ((Int) -> Unit)? = null) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Row(horizontalArrangement = Arrangement.spacedBy(if (segments.size > 20) 2.dp else 3.dp), modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            Box(
                Modifier
                    .weight(1f)
                    .then(if (onPick != null) Modifier.clickable { onPick(index) }.padding(vertical = 8.dp) else Modifier)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(track),
            ) {
                if (segment.filled > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(segment.filled.coerceAtMost(1f)).background(segment.color))
            }
        }
    }
}

/** The big number in a band. */
@Composable
internal fun BandNumber(millisLeft: Long, color: Color, modifier: Modifier = Modifier) {
    Text(clockLabel(secondsLeft(millisLeft) * 1000L), style = BigNumber, color = color, maxLines = 1, softWrap = false, modifier = modifier)
}

/** A count beside the number: `3` over "repeats left". */
@Composable
internal fun BandCount(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value.toString(), style = CountNumber)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

/** A round outlined icon button for the band. */
@Composable
internal fun BandIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(36.dp).border(1.dp, ink.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = ink, modifier = Modifier.size(20.dp))
        }
    }
}

/** A small outlined pill for the rest band: +30 s, Skip. */
@Composable
internal fun BandPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, ink.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/**
 * The rest between sets, counting down: the number, what's next, +30 s and Skip. The number
 * follows [nowMillis]; the drain follows [drainNowMillis], which moves smoothly.
 */
@Composable
internal fun RestBand(rest: Rest, nowMillis: Long, drainNowMillis: () -> Long, next: String, segments: List<Segment>, onAdd: () -> Unit, onSkip: () -> Unit) {
    val accent = TimerColors.setRest
    val left = rest.endsAtMillis - nowMillis
    TimerBand(accent, { (rest.endsAtMillis - drainNowMillis()) / (rest.totalSeconds * 1000f) }, segments, Modifier.testTag("session_rest")) {
        BandNumber(left, accent, Modifier.testTag("session_rest_left"))
        Column(Modifier.weight(1f)) {
            Text("Rest", style = MaterialTheme.typography.titleSmall)
            Text(next, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BandPill("+30 s", onAdd)
            BandPill("Skip", onSkip, Modifier.testTag("session_rest_skip"))
        }
    }
}

/**
 * The interval timer: the phase's number in its colour, repeats and cycles left, and pause.
 * Paused, it offers play and stop. Like [RestBand], the drain follows [drainNowMillis].
 */
@Composable
internal fun IntervalBand(run: IntervalRun, nowMillis: Long, drainNowMillis: () -> Long, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    val position = run.position(nowMillis)
    val accent = TimerColors.of(position.phase.kind)
    // Along the bottom, one piece per cycle and per rest between cycles, filled as they pass.
    val work = TimerColors.work
    val cycleRest = TimerColors.cycleRest
    val elapsed = run.elapsed(nowMillis)
    var start = 0L
    val segments = buildList {
        run.spec.phases.groupBy {
            if (it.kind ==
                IntervalPhase.Kind.CYCLE_REST
            ) {
                -1 - it.cycle * 2
            } else if (it.kind == IntervalPhase.Kind.PREP) {
                Int.MIN_VALUE
            } else {
                it.cycle * 2
            }
        }
            .forEach { (key, group) ->
                val length = group.sumOf { it.seconds * 1000L }
                if (key != Int.MIN_VALUE) {
                    val filled = ((elapsed - start).toFloat() / length).coerceIn(0f, 1f)
                    add(Segment(filled, if (key < 0) cycleRest else work))
                }
                start += length
            }
    }
    // The fill drains over the whole cycle, in the colour of the phase it's in.
    TimerBand(accent, { run.stretch(drainNowMillis()).fractionLeft }, segments, Modifier.testTag("timer_band")) {
        BandNumber(
            position.leftMillis,
            if (run.paused) {
                accent.copy(alpha = 0.5f)
            } else if (position.phase.kind == IntervalPhase.Kind.PREP) {
                MaterialTheme.colorScheme.onSurface
            } else {
                accent
            },
            Modifier.testTag("timer_left"),
        )
        Spacer(Modifier.weight(1f))
        BandCount(position.repeatsLeft, "repeats left", Modifier.testTag("timer_repeats_left"))
        BandCount(position.cyclesLeft, "cycles left", Modifier.testTag("timer_cycles_left"))
        if (run.paused) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                BandIconButton(Icons.Rounded.PlayArrow, "Resume", onResume, Modifier.testTag("timer_resume"))
                BandIconButton(Icons.Rounded.Close, "Stop the timer", onStop, Modifier.testTag("timer_stop"))
            }
        } else {
            BandIconButton(Icons.Rounded.Pause, "Pause", onPause, Modifier.testTag("timer_pause"))
        }
    }
}

/** What a timer phase is called, for the sheet's total and for screen readers. */
internal fun IntervalSpec.describe(): String = buildString {
    append("$repeats × ${formatDuration(workSeconds)}")
    if (restSeconds > 0) append(" on / ${formatDuration(restSeconds)} off")
    if (cycles > 1) append(", $cycles cycles")
}

/**
 * Sets the interval timer up before it starts: preparation, work, rest, repeats, cycles and the
 * rest between cycles. [title] names the exercise it runs for, or is null for a free timer,
 * which also offers presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerSetupSheet(initial: IntervalSpec, title: String?, onStart: (IntervalSpec) -> Unit, onDismiss: () -> Unit) {
    var prep by rememberSaveable { mutableStateOf(initial.prepSeconds) }
    var work by rememberSaveable { mutableStateOf(initial.workSeconds) }
    var rest by rememberSaveable { mutableStateOf(initial.restSeconds) }
    var repeats by rememberSaveable { mutableStateOf(initial.repeats) }
    var cycles by rememberSaveable { mutableStateOf(initial.cycles) }
    var cycleRest by rememberSaveable { mutableStateOf(initial.cycleRestSeconds) }
    val spec = IntervalSpec(prep, work, rest, repeats, cycles, cycleRest)
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("timer_setup"),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = space.s4).padding(bottom = space.s4).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            Text("Interval timer", style = MaterialTheme.typography.headlineSmall)
            Text(
                title ?: "A timer of its own. Nothing is logged.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (title == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                    listOf("Tabata" to IntervalSpec.TABATA, "Repeaters" to IntervalSpec.REPEATERS).forEach { (name, preset) ->
                        CruxButton(
                            name,
                            {
                                prep = preset.prepSeconds
                                work = preset.workSeconds
                                rest = preset.restSeconds
                                repeats = preset.repeats
                                cycles = preset.cycles
                                cycleRest = preset.cycleRestSeconds
                            },
                            variant = if (spec == preset) CruxButtonVariant.Tonal else CruxButtonVariant.Outlined,
                            size = CruxButtonSize.Small,
                            modifier = Modifier.testTag("timer_preset_$name"),
                        )
                    }
                }
            }
            IntervalFields(spec.toSettings()) {
                prep = it.prepSeconds
                work = it.workSeconds
                rest = it.restSeconds
                repeats = it.repeats
                cycles = it.cycles
                cycleRest = it.cycleRestSeconds
            }
            Text(
                "Takes ${formatDuration(spec.totalSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = space.s1),
            )
            CruxButton(
                "Start",
                { onStart(spec) },
                icon = Icons.Rounded.PlayArrow,
                size = CruxButtonSize.Large,
                modifier = Modifier.fillMaxWidth().testTag("timer_start"),
            )
        }
    }
}

/**
 * The six settings of an interval timer as rows of steppers, coloured like the phases they set.
 * Used when setting a timer up in a session and when setting an exercise up.
 */
@Composable
fun IntervalFields(settings: IntervalSettings, onChange: (IntervalSettings) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        SetupRow("Preparation", "before the first repeat", TimerColors.prep) {
            CruxStepper(settings.prepSeconds, { onChange(settings.copy(prepSeconds = it)) }, 0..120, "s", step = 5, testTagPrefix = "timer_prep")
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        SetupRow("Work", "each repeat", TimerColors.work) {
            CruxStepper(settings.workSeconds, { onChange(settings.copy(workSeconds = it)) }, 1..600, "s", testTagPrefix = "timer_work")
        }
        SetupRow("Rest", "between repeats", TimerColors.rest) {
            CruxStepper(settings.restSeconds, { onChange(settings.copy(restSeconds = it)) }, 0..600, "s", testTagPrefix = "timer_rest")
        }
        SetupRow("Repeats", "in a cycle", null) {
            CruxStepper(settings.repeats, { onChange(settings.copy(repeats = it)) }, 1..50, "", testTagPrefix = "timer_repeats")
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        SetupRow("Cycles", null, null) {
            CruxStepper(settings.cycles, { onChange(settings.copy(cycles = it)) }, 1..20, "", testTagPrefix = "timer_cycles")
        }
        SetupRow("Rest between cycles", null, TimerColors.cycleRest) {
            CruxStepper(
                settings.cycleRestSeconds,
                { onChange(settings.copy(cycleRestSeconds = it)) },
                0..900,
                "s",
                step = 15,
                enabled = settings.cycles > 1,
                testTagPrefix = "timer_cycle_rest",
            )
        }
    }
}

@Composable
private fun SetupRow(label: String, hint: String?, accent: Color?, stepper: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent ?: Color.Transparent))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        stepper()
    }
}

/** Short beeps for a countdown and a longer one when a phase changes; silent when turned off. */
internal class TimerSounds(enabled: Boolean) {
    private val tones = if (enabled) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull() else null

    fun tick() {
        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    fun change() {
        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350) }
    }

    fun release() {
        runCatching { tones?.release() }
    }
}

@Composable
internal fun rememberTimerSounds(enabled: Boolean): TimerSounds {
    val sounds = remember(enabled) { TimerSounds(enabled) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}

/** A double buzz; short when [short]. */
internal fun buzz(context: Context, short: Boolean = false) {
    runCatching {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val pattern = if (short) longArrayOf(0, 120) else longArrayOf(0, 180, 120, 180)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
