package com.hardtekpt.crux.data.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** A session shows goes on the same problem as one entry. */
class ClimbGroupsTest {
    private fun climb(id: Long, style: AscentStyle, attempts: Int, problemId: Long?) = Climb(
        id = id,
        discipline = Discipline.BOULDER,
        gradeScale = GradeScale.FONT,
        gradeIndex = 10,
        style = style,
        attempts = attempts,
        venue = Venue.GYM,
        date = LocalDate.of(2026, 10, 9),
        name = null,
        place = null,
        notes = null,
        problemId = problemId,
    )

    @Test
    fun `goes on one problem add up, and a send among them makes it a redpoint`() {
        val groups = listOf(
            climb(1, AscentStyle.ATTEMPT, 2, problemId = 7),
            climb(2, AscentStyle.FLASH, 1, problemId = null),
            climb(3, AscentStyle.ATTEMPT, 1, problemId = 7),
            climb(4, AscentStyle.REDPOINT, 2, problemId = 7),
            climb(5, AscentStyle.ATTEMPT, 1, problemId = 8),
            climb(6, AscentStyle.ATTEMPT, 3, problemId = 8),
        ).groupedByProblem()

        assertEquals(3, groups.size)
        val dyno = groups[0]
        assertEquals(AscentStyle.REDPOINT, dyno.style)
        assertEquals(5, dyno.attempts)
        assertEquals(4L, dyno.id)
        assertEquals(AscentStyle.FLASH, groups[1].style)
        assertEquals(AscentStyle.ATTEMPT, groups[2].style)
        assertEquals(4, groups[2].attempts)
    }
}
