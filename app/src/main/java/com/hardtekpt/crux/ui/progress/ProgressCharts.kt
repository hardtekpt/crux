package com.hardtekpt.crux.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.ui.charts.BarChart
import com.hardtekpt.crux.ui.charts.BarDatum
import com.hardtekpt.crux.ui.charts.ChartCard
import com.hardtekpt.crux.ui.charts.DonutChart
import com.hardtekpt.crux.ui.charts.HorizontalBarList
import com.hardtekpt.crux.ui.charts.RankedDatum
import com.hardtekpt.crux.ui.charts.SliceDatum
import com.hardtekpt.crux.ui.charts.formatTick
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Grade pyramid for one discipline in one scale: sends per grade, hardest first. */
data class GradePyramid(val discipline: Discipline, val scaleLabel: String, val rows: List<RankedDatum>)

/** The chart data Progress shows, derived from the journal. */
data class ProgressCharts(
    /** Sends per week, oldest first, ending with the current week. */
    val weeklySends: List<BarDatum> = emptyList(),
    val pyramids: List<GradePyramid> = emptyList(),
    /** Sends split by style across both disciplines. */
    val sendsByStyle: List<SliceDatum> = emptyList(),
) {
    val hasSends: Boolean get() = sendsByStyle.isNotEmpty()
}

fun progressCharts(climbs: List<Climb>, scales: GradeScales, today: LocalDate, weeks: Int = 8, pyramidRows: Int = 6): ProgressCharts {
    val sends = climbs.filter { it.style.isSend }
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekly = (weeks - 1 downTo 0).map { back ->
        val start = thisMonday.minusWeeks(back.toLong())
        val end = start.plusDays(7)
        BarDatum(
            label = start.shortLabel(),
            value = sends.count { !it.date.isBefore(start) && it.date.isBefore(end) }.toDouble(),
        )
    }
    // Pyramids use the scale chosen in Settings; climbs logged in another scale are not converted.
    val pyramids = Discipline.entries.mapNotNull { discipline ->
        val scale = scales.forDiscipline(discipline)
        val counts = sends.filter { it.discipline == discipline && it.gradeScale == scale }
            .groupingBy { it.gradeIndex }
            .eachCount()
        if (counts.isEmpty()) return@mapNotNull null
        GradePyramid(
            discipline = discipline,
            scaleLabel = scale.label,
            rows = counts.entries.sortedByDescending { it.key }.take(pyramidRows)
                .map { RankedDatum(scale.label(it.key), it.value.toDouble()) },
        )
    }
    val byStyle = sends.groupingBy { it.style }.eachCount().entries
        .sortedBy { it.key.ordinal }
        .map { SliceDatum(it.key.label, it.value.toDouble()) }
    return ProgressCharts(weekly, pyramids, byStyle)
}

@Composable
fun ProgressChartCards(charts: ProgressCharts, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
        val weeklyTotal = charts.weeklySends.sumOf { it.value }
        ChartCard(
            title = "Sends per week",
            trailing = "${formatTick(weeklyTotal)} in ${charts.weeklySends.size} weeks",
            modifier = Modifier.testTag("chart_weekly"),
        ) {
            BarChart(
                bars = charts.weeklySends,
                formatValue = ::formatTick,
                description = "Sends per week: " + charts.weeklySends.joinToString { "${it.label} ${formatTick(it.value)}" },
            )
        }
        charts.pyramids.forEach { pyramid ->
            ChartCard(
                title = "${pyramid.discipline.label} pyramid",
                trailing = "${pyramid.scaleLabel} · sends per grade",
                modifier = Modifier.testTag("chart_pyramid_${pyramid.discipline.name}"),
            ) {
                HorizontalBarList(rows = pyramid.rows, formatValue = ::formatTick, accentFirst = true)
            }
        }
        if (charts.sendsByStyle.isNotEmpty()) {
            val total = charts.sendsByStyle.sumOf { it.value }
            ChartCard(title = "Sends by style", modifier = Modifier.testTag("chart_styles")) {
                DonutChart(
                    slices = charts.sendsByStyle,
                    centerValue = formatTick(total),
                    centerLabel = "sends",
                    formatValue = ::formatTick,
                )
            }
        }
        Text(
            "Tap a point, bar or slice to read it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CruxTheme.space.s1),
        )
    }
}
