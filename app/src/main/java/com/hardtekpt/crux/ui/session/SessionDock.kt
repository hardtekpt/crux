package com.hardtekpt.crux.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.ui.theme.JetBrainsMono

private val DockNumber = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp)
private val HugeNumber = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 88.sp, lineHeight = 88.sp)
private val PhaseLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.6.sp)

/**
 * How a timer is drawn: [fill] behind it, [ink] for its text and controls, and [accent] for the
 * outline and the drain. A work phase fills with its colour; everything else is a tint of it.
 */
internal data class DockLook(val fill: Color, val ink: Color, val accent: Color, val muted: Color)

@Composable
internal fun dockLook(accent: Color, solid: Boolean): DockLook {
    val surface = MaterialTheme.colorScheme.surface
    return if (solid) {
        val ink = if (accent.luminance() > 0.35f) Color(0xFF0B0F0E) else Color.White
        DockLook(accent, ink, ink, ink.copy(alpha = 0.75f))
    } else {
        DockLook(accent.copy(alpha = 0.16f).compositeOver(surface), accent, accent, accent.copy(alpha = 0.85f))
    }
}

/** The slim dock a timer runs in: rounded, outlined in its colour; tapping it makes it big. */
@Composable
private fun DockFrame(look: DockLook, onOpen: () -> Unit, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(look.fill)
            .border(1.dp, look.accent.copy(alpha = 0.7f), shape)
            .clickable(onClickLabel = "Show the timer large", role = Role.Button, onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        content = content,
    )
}

