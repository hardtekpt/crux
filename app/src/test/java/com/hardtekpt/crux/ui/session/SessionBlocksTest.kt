package com.hardtekpt.crux.ui.session

import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionItem
import com.hardtekpt.crux.data.SessionSet
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.Venue
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A session is drawn as blocks, with climbing where it happened. */
class SessionBlocksTest {
    private fun item(id: Long, vararg doneAt: Long) = SessionItem(
        id = id,
        exercise = Exercise(id, "Exercise $id", ExerciseCategory.entries.first(), MetricType.REPS, null),
        blockName = "",
        target = ExerciseTarget(sets = 2),
        sets = doneAt.mapIndexed { index, at -> SessionSet(index, 8, null, null, skipped = false, completedAtMillis = at) },
    )

    private fun climb(id: Long, at: Long) = Climb(
        id = id,
        discipline = Discipline.BOULDER,
        gradeScale = GradeScale.FONT,
        gradeIndex = 10,
        style = AscentStyle.FLASH,
        attempts = 1,
        venue = Venue.GYM,
        date = LocalDate.of(2026, 10, 16),
        name = null,
        place = null,
        notes = null,
        createdAtMillis = at,
    )

    private fun session(items: List<SessionItem>, climbs: List<Climb> = emptyList()) = Session(
        id = 1,
        name = "Power endurance",
        templateId = 1,
        placeId = null,
        sectionId = null,
        startedAtMillis = 0,
        endedAtMillis = null,
        running = true,
        effort = null,
        notes = null,
        items = items,
        climbs = climbs,
    )

    private fun List<SessionBlock>.keys() = map { it.key }

    @Test
    fun `a training session is its exercises`() {
        val s = session(listOf(item(1), item(2)))
        assertEquals(listOf(1L, 2L), s.blocks(hasPlaylist = false).keys())
        assertFalse(s.climbingNow(hasPlaylist = false))
    }

    @Test
    fun `climbing goes where it started, before the first exercise not begun by then`() {
        val s = session(listOf(item(1, 10, 20), item(2, 50), item(3)), listOf(climb(1, 30), climb(2, 40)))
        assertEquals(listOf(1L, CLIMBING_KEY, 2L, 3L), s.blocks(hasPlaylist = false).keys())
    }

    @Test
    fun `climbing is current while it is the newest thing logged`() {
        assertTrue(session(listOf(item(1, 10)), listOf(climb(1, 30))).climbingNow(false))
        assertFalse(session(listOf(item(1, 10, 40)), listOf(climb(1, 30))).climbingNow(false))
        assertTrue(session(listOf(item(1, 10, 40)), listOf(climb(1, 30))).climbingNow(hasPlaylist = true))
    }

    @Test
    fun `a playlist before any climb sits before the first exercise not started`() {
        val s = session(listOf(item(1, 10, 20), item(2)))
        assertEquals(listOf(1L, CLIMBING_KEY, 2L), s.blocks(hasPlaylist = true).keys())
    }

    @Test
    fun `an unplanned session starts blank, and climbing alone is one block`() {
        assertTrue(session(emptyList()).isBlank(hasPlaylist = false))
        val climbing = session(emptyList(), listOf(climb(1, 5)))
        assertEquals(listOf(CLIMBING_KEY), climbing.blocks(false).keys())
        assertTrue(climbing.climbingNow(false))
    }
}
