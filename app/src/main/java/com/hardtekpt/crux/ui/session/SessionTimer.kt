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
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.model.IntervalSettings
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono

/** The colours a timer phase is drawn in: work warm, rest cool, preparation neutral. */
internal object TimerColors {
    val work: Color
        @Composable get() = CruxTheme.colors.timerWork
    val rest: Color
        @Composable get() = CruxTheme.colors.timerRest
    val prep: Color
        @Composable get() = MaterialTheme.colorScheme.outline

    /** The longer rest between cycles, apart from the rest between repeats. */
    val cycleRest: Color
        @Composable get() = CruxTheme.colors.timerCycleRest

    /** The rest between sets: the same blue as an interval timer's rest, so a rest always looks like a rest. */
    val setRest: Color
        @Composable get() = CruxTheme.colors.timerRest

    @Composable
    fun of(kind: IntervalPhase.Kind): Color = when (kind) {
        IntervalPhase.Kind.PREP -> prep
        IntervalPhase.Kind.WORK -> work
        IntervalPhase.Kind.REST -> rest
        IntervalPhase.Kind.CYCLE_REST -> cycleRest
    }
}

/** Whole seconds left, rounded up, so the last second reads 1 and not 0. */
internal fun secondsLeft(millis: Long): Int = ((millis.coerceAtLeast(0) + 999) / 1000).toInt()

/** One rail segment: how full, its colour, and what TalkBack calls it ("Hangboard, 2 of 5 sets"). */
internal data class Segment(val filled: Float, val color: Color, val label: String = "")

@Composable
internal fun SegmentStrip(segments: List<Segment>, modifier: Modifier = Modifier, height: Int = 3, onPick: ((Int) -> Unit)? = null) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Row(horizontalArrangement = Arrangement.spacedBy(if (segments.size > 20) 2.dp else 3.dp), modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (onPick != null) {
                            Modifier
                                .clickable(onClickLabel = "Go to it") { onPick(index) }
                                .semantics { contentDescription = segment.label }
                                .padding(vertical = 8.dp)
                        } else {
                            Modifier
                        },
                    )
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(track),
            ) {
                if (segment.filled > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(segment.filled.coerceAtMost(1f)).background(segment.color))
            }
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
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("timer_setup"),
        title = if (title == null) "Start a timer" else "Interval timer",
        confirm = SheetButton("Start", { onStart(spec) }, icon = Icons.Rounded.PlayArrow, tag = "timer_start"),
    ) {
        Text(
            title ?: "Timers of their own. Nothing is logged.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if (title == null) {
            // A plain countdown: one stretch, no preparation, no rests. Tapping one starts it.
            Text("Countdown", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 180 to "3 min", 300 to "5 min").forEach { (seconds, label) ->
                    CruxButton(
                        label,
                        { onStart(IntervalSpec(prepSeconds = 0, workSeconds = seconds, restSeconds = 0, repeats = 1, cycles = 1, cycleRestSeconds = 0)) },
                        variant = CruxButtonVariant.Outlined,
                        size = CruxButtonSize.Small,
                        modifier = Modifier.testTag("timer_countdown_$seconds"),
                    )
                }
            }
            Text("Or an interval timer", style = MaterialTheme.typography.labelLarge, color = colors.onSurface, modifier = Modifier.padding(top = space.s1))
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
    }
}

/**
 * The six settings of an interval timer as rows of steppers, coloured like the phases they set.
 * Used when setting a timer up in a session and when setting an exercise up.
 */
@Composable
fun IntervalFields(settings: IntervalSettings, showPrep: Boolean = true, onChange: (IntervalSettings) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        // A plan's targets leave the preparation to the exercise.
        if (showPrep) {
            SetupRow("Preparation", "before the first repeat", TimerColors.prep) {
                CruxStepper(settings.prepSeconds, { onChange(settings.copy(prepSeconds = it)) }, 0..120, "s", step = 5, testTagPrefix = "timer_prep")
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        }
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
