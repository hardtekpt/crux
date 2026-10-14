package com.hardtekpt.crux.ui.progress

import com.hardtekpt.crux.data.ExerciseRecord
import com.hardtekpt.crux.data.ScheduleSlot
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionItem
import com.hardtekpt.crux.data.SessionSet
import com.hardtekpt.crux.data.local.ScheduleKind
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.MetricType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingProgressTest {
    private val zone = ZoneId.of("UTC")

    /** A Wednesday; its week starts on Monday 12 October. */
    private val today = LocalDate.of(2026, 10, 14)
    private val monday = LocalDate.of(2026, 10, 12)

    private val hangs = Exercise(1, "Max hangs", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, null)
    private val plank = Exercise(2, "Plank", ExerciseCategory.CORE, MetricType.REPS, null)

    private fun set(index: Int, skipped: Boolean = false) = SessionSet(index, reps = 5, seconds = 10, loadKg = 10.0, skipped = skipped)

    private fun session(id: Long, date: LocalDate, minutes: Long = 45, sets: List<SessionSet> = listOf(set(0)), running: Boolean = false): Session {
        val start = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        return Session(
            id = id,
            name = "Session $id",
            templateId = 1,
            placeId = null,
            sectionId = null,
            startedAtMillis = start,
            endedAtMillis = if (running) null else start + minutes * 60_000,
            running = running,
            effort = null,
            notes = null,
            items = listOf(SessionItem(id, hangs, "Fingers", ExerciseTarget(sets = 3), sets)),
            climbs = emptyList(),
        )
    }

    private fun slot(id: Long, kind: ScheduleKind) = ScheduleSlot(id, DayOfWeek.MONDAY, kind)

    private fun result(id: Long, exercise: Exercise, date: LocalDate, loadKg: Double? = null, reps: Int? = null, seconds: Int? = 10) =
        ExerciseRecord(id, exercise.id, date, reps, seconds, loadKg, notes = null)

    private fun progress(
        sessions: List<Session> = emptyList(),
        records: List<ExerciseRecord> = emptyList(),
        slots: List<ScheduleSlot> = emptyList(),
        climbDays: Set<LocalDate> = emptySet(),
    ) = trainingProgress(sessions, records, listOf(hangs, plank), slots, climbDays, today, zone)

    @Test
    fun `eight weeks, oldest first, ending with this Monday`() {
        val weeks = progress().weeks

        assertEquals(8, weeks.size)
        assertEquals(monday, weeks.last().start)
        assertEquals(monday.minusWeeks(7), weeks.first().start)
        assertEquals(weeks.map { it.start }.sorted(), weeks.map { it.start })
    }

    @Test
    fun `done counts a session for each plan and a day for each climbing day, up to what was planned`() {
        val slots = listOf(slot(1, ScheduleKind.PLAN), slot(2, ScheduleKind.PLAN), slot(3, ScheduleKind.CLIMB))
        val busy = listOf(session(1, monday), session(2, monday.plusDays(1)), session(3, today))
        val quiet = session(4, monday.minusDays(6))

        val weeks = progress(sessions = busy + quiet, slots = slots, climbDays = setOf(monday, monday.plusDays(1))).weeks

        assertEquals(3, weeks.last().planned)
        assertEquals(3, weeks.last().done)
        assertEquals(3, weeks.last().sessions)
        assertEquals(3, weeks[6].planned)
        assertEquals(1, weeks[6].done)
    }

    @Test
    fun `no schedule means nothing planned`() {
        val result = progress(sessions = listOf(session(1, monday)))

        assertFalse(result.scheduled)
        assertTrue(result.weeks.all { it.planned == 0 })
        assertEquals(1, result.weeks.last().sessions)
    }

    @Test
    fun `a running session is ignored`() {
        val result = progress(sessions = listOf(session(1, today, running = true)))

        assertEquals(0, result.weeks.last().sessions)
        assertEquals(0, result.sessionsLast30)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `sets are the ones done, not skipped, and minutes come from the session's times`() {
        val first = session(1, monday, minutes = 45, sets = listOf(set(0), set(1), set(2, skipped = true)))
        val second = session(2, today, minutes = 30, sets = listOf(set(0), set(1)))

        val week = progress(sessions = listOf(first, second)).weeks.last()

        assertEquals(2, week.sessions)
        assertEquals(4, week.sets)
        assertEquals(75, week.minutes)
    }

    @Test
    fun `a result that beats the best so far is a new best, the first and an equal one are not`() {
        val records = listOf(
            result(1, hangs, monday.minusDays(6), loadKg = 10.0),
            result(2, hangs, monday.minusDays(4), loadKg = 12.5),
            result(3, hangs, monday.minusDays(2), loadKg = 12.5),
            result(4, hangs, monday, loadKg = 15.0),
        )

        val result = progress(records = records)

        assertEquals(1, result.weeks[6].newBests)
        assertEquals(1, result.weeks.last().newBests)
        assertEquals(2, result.newBestsLast30)
    }

    @Test
    fun `exercises come newest first, with a row per day and the star on the day of the best`() {
        val records = listOf(
            result(1, hangs, monday.minusDays(4), loadKg = 12.5),
            result(2, hangs, monday, loadKg = 14.0),
            result(3, hangs, monday, loadKg = 15.0),
            result(4, plank, today, reps = 30, seconds = null),
        )

        val exercises = progress(records = records).exercises

        assertEquals(listOf("Plank", "Max hangs"), exercises.map { it.exercise.name })
        val days = exercises.last().days
        assertEquals(listOf(monday, monday.minusDays(4)), days.map { it.date })
        assertEquals(2, days.first().results)
        assertEquals(15.0, days.first().best.loadKg!!, 0.0)
        assertEquals(1, days.last().results)
        assertTrue(days.first().isBest)
        assertFalse(days.last().isBest)
        assertEquals(15.0, exercises.last().best.loadKg!!, 0.0)
    }

    @Test
    fun `the headline figure is load for weighted work, seconds for time and reps for reps`() {
        val record = result(1, hangs, today, loadKg = 12.5, reps = 6, seconds = 20)

        assertEquals(12.5, MetricType.WEIGHTED_TIME.headline(record), 0.0)
        assertEquals(20.0, MetricType.TIME.headline(record), 0.0)
        assertEquals(6.0, MetricType.REPS.headline(record), 0.0)
    }

    @Test
    fun `minutes read as minutes, then hours`() {
        assertEquals("45 min", minutesLabel(45))
        assertEquals("1 h", minutesLabel(60))
        assertEquals("1 h 20 min", minutesLabel(80))
    }
}
