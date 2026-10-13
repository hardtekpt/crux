package com.hardtekpt.crux.live

import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.ui.session.IntervalPhase
import com.hardtekpt.crux.ui.session.IntervalRun
import com.hardtekpt.crux.ui.session.Rest
import com.hardtekpt.crux.ui.session.SessionTimer
import com.hardtekpt.crux.ui.session.clockLabel

/** The colours of the live bar, the session timer band's: work, rest, the rest between cycles, done. */
enum class LiveColor { WORK, REST, CYCLE_REST, DONE, LATER }

/** What the tracker on the bar shows. */
enum class LiveTracker { CLIMBER, REST, NONE }

/** The buttons a live card can have. Tapping the card itself opens the session or timer. */
enum class LiveAction(val label: String) {
    SET_DONE("Set done"),
    ADD_REST("+30 s"),
    SUB_REST("−30 s"),
    SKIP_REST("Skip rest"),
    LOG_CLIMB("Log climb"),
    PAUSE("Pause"),
    RESUME("Resume"),
    STOP("Stop"),
}

data class LiveSegment(val length: Int, val color: LiveColor)

/** A mark on the bar at [position] (in the same units as the segments). */
data class LivePoint(val position: Int, val color: LiveColor)

/**
 * One live notification, worked out from the session and timers and nothing else, so it can be
 * tested off the device. The bar is [segments] with [points], filled up to [progress].
 */
data class LiveCard(
    val title: String,
    val text: String?,
    /** The small line by the app name: the session or timer. */
    val sub: String,
    /** The status bar chip; null shows the clock instead. */
    val chip: String?,
    /** The clock's base: counting up from it, or down to it when [countDown]. */
    val clockAt: Long?,
    val countDown: Boolean,
    val segments: List<LiveSegment>,
    val points: List<LivePoint> = emptyList(),
    val progress: Int,
    val tracker: LiveTracker,
    val actions: List<LiveAction>,
    /** Opens the session ([sessionId]) or else the Interval timer page. */
    val sessionId: Long?,
    /** The accent of the card: rest is amber, everything else the work colour. */
    val accent: LiveColor = LiveColor.WORK,
)

object LiveCards {
    /** Android 16 draws at most this many segments; more and the bar becomes one plain segment. */
    const val MAX_SEGMENTS = 10

    /** And at most this many points. */
    const val MAX_POINTS = 4

    /**
     * The card within Android's limits, keeping detail where it matters, around and after now:
     * what's behind the progress becomes one done segment, neighbours of a colour join, and the
     * far end is merged until the bar fits. Only the latest points are kept.
     */
    fun fit(card: LiveCard): LiveCard {
        var segments = card.segments
        // Everything already behind the progress, as one.
        var start = 0
        var behind = 0
        var firstAhead = segments.size
        for ((index, segment) in segments.withIndex()) {
            if (start + segment.length <= card.progress) {
                behind++
            } else {
                firstAhead = index
                break
            }
            start += segment.length
        }
        if (behind > 1) {
            segments = listOf(LiveSegment(segments.take(behind).sumOf { it.length }, LiveColor.DONE)) + segments.drop(firstAhead.coerceAtLeast(behind))
        }
        // Neighbours of one colour as one.
        segments = segments.fold(mutableListOf<LiveSegment>()) { acc, segment ->
            val last = acc.lastOrNull()
            if (last != null && last.color == segment.color) acc[acc.lastIndex] = last.copy(length = last.length + segment.length) else acc += segment
            acc
        }
        // The far end merged until it fits.
        while (segments.size > MAX_SEGMENTS) {
            val tail = segments.takeLast(2)
            segments = segments.dropLast(2) + LiveSegment(tail.sumOf { it.length }, tail.first().color)
        }
        return card.copy(segments = segments, points = card.points.takeLast(MAX_POINTS))
    }

    /** The length the bar of a climbing day spans when there's no history yet. */
    const val USUAL_CLIMBING_MINUTES = 120

