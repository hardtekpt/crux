package com.hardtekpt.crux.live

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
import com.hardtekpt.crux.ui.session.IntervalRun
import com.hardtekpt.crux.ui.session.IntervalSpec
import com.hardtekpt.crux.ui.session.Rest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCardsTest {
    private val now = 30 * 60_000L

    private fun exercise(id: Long, name: String) = Exercise(id, name, ExerciseCategory.PULLING, MetricType.REPS, notes = null)

    private fun doneSets(count: Int) = List(count) { SessionSet(setIndex = it, reps = 8, seconds = null, loadKg = null, skipped = false) }

    private fun item(id: Long, name: String, sets: Int, logged: Int) =
        SessionItem(id, exercise(id, name), "Main", ExerciseTarget(sets = sets, reps = 8, restSeconds = 60), doneSets(logged))

    private fun session(items: List<SessionItem> = emptyList(), climbs: List<Climb> = emptyList()) = Session(
        id = 1,
        name = "Evening",
        templateId = if (items.isEmpty()) null else 5,
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

    private fun climb(id: Long, style: AscentStyle) = Climb(
        id = id,
        discipline = Discipline.BOULDER,
        gradeScale = GradeScale.FONT,
        gradeIndex = 3,
        style = style,
        attempts = 1,
        venue = Venue.GYM,
        date = LocalDate.of(2026, 10, 13),
        name = null,
        place = null,
        notes = null,
    )

    /** The first exercise finished and the second halfway through its four sets. */
    private fun planSession() = session(items = listOf(item(1, "Pull-ups", 3, 3), item(2, "Rows", 4, 2)))

    @Test
    fun `a plan session shows its exercises as segments and the next set`() {
        val card = LiveCards.forSession(planSession(), rest = null, timer = null, now = now)

        assertEquals(listOf(LiveColor.DONE, LiveColor.WORK), card.segments.map { it.color })
        assertEquals("Rows · set 3 of 4", card.title)
        assertEquals("Set 3/4", card.chip)
        assertEquals(listOf(LiveAction.SET_DONE), card.actions)
        assertTrue(card.progress > 0)
        assertTrue(card.progress < card.segments.sumOf { it.length })
        assertEquals(false, card.countDown)
    }

    @Test
    fun `a rest turns the card into the countdown`() {
        val rest = Rest(endsAtMillis = now + 90_000, totalSeconds = 120)
        val card = LiveCards.forSession(planSession(), rest = rest, timer = null, now = now)

        assertEquals("1:30 · Rest", card.title)
        assertEquals("1:30", card.chip)
        assertTrue(card.countDown)
        assertNull(card.clockAt)
        assertEquals(listOf(LiveAction.ADD_REST, LiveAction.SUB_REST, LiveAction.SKIP_REST), card.actions)
        assertEquals(LiveColor.REST, card.accent)
    }

    @Test
    fun `a rest that has ended no longer takes the card over`() {
        val rest = Rest(endsAtMillis = now - 1, totalSeconds = 120)
        val card = LiveCards.forSession(planSession(), rest = rest, timer = null, now = now)

        assertEquals(listOf(LiveAction.SET_DONE), card.actions)
    }

    @Test
    fun `a climbing day counts climbs and marks each one`() {
        val climbs = listOf(climb(1, AscentStyle.REDPOINT), climb(2, AscentStyle.ATTEMPT))
        val card = LiveCards.forSession(session(climbs = climbs), rest = null, timer = null, now = now)

        assertTrue(card.title.startsWith("2 climbs · 1 sent"))
        assertEquals(listOf(LiveColor.DONE, LiveColor.LATER), card.points.map { it.color })
        assertEquals(listOf(LiveAction.LOG_CLIMB), card.actions)
    }

    @Test
    fun `the interval card shows work and rest with pause and stop`() {
        val spec = IntervalSpec.TABATA
        // One second into the first work phase, after the preparation.
        val at = spec.prepSeconds * 1000L + 1_000
        val card = LiveCards.forInterval(IntervalRun(spec, startedAtMillis = 0), at, name = null, sessionId = null)

        assertEquals("0:19 · Work", card.title)
        assertTrue(card.text!!.startsWith("Rep 1 of"))
        assertTrue(card.segments.any { it.color == LiveColor.WORK })
        assertTrue(card.segments.any { it.color == LiveColor.REST })
        assertEquals(listOf(LiveAction.PAUSE, LiveAction.STOP), card.actions)
        assertEquals("0:19", card.chip)
    }

    @Test
    fun `a paused interval card offers resume and says so`() {
        val spec = IntervalSpec.TABATA
        val at = spec.prepSeconds * 1000L + 1_000
        val card = LiveCards.forInterval(IntervalRun(spec, startedAtMillis = 0, pausedAtMillis = at), at, name = null, sessionId = null)

        assertEquals(listOf(LiveAction.RESUME, LiveAction.STOP), card.actions)
        assertEquals("Paused", card.chip)
    }

    @Test
    fun `a long interval fits Android's limits and keeps its colours`() {
        // 8 × 20 s on, 10 s off: 16 phases with the preparation, more than Android draws.
        val spec = IntervalSpec(prepSeconds = 10, workSeconds = 20, restSeconds = 10, repeats = 8, cycles = 1, cycleRestSeconds = 0)
        val at = (10 + 20 + 10 + 20 + 5) * 1000L
        val card = LiveCards.fit(LiveCards.forInterval(IntervalRun(spec, startedAtMillis = 0), at, name = null, sessionId = null))

        assertTrue(card.segments.size in 2..LiveCards.MAX_SEGMENTS)
        assertEquals(LiveColor.DONE, card.segments.first().color)
        assertTrue(card.segments.any { it.color == LiveColor.WORK })
        assertTrue(card.segments.any { it.color == LiveColor.REST })
        assertEquals(spec.totalSeconds, card.segments.sumOf { it.length })
    }

    @Test
    fun `only the latest four climbs get points`() {
        val card =
            LiveCard(
                "t", null, "s", null, null, false, listOf(LiveSegment(100, LiveColor.REST)),
                (1..6).map {
                    LivePoint(it * 10, LiveColor.DONE)
                },
                70, LiveTracker.NONE, emptyList(), null,
            )
        assertEquals(listOf(30, 40, 50, 60), LiveCards.fit(card).points.map { it.position })
    }
}
