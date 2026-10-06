package com.hardtekpt.crux.ui.you

import com.hardtekpt.crux.data.ExerciseRecord
import com.hardtekpt.crux.data.best
import com.hardtekpt.crux.data.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The numbers behind the profile: weekly streaks and which result is a PR. */
class ProfileTest {

    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun `streak counts weeks in a row up to this one, or last week if this one is empty`() {
        val days = setOf(monday.minusWeeks(3), monday.minusWeeks(2).plusDays(2), monday.minusWeeks(1), monday.minusWeeks(6))
        // Nothing yet this week: the run up to last week still counts.
        assertEquals(3 to 3, weekStreaks(days, monday.plusDays(1)))
        assertEquals(4 to 4, weekStreaks(days + monday, monday.plusDays(1)))
        assertEquals(0 to 0, weekStreaks(emptySet(), monday))
    }

    private fun result(id: Long, reps: Int? = null, seconds: Int? = null, load: Double? = null) =
        ExerciseRecord(id, 1, monday.minusDays(id), reps, seconds, load, null)

    @Test
    fun `a PR is the heaviest load, then most reps, or the longest hold`() {
        assertEquals(2L, MetricType.WEIGHTED_REPS.best(listOf(result(1, reps = 8, load = 10.0), result(2, reps = 3, load = 20.0)))?.id)
        assertEquals(1L, MetricType.WEIGHTED_REPS.best(listOf(result(1, reps = 6, load = 20.0), result(2, reps = 3, load = 20.0)))?.id)
        assertEquals(2L, MetricType.TIME.best(listOf(result(1, seconds = 30), result(2, seconds = 45)))?.id)
        assertEquals(1L, MetricType.REPS.best(listOf(result(1, reps = 12), result(2, reps = 9)))?.id)
    }
}