    /**
     * The card for a running [session]: a rest or the interval timer takes it over while they
     * run; otherwise a plan session shows its exercises and a climbing day its climbs.
     */
    fun forSession(session: Session, rest: Rest?, timer: SessionTimer?, now: Long, usualMinutes: Int = USUAL_CLIMBING_MINUTES): LiveCard {
        timer?.let { return forInterval(it.run, now, session.name, session.id) }
        val minutes = durationMinutes(session, now)
        if (rest != null && rest.endsAtMillis > now) return forRest(session, rest, now, minutes)
        return if (session.items.isNotEmpty()) forPlan(session, minutes) else forClimbing(session, now, usualMinutes, minutes)
    }

    private fun durationMinutes(session: Session, now: Long) = ((now - session.startedAtMillis) / 60_000).coerceAtLeast(0)

    /** The line by the app name; the clock beside it already says how long. */
    @Suppress("UNUSED_PARAMETER")
    private fun header(session: Session, minutes: Long) = session.name

    /** One segment per exercise, as long as it should take: done ones green, the rest in the work colour. */
    private fun planSegments(session: Session): Pair<List<LiveSegment>, Int> {
        var progress = 0
        val segments = session.items.map { item ->
            val length = item.target.estimatedSeconds(item.exercise.metric).coerceAtLeast(60)
            val share = if (item.target.sets == 0) 1f else item.logged.toFloat() / item.target.sets
            progress += (length * share.coerceIn(0f, 1f)).toInt()
            LiveSegment(length, if (item.finished) LiveColor.DONE else LiveColor.WORK)
        }
        return segments to progress
    }

    private fun forPlan(session: Session, minutes: Long): LiveCard {
        val (segments, progress) = planSegments(session)
        val current = session.items.firstOrNull { !it.finished }
        val next = current?.let { c -> session.items.drop(session.items.indexOf(c) + 1).firstOrNull { !it.finished } }
        val left = session.items.count { !it.finished }
        val title = current?.let { "${it.exercise.name} · set ${(it.nextSet ?: 0) + 1} of ${it.target.sets}" } ?: "Every exercise done"
        val text = when {
            current == null -> "Finish the session in Crux"
            next != null -> "Then ${next.exercise.name} · ${left - 1} more to go"
            else -> "Last exercise"
        }
        return LiveCard(
            title = title,
            text = text,
            sub = header(session, minutes),
            chip = current?.let { "Set ${(it.nextSet ?: 0) + 1}/${it.target.sets}" } ?: "Done",
            clockAt = session.startedAtMillis,
            countDown = false,
            segments = segments,
            progress = progress,
            tracker = LiveTracker.CLIMBER,
            actions = if (current != null) listOf(LiveAction.SET_DONE) else emptyList(),
            sessionId = session.id,
        )
    }

    private fun forRest(session: Session, rest: Rest, now: Long, minutes: Long): LiveCard {
        val current = session.items.firstOrNull { !it.finished }
        val (segments, progress) = if (session.items.isNotEmpty()) planSegments(session) else listOf(LiveSegment(rest.totalSeconds, LiveColor.REST)) to 0
        val restProgress = (rest.totalSeconds - ((rest.endsAtMillis - now) / 1000).toInt()).coerceIn(0, rest.totalSeconds)
        return LiveCard(
            title = current?.let { "Rest before set ${(it.nextSet ?: 0) + 1}" } ?: "Rest",
            text = current?.let { "${it.exercise.name} · ${it.logged} of ${it.target.sets} sets done" } ?: "Then pull on again",
            sub = header(session, minutes),
            chip = null,
            clockAt = rest.endsAtMillis,
            countDown = true,
            segments = segments,
            progress = if (session.items.isNotEmpty()) progress else restProgress,
            tracker = LiveTracker.REST,
            actions = listOf(LiveAction.ADD_REST, LiveAction.SUB_REST, LiveAction.SKIP_REST),
            sessionId = session.id,
            accent = LiveColor.REST,
        )
    }

