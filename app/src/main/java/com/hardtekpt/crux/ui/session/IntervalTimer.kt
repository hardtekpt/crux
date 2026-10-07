package com.hardtekpt.crux.ui.session

/** What an interval timer runs: a preparation, then cycles of work and rest repeats. */
data class IntervalSpec(
    val prepSeconds: Int = 10,
    val workSeconds: Int = 20,
    val restSeconds: Int = 10,
    val repeats: Int = 8,
    val cycles: Int = 1,
    /** Rest between cycles, replacing the last repeat's rest. */
    val cycleRestSeconds: Int = 60,
) {
    /** The phases in order. The last repeat of the last cycle has no rest after it. */
    val phases: List<IntervalPhase> by lazy {
        buildList {
            if (prepSeconds > 0) add(IntervalPhase(IntervalPhase.Kind.PREP, prepSeconds, cycle = 0, repeat = 0))
            for (cycle in 0 until cycles) {
                for (repeat in 0 until repeats) {
                    add(IntervalPhase(IntervalPhase.Kind.WORK, workSeconds, cycle, repeat))
                    val last = repeat == repeats - 1
                    when {
                        !last && restSeconds > 0 -> add(IntervalPhase(IntervalPhase.Kind.REST, restSeconds, cycle, repeat))
                        last && cycle < cycles - 1 && cycleRestSeconds > 0 -> add(IntervalPhase(IntervalPhase.Kind.CYCLE_REST, cycleRestSeconds, cycle, repeat))
                    }
                }
            }
        }
    }

    val totalSeconds: Int get() = phases.sumOf { it.seconds }

    fun toSettings() = com.hardtekpt.crux.data.model.IntervalSettings(prepSeconds, workSeconds, restSeconds, repeats, cycles, cycleRestSeconds)

    companion object {
        /** Classic Tabata: 8 × 20 s on, 10 s off. */
        val TABATA = IntervalSpec(prepSeconds = 10, workSeconds = 20, restSeconds = 10, repeats = 8, cycles = 1, cycleRestSeconds = 60)

        /** Hangboard repeaters: 6 × 7 s on, 3 s off, three cycles with 3 min between. */
        val REPEATERS = IntervalSpec(prepSeconds = 10, workSeconds = 7, restSeconds = 3, repeats = 6, cycles = 3, cycleRestSeconds = 180)
    }
}

fun com.hardtekpt.crux.data.model.IntervalSettings.toSpec() = IntervalSpec(prepSeconds, workSeconds, restSeconds, repeats, cycles, cycleRestSeconds)

data class IntervalStretch(val leftMillis: Long, val totalMillis: Long) {
    val fractionLeft: Float get() = if (totalMillis <= 0) 0f else leftMillis.toFloat() / totalMillis
}

data class IntervalPhase(val kind: Kind, val seconds: Int, val cycle: Int, val repeat: Int) {
    enum class Kind { PREP, WORK, REST, CYCLE_REST }
}

/** Where a running timer is: the phase, how long is left in it, and what's left overall. */
data class IntervalPosition(
    val index: Int,
    val phase: IntervalPhase,
    /** Milliseconds left in this phase. */
    val leftMillis: Long,
    /** Repeats left in this cycle, counting the one in progress (or about to start). */
    val repeatsLeft: Int,
    /** Cycles left, counting the one in progress (between cycles, the ones still to start). */
    val cyclesLeft: Int,
    val finished: Boolean,
)

/**
 * A running timer, worked out from the clock rather than ticked, so it stays right when the
 * screen is off or the app is in the background. [pausedAtMillis] is set while paused.
 */
data class IntervalRun(
    val spec: IntervalSpec,
    val startedAtMillis: Long,
    val pausedAtMillis: Long? = null,
    /** Time spent paused before the current pause. */
    val pausedMillis: Long = 0,
) {
    val paused: Boolean get() = pausedAtMillis != null

    fun elapsed(nowMillis: Long): Long = (pausedAtMillis ?: nowMillis) - startedAtMillis - pausedMillis

    fun position(nowMillis: Long): IntervalPosition {
        val phases = spec.phases
        var left = elapsed(nowMillis).coerceAtLeast(0)
        phases.forEachIndexed { index, phase ->
            val length = phase.seconds * 1000L
            if (left < length) {
                return IntervalPosition(
                    index = index,
                    phase = phase,
                    leftMillis = length - left,
                    repeatsLeft = repeatsLeft(phase),
                    cyclesLeft = spec.cycles - phase.cycle - if (phase.kind == IntervalPhase.Kind.CYCLE_REST) 1 else 0,
                    finished = false,
                )
            }
            left -= length
        }
        val last = phases.lastOrNull() ?: IntervalPhase(IntervalPhase.Kind.WORK, 0, 0, 0)
        return IntervalPosition(phases.lastIndex, last, 0, 0, 0, finished = true)
    }

    private fun repeatsLeft(phase: IntervalPhase): Int = when (phase.kind) {
        IntervalPhase.Kind.PREP -> spec.repeats
        IntervalPhase.Kind.WORK -> spec.repeats - phase.repeat
        IntervalPhase.Kind.REST -> spec.repeats - phase.repeat - 1
        IntervalPhase.Kind.CYCLE_REST -> spec.repeats
    }

    /** Cycles fully done by now. */
    fun cyclesDone(nowMillis: Long): Int {
        val position = position(nowMillis)
        if (position.finished) return spec.cycles
        return when (position.phase.kind) {
            IntervalPhase.Kind.CYCLE_REST -> position.phase.cycle + 1
            else -> position.phase.cycle
        }
    }

    /**
     * How far through its stretch the timer is: the preparation, a whole cycle (its repeats and
     * the rests between them) or the rest between cycles.
     */
    fun stretch(nowMillis: Long): IntervalStretch {
        val position = position(nowMillis)
        val phase = position.phase
        if (phase.kind == IntervalPhase.Kind.PREP || phase.kind == IntervalPhase.Kind.CYCLE_REST || position.finished) {
            return IntervalStretch(position.leftMillis, phase.seconds * 1000L)
        }
        val inCycle = spec.phases.withIndex().filter { (_, p) ->
            p.cycle == phase.cycle &&
                (p.kind == IntervalPhase.Kind.WORK || p.kind == IntervalPhase.Kind.REST)
        }
        val total = inCycle.sumOf { it.value.seconds * 1000L }
        val before = inCycle.filter { it.index < position.index }.sumOf { it.value.seconds * 1000L }
        val done = before + phase.seconds * 1000L - position.leftMillis
        return IntervalStretch(total - done, total)
    }

    fun pause(nowMillis: Long): IntervalRun = if (paused) this else copy(pausedAtMillis = nowMillis)

    fun resume(nowMillis: Long): IntervalRun = pausedAtMillis?.let { copy(pausedAtMillis = null, pausedMillis = pausedMillis + (nowMillis - it)) } ?: this
}
