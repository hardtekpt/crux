package com.hardtekpt.crux.ui

import com.hardtekpt.crux.ui.charts.BarDatum
import com.hardtekpt.crux.ui.charts.SeriesPoint
import com.hardtekpt.crux.ui.charts.describeBars
import com.hardtekpt.crux.ui.charts.describeSeries
import com.hardtekpt.crux.ui.session.spokenDuration
import com.hardtekpt.crux.ui.you.consistencyDescription
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** What TalkBack reads for pictures: charts, the consistency grid and timer times. */
class AccessibilityTextTest {
    @Test
    fun `a line chart reads its range, low and high`() {
        val points = listOf(SeriesPoint(1.0, 72.0), SeriesPoint(2.0, 71.2), SeriesPoint(3.0, 71.6))
        assertEquals(
            "Bodyweight. 3 points, from 72.0 on day 1 to 71.6 on day 3. low 71.2, high 72.0",
            describeSeries("Bodyweight", points, { "day ${it.toInt()}" }, { "%.1f".format(it) }),
        )
    }

    @Test
    fun `a bar chart reads every bar`() {
        assertEquals(
            "Sends per week. W1 2, W2 0, W3 5",
            describeBars("Sends per week", listOf(BarDatum("W1", 2.0), BarDatum("W2", 0.0), BarDatum("W3", 5.0))) { it.toInt().toString() },
        )
    }

    @Test
    fun `times are said in words`() {
        assertEquals("45 seconds", spokenDuration(45))
        assertEquals("1 minute", spokenDuration(60))
        assertEquals("2 minutes 30 seconds", spokenDuration(150))
        assertEquals("1 second", spokenDuration(1))
    }

    @Test
    fun `the consistency grid reads the recent weeks by day`() {
        val today = LocalDate.of(2026, 10, 15) // a Thursday
        val activity = mapOf(today.minusDays(1) to 2, today.minusDays(8) to 1)
        val text = consistencyDescription(activity, today, today.minusWeeks(3).minusDays(3), 4, 2)
        assertEquals(
            "2 active days in the last 4 weeks, since 21 Sept. This week: Wednesday. Last week: Wednesday. " +
                "Week of 28 Sept: no days. Week of 21 Sept: no days",
            text,
        )
    }
}