    private fun forClimbing(session: Session, now: Long, usualMinutes: Int, minutes: Long): LiveCard {
        val climbs = session.climbGroups
        val sent = climbs.count { it.style.isSend }
        val span = maxOf(usualMinutes.toLong(), minutes + 10).toInt()
        val elapsed = minutes.toInt().coerceAtMost(span)
        val hardest = climbs.filter { it.style.isSend }.maxByOrNull { it.gradeIndex }?.grade
        val last = session.climbs.lastOrNull()
        return LiveCard(
            title = if (climbs.isEmpty()) {
                "Climbing"
            } else {
                "${climbs.size} ${if (climbs.size == 1) "climb" else "climbs"} · $sent sent" +
                    (hardest?.let { " · $it" } ?: "")
            },
            text = last?.let { "Last: ${it.name ?: it.grade}, ${if (it.style.isSend) "sent" else "not yet"}" } ?: "Climbs you log land here",
            sub = header(session, minutes),
            chip = if (climbs.isEmpty()) null else "${climbs.size} · $sent sent",
            clockAt = session.startedAtMillis,
            countDown = false,
            segments = listOf(LiveSegment(span, LiveColor.REST)),
            // One mark per climb, in the order logged, across the time so far.
            points = session.climbs.mapIndexed { index, climb ->
                LivePoint(((elapsed * (index + 1)) / (session.climbs.size + 1)).coerceAtLeast(1), if (climb.style.isSend) LiveColor.DONE else LiveColor.LATER)
            },
            progress = elapsed,
            tracker = LiveTracker.CLIMBER,
            actions = listOf(LiveAction.LOG_CLIMB),
            sessionId = session.id,
        )
    }

    /**
     * The interval timer: finished cycles as one green segment each, the current cycle rep by
     * rep (work and rest), the cycles to come as one segment each, and the rests between cycles.
     */
    fun forInterval(run: IntervalRun, now: Long, name: String?, sessionId: Long?): LiveCard {
        val spec = run.spec
        val position = run.position(now)
        val phases = spec.phases
        val current = position.phase
        // Every phase in order; [fit] then joins what's done, and cycles to come, to fit the bar.
        val segments = phases.map { phase ->
            val color = when {
                phase.kind == IntervalPhase.Kind.PREP -> LiveColor.LATER
                phase.kind == IntervalPhase.Kind.CYCLE_REST -> LiveColor.CYCLE_REST
                phase.cycle < current.cycle -> LiveColor.DONE
                phase.cycle > current.cycle -> LiveColor.LATER
                phase.kind == IntervalPhase.Kind.WORK -> LiveColor.WORK
                else -> LiveColor.REST
            }
            LiveSegment(phase.seconds.coerceAtLeast(1), color)
        }
        // Progress is time done, in seconds, on the same scale as the segments.
        val doneBefore = phases.take(position.index).sumOf { it.seconds }
        val progress = (doneBefore + current.seconds - (position.leftMillis / 1000).toInt()).coerceIn(0, segments.sumOf { it.length })
        // Phases number cycles and repeats from 0; the card counts from 1.
        val title = when {
            position.finished -> "Done"
            current.kind == IntervalPhase.Kind.WORK -> "Work · rep ${current.repeat + 1} of ${spec.repeats}"
            current.kind == IntervalPhase.Kind.REST -> "Rest · rep ${current.repeat + 2} next"
            current.kind == IntervalPhase.Kind.CYCLE_REST -> "Rest before cycle ${current.cycle + 2}"
            else -> "Get ready"
        }
        val left = (position.leftMillis / 1000).toInt()
        return LiveCard(
            title = title,
            text =
                "Cycle ${current.cycle + 1} of ${spec.cycles}" +
                    if (spec.cycles > 1 && spec.cycleRestSeconds > 0) " · ${clockLabel(spec.cycleRestSeconds * 1000L)} between cycles" else "",
            sub = listOfNotNull(name, "${spec.repeats} × ${spec.workSeconds} s on, ${spec.restSeconds} s off").joinToString(" · "),
            chip = if (run.paused) "Paused" else null,
            clockAt = if (run.paused) null else now + left * 1000L,
            countDown = true,
            segments = segments,
            progress = progress,
            tracker = if (current.kind == IntervalPhase.Kind.WORK) LiveTracker.CLIMBER else LiveTracker.REST,
            actions = listOf(if (run.paused) LiveAction.RESUME else LiveAction.PAUSE, LiveAction.STOP),
            sessionId = sessionId,
            accent = if (current.kind == IntervalPhase.Kind.WORK) LiveColor.WORK else LiveColor.REST,
        )
    }
}
