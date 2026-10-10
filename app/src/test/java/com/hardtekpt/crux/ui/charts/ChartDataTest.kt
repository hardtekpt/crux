package com.hardtekpt.crux.ui.charts

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.ui.progress.progressCharts
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDataTest {

    @Test
    fun `y axis hugs the data on round ticks`() {
        val scale = niceScale(72.2, 73.4)
        assertEquals(72.0, scale.min, 1e-9)
        assertEquals(73.5, scale.max, 1e-9)
        assertEquals(listOf(72.0, 72.5, 73.0, 73.5), scale.ticks)
    }

    @Test
    fun `a flat series still gets a usable axis`() {
        val scale = niceScale(70.0, 70.0)
        assertTrue(scale.min < 70.0 && scale.max > 70.0)
    }

    @Test
    fun `slices past the palette fold into Other`() {
        val slices = (1..7).map { SliceDatum("S$it", it.toDouble()) }
        val folded = foldSlices(slices)

        assertEquals(CATEGORICAL_SLOTS, folded.size)
        assertEquals("Other", folded.last().label)
        assertEquals(1.0 + 2 + 3, folded.last().value, 0.0)
    }

    @Test
    fun `progress charts count sends per week and build pyramids in the chosen scale`() {
        val today = LocalDate.of(2026, 10, 5) // a Monday
        val climbs = listOf(
            climb(1, today, "6C", GradeScale.FONT),
            climb(2, today.minusDays(1), "6C", GradeScale.FONT),
            climb(3, today.minusDays(1), "7A", GradeScale.FONT),
            climb(4, today, "V5", GradeScale.V_SCALE),
            climb(5, today, "7A+", GradeScale.FONT, AscentStyle.ATTEMPT),
        )

        val charts = progressCharts(climbs, GradeScales(), today, weeks = 2)

        assertEquals(listOf(2.0, 2.0), charts.weeklySends.map { it.value })
        val pyramid = charts.pyramids.single()
        assertEquals(listOf("7A", "6C"), pyramid.rows.map { it.label })
        assertEquals(listOf(1.0, 2.0), pyramid.rows.map { it.value })
        assertEquals(4.0, charts.sendsByStyle.sumOf { it.value }, 0.0)
    }

    private fun climb(id: Long, date: LocalDate, grade: String, scale: GradeScale, style: AscentStyle = AscentStyle.FLASH) =
        Climb(id, Discipline.BOULDER, scale, scale.grades.indexOf(grade), style, 1, Venue.GYM, date, null, null, null)
}
