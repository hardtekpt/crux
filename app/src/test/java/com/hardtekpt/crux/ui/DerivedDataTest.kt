package com.hardtekpt.crux.ui

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.ui.journal.count
import com.hardtekpt.crux.ui.journal.groupByDayAndPlace
import com.hardtekpt.crux.ui.journal.matching
import com.hardtekpt.crux.ui.progress.ProgressUiState
import com.hardtekpt.crux.ui.progress.toDisciplineBests
import com.hardtekpt.crux.ui.you.apeIndex
import com.hardtekpt.crux.ui.you.parseMeasurement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DerivedDataTest {

    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun `weight change compares with the entry a full 30 days back`() {
        val summary = listOf(
            Measurement(1, 72.4, today),
            Measurement(2, 72.9, today.minusDays(20)),
            Measurement(3, 73.4, today.minusDays(33)),
        ).weightSummary()!!

        assertEquals(72.4, summary.latest.value, 0.0)
        assertEquals(-1.0, summary.change!!, 1e-9)
        assertEquals("30 days", summary.window)
    }

    @Test
    fun `weight change falls back to the oldest entry inside the window`() {
        val summary = listOf(Measurement(1, 72.4, today), Measurement(2, 72.0, today.minusDays(10)))
            .weightSummary()!!

        assertEquals(0.4, summary.change!!, 1e-9)
        assertTrue(summary.window, summary.window.startsWith("since 25 Sep"))
    }

    @Test
    fun `a single weigh-in has no change`() {
        val summary = listOf(Measurement(1, 72.4, today)).weightSummary()!!
        assertNull(summary.change)
        assertNull(emptyList<Measurement>().weightSummary())
    }

    @Test
    fun `journal groups by day and place in arrival order`() {
        val days = listOf(
            climb(1, today, "Arco"),
            climb(2, today, "Arco"),
            climb(3, today, "Block Lab"),
            climb(4, today.minusDays(1), "Arco"),
        ).groupByDayAndPlace()

        assertEquals(listOf(2, 1, 1), days.map { it.climbs.size })
        assertEquals("Mon 5 Oct · Arco · Gym", days.first().title)
    }

    @Test
    fun `timeline puts every kind of entry on its day, newest day first`() {
        val note = com.hardtekpt.crux.data.Note(1, "Finger tweak", today.minusDays(1), pinned = false, tag = "injury")
        val days = com.hardtekpt.crux.ui.journal.buildTimeline(
            climbs = listOf(climb(1, today, "Arco"), climb(2, today.minusDays(1), "Arco")),
            results = emptyList(),
            notes = listOf(note),
        )

        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(
            listOf(com.hardtekpt.crux.ui.journal.JournalFilter.Climbs, com.hardtekpt.crux.ui.journal.JournalFilter.Notes),
            days[1].entries.map { it.kind },
        )
        assertEquals(3, days.count())
    }

    @Test
    fun `journal filters combine and search matches names and note text`() {
        val note = com.hardtekpt.crux.data.Note(1, "Finger tweak on crimps", today.minusDays(1), pinned = false, tag = "injury")
        val days = com.hardtekpt.crux.ui.journal.buildTimeline(
            climbs = listOf(climb(1, today, "Arco"), climb(2, today.minusDays(1), "Block Lab"), climb(3, today.minusDays(40), "Arco")),
            results = emptyList(),
            notes = listOf(note),
        )
        fun shown(query: com.hardtekpt.crux.ui.journal.JournalQuery) = days.matching(query, today).count()
        val q = com.hardtekpt.crux.ui.journal.JournalQuery()

        assertEquals(4, shown(q))
        // A place keeps only climbs there; with a date range on top, only recent ones.
        assertEquals(2, shown(q.copy(places = setOf("Arco"))))
        assertEquals(1, shown(q.copy(places = setOf("Arco"), period = com.hardtekpt.crux.ui.journal.JournalPeriod.Month)))
        // A tag keeps only notes with it.
        assertEquals(1, shown(q.copy(tags = setOf("injury"))))
        // Search looks at note text and place names, every word must match.
        assertEquals(1, shown(q.copy(search = "crimps")))
        assertEquals(1, shown(q.copy(search = "block lab")))
        assertEquals(0, shown(q.copy(search = "block crimps")))
    }

    @Test
    fun `climbs from a finished session sit inside it, not on their own`() {
        val inSession = climb(1, today, "Arco").copy(sessionId = 9)
        val loose = climb(2, today, "Arco")
        val session = com.hardtekpt.crux.data.Session(
            id = 9, name = "Climbing session", templateId = null, placeId = null, sectionId = null,
            startedAtMillis = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), endedAtMillis = null,
            running = false, effort = null, notes = null, items = emptyList(), climbs = listOf(inSession),
        )
        val day = com.hardtekpt.crux.ui.journal.buildTimeline(
            listOf(inSession, loose),
            emptyList(),
            emptyList(),
            listOf(session),
            java.time.ZoneOffset.UTC,
        ).single()
        val loneClimbs = day.entries.filterIsInstance<com.hardtekpt.crux.ui.journal.TimelineEntry.Climbs>().flatMap { it.day.climbs }
        assertEquals(listOf(2L), loneClimbs.map { it.id })
        assertEquals(1, day.entries.count { it is com.hardtekpt.crux.ui.journal.TimelineEntry.SessionEntry })
    }

    @Test
    fun `hardest send per discipline ignores style`() {
        val bests = listOf(
            best(Discipline.BOULDER, AscentStyle.FLASH, 9),
            best(Discipline.BOULDER, AscentStyle.REDPOINT, 12),
        ).toDisciplineBests()

        assertEquals(12, bests.single().hardest?.gradeIndex)
        assertNull(ProgressUiState(groups = bests).headline(Discipline.ROUTE))
    }

    @Test
    fun `bests in different scales are kept apart and the chosen scale leads`() {
        val groups = listOf(
            best(Discipline.BOULDER, AscentStyle.FLASH, 12, GradeScale.FONT),
            best(Discipline.BOULDER, AscentStyle.FLASH, 3, GradeScale.V_SCALE),
        ).toDisciplineBests()

        assertEquals(listOf(GradeScale.FONT, GradeScale.V_SCALE), groups.map { it.scale })
        val vFirst = ProgressUiState(scales = GradeScales(boulder = GradeScale.V_SCALE), groups = groups)
        assertEquals("V2", vFirst.headline(Discipline.BOULDER)?.grade)
        assertEquals("7A+", ProgressUiState(groups = groups).headline(Discipline.BOULDER)?.grade)
    }

    @Test
    fun `each discipline offers its own scales`() {
        assertEquals(listOf(GradeScale.FONT, GradeScale.V_SCALE), Discipline.BOULDER.scales)
        assertEquals(listOf(GradeScale.FRENCH, GradeScale.YDS), Discipline.ROUTE.scales)
        assertEquals("V3", GradeScale.V_SCALE.label(GradeScale.V_SCALE.defaultIndex))
        assertEquals("5.10a", GradeScale.YDS.label(GradeScale.YDS.defaultIndex))
    }

    @Test
    fun `body stats are checked against their range`() {
        assertEquals(178.5, parseMeasurement(MeasurementType.HEIGHT, "178,5").getOrThrow(), 0.0)
        assertTrue(parseMeasurement(MeasurementType.HEIGHT, "90").isFailure)
        assertTrue(parseMeasurement(MeasurementType.WINGSPAN, "tall").isFailure)
        assertEquals(
            "Enter a body fat between 3 and 60%",
            parseMeasurement(MeasurementType.BODY_FAT, "80").exceptionOrNull()?.message,
        )
    }

    @Test
    fun `ape index is wingspan minus height, with the ratio`() {
        val ape = apeIndex(wingspanCm = 184.0, heightCm = 178.0)!!
        assertEquals(6.0, ape.differenceCm, 1e-9)
        assertEquals(1.034, ape.ratio, 1e-3)
        assertNull(apeIndex(wingspanCm = 184.0, heightCm = null))
    }

    private fun climb(id: Long, date: LocalDate, place: String) = Climb(
        id, Discipline.BOULDER, GradeScale.FONT, 5, AscentStyle.FLASH, 1, Venue.GYM, date, null, place, null,
    )

    private fun best(discipline: Discipline, style: AscentStyle, index: Int, scale: GradeScale = discipline.defaultScale) =
        PersonalBest(discipline, style, scale, index, null, null, today)
}