/** A small round-cornered pill in a timer's ink: +30 s, Skip. */
@Composable
private fun DockPill(
    text: String,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    fillInk: Color = Color.Unspecified,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (filled) fillInk else ink,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .then(if (filled) Modifier.background(ink) else Modifier.border(1.dp, ink.copy(alpha = 0.7f), CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A round icon control in a timer's ink. */
@Composable
private fun DockIcon(
    icon: ImageVector,
    description: String,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    fillInk: Color = Color.Unspecified,
    big: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(if (big) 56.dp else 40.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(ink) else Modifier.border(1.dp, ink.copy(alpha = 0.7f), CircleShape))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = if (filled) fillInk else ink, modifier = Modifier.size(if (big) 28.dp else 20.dp))
    }
}

/** A thin bar that drains as [fraction] (what's left) runs out; read while drawing only. */
@Composable
private fun DrainBar(fraction: () -> Float, color: Color, modifier: Modifier, height: Int = 3) {
    Box(
        modifier
            .height(height.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(color.copy(alpha = 0.25f), cornerRadius = radius)
                drawRoundRect(color, size = Size(size.width * fraction().coerceIn(0f, 1f), size.height), cornerRadius = radius)
            },
    )
}

/** The repeats of the cycle in progress as dots: done, the current one, still to come. */
@Composable
private fun RepeatDots(run: IntervalRun, position: IntervalPosition, ink: Color, modifier: Modifier = Modifier, big: Boolean = false) {
    val repeats = run.spec.repeats
    if (repeats <= 1) return
    val done = repeats - position.repeatsLeft
    val current = when (position.phase.kind) {
        IntervalPhase.Kind.WORK -> position.phase.repeat
        else -> -1
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (big) 4.dp else 3.dp), modifier = modifier) {
        (0 until repeats).forEach { index ->
            val color = when {
                index == current -> if (ink.luminance() < 0.5f) Color.White else ink
                index < done -> ink
                else -> ink.copy(alpha = 0.25f)
            }
            Box(
                Modifier
                    .then(if (big) Modifier.weight(1f) else Modifier.width(10.dp))
                    .height(if (big) 8.dp else 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** What a phase is called on the dock: "Work · rep 3 of 6 · cycle 2 of 3". */
private fun phaseLine(run: IntervalRun, position: IntervalPosition): String {
    val phase = position.phase
    val spec = run.spec
    val rep = "rep ${phase.repeat + 1} of ${spec.repeats}"
    val cycle = if (spec.cycles > 1) " · cycle ${phase.cycle + 1} of ${spec.cycles}" else ""
    return when (phase.kind) {
        IntervalPhase.Kind.PREP -> "Get ready$cycle"
        IntervalPhase.Kind.WORK -> "Work · $rep$cycle"
        IntervalPhase.Kind.REST -> "Rest · $rep$cycle"
        IntervalPhase.Kind.CYCLE_REST -> "Rest before cycle ${phase.cycle + 2} of ${spec.cycles}"
    }
}

/** The rest between sets in the dock: the time, what's next with a draining bar, +30 s and Skip. */
@Composable
internal fun RestDock(rest: Rest, nowMillis: Long, drainNowMillis: () -> Long, label: String, onAdd: () -> Unit, onSkip: () -> Unit, onOpen: () -> Unit) {
    val look = dockLook(TimerColors.setRest, solid = false)
    DockFrame(look, onOpen, Modifier.testTag("session_rest")) {
        Text(
            clockLabel(secondsLeft(rest.endsAtMillis - nowMillis) * 1000L),
            style = DockNumber,
            color = look.ink,
            modifier = Modifier.testTag("session_rest_left"),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = look.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            DrainBar({ (rest.endsAtMillis - drainNowMillis()) / (rest.totalSeconds * 1000f) }, look.accent, Modifier.fillMaxWidth())
        }
        DockPill("+30 s", look.ink, onAdd)
        DockPill("Skip", look.ink, onSkip, Modifier.testTag("session_rest_skip"))
    }
}

/** The interval timer in the dock: the phase's time, its line with the repeats as dots, pause or play, and skip or stop. */
@Composable
internal fun IntervalDock(
    run: IntervalRun,
    nowMillis: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
) {
    val position = run.position(nowMillis)
    val look = dockLook(TimerColors.of(position.phase.kind), solid = position.phase.kind == IntervalPhase.Kind.WORK && !run.paused)
    DockFrame(look, onOpen, Modifier.testTag("timer_band")) {
        Text(clockLabel(secondsLeft(position.leftMillis) * 1000L), style = DockNumber, color = look.ink, modifier = Modifier.testTag("timer_left"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (run.paused) "Paused · ${phaseLine(run, position)}" else phaseLine(run, position),
                style = MaterialTheme.typography.bodySmall,
                color = look.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("timer_counts"),
            )
            RepeatDots(run, position, look.ink)
        }
        if (run.paused) {
            DockIcon(Icons.Rounded.PlayArrow, "Resume", look.ink, onResume, Modifier.testTag("timer_resume"))
            DockIcon(Icons.Rounded.Close, "Stop the timer", look.ink, onStop, Modifier.testTag("timer_stop"))
        } else {
            DockIcon(Icons.Rounded.Pause, "Pause", look.ink, onPause, Modifier.testTag("timer_pause"))
            DockIcon(Icons.Rounded.SkipNext, "Next phase", look.ink, onSkip, Modifier.testTag("timer_skip_next"))
        }
    }
}

/** The top of the screen given to a timer: one big number, for a phone on the floor across the mat. */
@Composable
private fun BigFrame(
    look: DockLook,
    label: String,
    number: String,
    detail: String,
    onSmall: () -> Unit,
    modifier: Modifier,
    middle: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .background(look.fill)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = PhaseLabel, color = look.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(number, style = HugeNumber, color = look.ink, maxLines = 1, softWrap = false, modifier = Modifier.testTag("timer_big_left"))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = look.muted, textAlign = TextAlign.Center, maxLines = 2)
        Box(Modifier.fillMaxWidth(0.85f)) { middle() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onSmall).padding(horizontal = 12.dp, vertical = 6.dp).testTag("timer_make_small"),
        ) {
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = look.muted, modifier = Modifier.size(18.dp))
            Text("Make small", style = MaterialTheme.typography.labelLarge, color = look.muted)
        }
    }
}

/** The rest, big: REST, the time, what's next, a draining bar, −30 s, +30 s and Skip rest. */
@Composable
internal fun BigRest(
    rest: Rest,
    nowMillis: Long,
    drainNowMillis: () -> Long,
    detail: String,
    onAdd: (Int) -> Unit,
    onSkip: () -> Unit,
    onSmall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = dockLook(TimerColors.setRest, solid = false)
    BigFrame(
        look = look,
        label = "Rest",
        number = clockLabel(secondsLeft(rest.endsAtMillis - nowMillis) * 1000L),
        detail = detail,
        onSmall = onSmall,
        modifier = modifier.testTag("session_rest_big"),
        middle = { DrainBar({ (rest.endsAtMillis - drainNowMillis()) / (rest.totalSeconds * 1000f) }, look.accent, Modifier.fillMaxWidth(), height = 6) },
        actions = {
            DockPill("−30 s", look.ink, { onAdd(-30) })
            DockPill("+30 s", look.ink, { onAdd(30) })
            DockPill("Skip rest", look.ink, onSkip, Modifier.testTag("session_rest_big_skip"), filled = true, fillInk = MaterialTheme.colorScheme.surface)
        },
    )
}

/** The interval timer, big: the phase, its time, what it's for, the repeats, and every control. */
@Composable
internal fun BigInterval(
    run: IntervalRun,
    nowMillis: Long,
    title: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onStop: () -> Unit,
    onSmall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = run.position(nowMillis)
    val look = dockLook(TimerColors.of(position.phase.kind), solid = position.phase.kind == IntervalPhase.Kind.WORK && !run.paused)
    val phase = phaseLine(run, position).substringBefore(" · cycle")
    BigFrame(
        look = look,
        label = if (run.paused) "Paused · $phase" else phase,
        number = clockLabel(secondsLeft(position.leftMillis) * 1000L),
        detail = title + if (run.spec.cycles > 1) " · cycle ${minOf(position.phase.cycle + 1, run.spec.cycles)} of ${run.spec.cycles}" else "",
        onSmall = onSmall,
        modifier = modifier.testTag("timer_big"),
        middle = { RepeatDots(run, position, look.ink, Modifier.fillMaxWidth(), big = true) },
        actions = {
            val fillInk = look.fill
            DockIcon(Icons.Rounded.SkipPrevious, "Back", look.ink, { onSkip(false) })
            if (run.paused) {
                DockIcon(Icons.Rounded.PlayArrow, "Resume", look.ink, onResume, filled = true, fillInk = fillInk, big = true)
            } else {
                DockIcon(Icons.Rounded.Pause, "Pause", look.ink, onPause, filled = true, fillInk = fillInk, big = true)
            }
            DockIcon(Icons.Rounded.SkipNext, "Next phase", look.ink, { onSkip(true) })
            DockIcon(Icons.Rounded.Close, "Stop the timer", look.ink, onStop)
        },
    )
}

/** Keeps the height of the big view's frame stable across phases. */
internal val BigTimerHeight = 340.dp
