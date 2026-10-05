package com.hardtekpt.crux.ui

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.ui.journal.groupByDayAndPlace
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.ui.progress.ProgressUiState
import com.hardtekpt.crux.ui.progress.toDisciplineBests
import com.hardtekpt.crux.ui.you.parseHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
    fun `height must be between 100 and 250 cm`() {
        assertEquals(178.5, parseHeight("178,5").getOrThrow(), 0.0)
        assertTrue(parseHeight("90").isFailure)
        assertTrue(parseHeight("tall").isFailure)
    }

    private fun climb(id: Long, date: LocalDate, place: String) = Climb(
        id, Discipline.BOULDER, GradeScale.FONT, 5, AscentStyle.FLASH, 1, Venue.GYM, date, null, place, null,
    )

    private fun best(
        discipline: Discipline,
        style: AscentStyle,
        index: Int,
        scale: GradeScale = discipline.defaultScale,
    ) = PersonalBest(discipline, style, scale, index, null, null, today)
}
