package com.hardtekpt.crux.data.model

import com.hardtekpt.crux.ui.train.stepLoad
import com.hardtekpt.crux.ui.train.stepSeconds
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
    fun `load steps by half a kilo near zero and 2,5 beyond 5 kg`() {
        assertEquals(0.5, stepLoad(0.0, 1), 0.0)
        assertEquals(5.0, stepLoad(4.5, 1), 0.0)
        assertEquals(7.5, stepLoad(5.0, 1), 0.0)
        assertEquals(5.0, stepLoad(7.5, -1), 0.0)
        assertEquals(4.5, stepLoad(5.0, -1), 0.0)
        assertEquals(-0.5, stepLoad(0.0, -1), 0.0)
    }

    @Test
    fun `seconds step finely for hangs and coarsely for long holds`() {
        assertEquals(11, stepSeconds(10, 1))
        assertEquals(20, stepSeconds(15, 1))
        assertEquals(14, stepSeconds(15, -1))
        assertEquals(150, stepSeconds(120, 1))
        assertEquals(115, stepSeconds(120, -1))
    }
}
