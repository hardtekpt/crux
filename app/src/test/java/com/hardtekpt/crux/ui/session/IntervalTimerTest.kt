package com.hardtekpt.crux.ui.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalTimerTest {
    private val spec = IntervalSpec(prepSeconds = 10, workSeconds = 20, restSeconds = 10, repeats = 3, cycles = 2, cycleRestSeconds = 60)

    @Test
    fun `phases run prep, then work and rest, with a cycle rest between cycles and none at the end`() {
        val kinds = spec.phases.map { it.kind }
        assertEquals(IntervalPhase.Kind.PREP, kinds.first())
        assertEquals(1 + 2 * (3 + 2) + 1, kinds.size)
        assertEquals(IntervalPhase.Kind.CYCLE_REST, kinds[6])
        assertEquals(IntervalPhase.Kind.WORK, kinds.last())
        assertEquals(10 + 2 * (3 * 20 + 2 * 10) + 60, spec.totalSeconds)
    }

    @Test
    fun `position counts repeats and cycles left, including the one in progress`() {
        val run = IntervalRun(spec, startedAtMillis = 0)
        val prep = run.position(5_000)
        assertEquals(IntervalPhase.Kind.PREP, prep.phase.kind)
        assertEquals(3, prep.repeatsLeft)
        assertEquals(2, prep.cyclesLeft)

        // 10 s prep + 20 s work + 10 s rest + 5 s into the second repeat.
        val second = run.position(45_000)
        assertEquals(IntervalPhase.Kind.WORK, second.phase.kind)
        assertEquals(2, second.repeatsLeft)
        assertEquals(15_000, second.leftMillis)

        // 10 s prep + a whole cycle of 80 s, then 10 s into the rest between cycles.
        val between = run.position(100_000)
        assertEquals(IntervalPhase.Kind.CYCLE_REST, between.phase.kind)
        assertEquals(1, between.cyclesLeft)
        assertEquals(3, between.repeatsLeft)
        assertEquals(1, run.cyclesDone(100_000))

        assertTrue(run.position(1_000_000).finished)
        assertEquals(2, run.cyclesDone(1_000_000))
    }

    @Test
    fun `time paused doesn't count`() {
        val run = IntervalRun(spec, startedAtMillis = 0).pause(5_000).resume(65_000)
        assertEquals(5_000, run.elapsed(65_000))
        assertEquals(IntervalPhase.Kind.PREP, run.position(65_000).phase.kind)
    }

    @Test
    fun `the stretch is the whole cycle while working or resting between repeats`() {
        val run = IntervalRun(spec, startedAtMillis = 0)
        // A cycle is 3 × 20 s of work and 2 × 10 s of rest: 80 s. 45 s in is 35 s into it.
        val cycle = run.stretch(45_000)
        assertEquals(80_000, cycle.totalMillis)
        assertEquals(45_000, cycle.leftMillis)
        // Preparation and the rest between cycles are stretches of their own.
        assertEquals(IntervalStretch(5_000, 10_000), run.stretch(5_000))
        assertEquals(IntervalStretch(50_000, 60_000), run.stretch(100_000))
    }
}
