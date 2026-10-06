package com.hardtekpt.crux.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingModelTest {

    @Test
    fun `prescriptions follow the metric`() {
        assertEquals("3 × 12", ExerciseTarget(sets = 3, reps = 12).prescription(MetricType.REPS))
        assertEquals("5 × 5 · +10 kg", ExerciseTarget(sets = 5, reps = 5, loadKg = 10.0).prescription(MetricType.WEIGHTED_REPS))
        assertEquals("6 × 10 s · +2.5 kg", ExerciseTarget(sets = 6, seconds = 10, loadKg = 2.5).prescription(MetricType.WEIGHTED_TIME))
        assertEquals("4 × 7 s · −10 kg", ExerciseTarget(sets = 4, seconds = 7, loadKg = -10.0).prescription(MetricType.WEIGHTED_TIME))
        // Load is ignored where the metric does not use it.
        assertEquals("3 × 30 s", ExerciseTarget(sets = 3, seconds = 30, loadKg = 5.0).prescription(MetricType.TIME))
        assertEquals("1 × 10 min", ExerciseTarget(sets = 1, seconds = 600).prescription(MetricType.TIME))
    }

    @Test
    fun `interval prescriptions show work, rest, repeats and cycles`() {
        val repeaters = ExerciseTarget.defaultFor(MetricType.WEIGHTED_INTERVALS).copy(loadKg = 5.0)
        assertEquals("3 × 6 × 7 s on / 3 s off · +5 kg", repeaters.prescription(MetricType.WEIGHTED_INTERVALS))
        assertEquals("3 min", repeaters.restLabel())
        val tabata = ExerciseTarget.defaultFor(MetricType.INTERVALS)
        assertEquals("1 × 8 × 20 s on / 10 s off", tabata.prescription(MetricType.INTERVALS))
    }

    @Test
    fun `interval length counts every repeat, rest and cycle rest`() {
        // 3 cycles × (6 × 7 s + 5 × 3 s) + 2 × 180 s = 3 × 57 + 360 = 531 s.
        val repeaters = ExerciseTarget(sets = 3, reps = 6, seconds = 7, repRestSeconds = 3, restSeconds = 180)
        assertEquals(531, repeaters.estimatedSeconds(MetricType.WEIGHTED_INTERVALS))
    }

    @Test
    fun `rest reads like a person would say it`() {
        assertEquals("3 min", ExerciseTarget(restSeconds = 180).restLabel())
        assertEquals("1 min 30 s", ExerciseTarget(restSeconds = 90).restLabel())
        assertEquals("45 s", ExerciseTarget(restSeconds = 45).restLabel())
        assertNull(ExerciseTarget(restSeconds = 0).restLabel())
    }

    @Test
    fun `plan length counts work, rest between sets and changeovers`() {
        val hang = Exercise(1, "Hang", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, null)
        val plan = WorkoutTemplate(
            id = 1,
            name = "Hangs",
            description = "",
            // 6 × 10 s work + 5 × 180 s rest + 60 s changeover = 1020 s = 17 min → 20 min.
            blocks = listOf(PlanBlock("Main", listOf(PlanItem(hang, ExerciseTarget(sets = 6, seconds = 10, restSeconds = 180))))),
        )
        assertEquals(20, plan.estimatedMinutes)
        assertEquals(1, plan.exerciseCount)
    }

    @Test
    fun `added load shows in pounds under imperial, to the nearest half pound`() {
        assertEquals("5 × 5 · +22 lb", ExerciseTarget(sets = 5, reps = 5, loadKg = 10.0).prescription(MetricType.WEIGHTED_REPS, imperial = true))
        assertEquals("−5.5 lb", signedLoad(-2.5, imperial = true))
        assertEquals("+2.5 kg", signedLoad(2.5, imperial = false))
        // Pounds typed in come back as the same pounds.
        assertEquals(22.5, loadValue(poundsToKg(22.5), imperial = true), 0.0)
    }
}
