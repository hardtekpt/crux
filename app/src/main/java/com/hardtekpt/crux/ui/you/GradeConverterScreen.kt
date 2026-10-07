package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeConversion
import com.hardtekpt.crux.data.model.GradeConversion.System
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.input.GradeStrip
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono

private val Mono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)

/**
 * A standalone grade converter: pick a system and a grade, see it in every other system,
 * with the whole chart below. It reads and writes nothing; logged climbs never convert.
 */
@Composable
fun GradeConverterScreen(onBack: () -> Unit) {
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    var discipline by rememberSaveable { mutableStateOf(Discipline.ROUTE) }
    var routeSystem by rememberSaveable { mutableStateOf(System.FRENCH) }
    var boulderSystem by rememberSaveable { mutableStateOf(System.FONT) }
    var routeGrade by rememberSaveable { mutableStateOf("7a") }
    var boulderGrade by rememberSaveable { mutableStateOf("6C") }

    val system = if (discipline == Discipline.BOULDER) boulderSystem else routeSystem
    val grade = if (discipline == Discipline.BOULDER) boulderGrade else routeGrade
    val grades = GradeConversion.grades(system)
    fun pick(newSystem: System, newGrade: String) {
        if (discipline == Discipline.BOULDER) {
            boulderSystem = newSystem
            boulderGrade = newGrade
        } else {
            routeSystem = newSystem
            routeGrade = newGrade
        }
    }

    /** Switch system, keeping the same difficulty (the first grade it maps to). */
    fun switchTo(target: System) {
        val mapped = GradeConversion.rowsFor(system, grade).firstOrNull()?.values?.get(target) ?: GradeConversion.grades(target).first()
        pick(target, mapped)
    }
    val others = GradeConversion.systems(discipline).filter { it != system }
    val covered = GradeConversion.rowsFor(system, grade).toSet()

    Column(Modifier.fillMaxSize().testTag("screen_GradeConverter")) {
        CruxTopAppBar(title = "Grade converter", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s4),
            modifier = Modifier.testTag("converter_list"),
        ) {
            item(key = "discipline") {
                CruxSegmentedButtons(
                    options = listOf(Discipline.ROUTE, Discipline.BOULDER),
                    selected = discipline,
                    label = { if (it == Discipline.BOULDER) "Boulders" else "Routes" },
                    onSelect = { discipline = it },
                    modifier = Modifier.testTag("converter_discipline"),
                )
            }

            // The source: system chips, the grade large, and a strip to pick it.
            item(key = "source") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceContainerLow)
                        .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(24.dp))
                        .padding(vertical = space.s4),
                    verticalArrangement = Arrangement.spacedBy(space.s3),
                ) {
                    Eyebrow("Convert from", Modifier.padding(horizontal = space.s4))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space.s2),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = space.s4),
                    ) {
                        GradeConversion.systems(discipline).forEach { option ->
                            CruxFilterChip(option.label, option == system, { if (option != system) switchTo(option) }, Modifier.testTag("from_${option.name}"))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = space.s2)) {
                        Text(
                            grade,
                            style = Mono.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
                            color = colors.primary,
                            modifier = Modifier.testTag("converter_grade"),
                        )
                        Text("${system.label} · ${system.region}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    GradeStrip(
                        grades = grades,
                        selectedIndex = grades.indexOf(grade).coerceAtLeast(0),
                        onSelect = { pick(system, grades[it]) },
                        tagPrefix = "convert",
                        description = "${system.label} grade",
                    )
                }
            }

            // The answers: one tile per other system; tap one to convert from it instead.
            item(key = "results") {
                Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    Eyebrow("Roughly the same as")
                    others.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                            pair.forEach { target ->
                                val value = GradeConversion.convert(system, grade, target) ?: "–"
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surfaceContainer)
                                        .clickable { switchTo(target) }
                                        .padding(horizontal = space.s4, vertical = space.s3)
                                        .testTag("result_${target.name}"),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(target.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                    Text(value, style = Mono.copy(fontSize = 24.sp, lineHeight = 28.sp), modifier = Modifier.testTag("value_${target.name}"))
                                    Text(target.region, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // The whole chart, the current grade's rows lit.
            item(key = "chart_label") { Eyebrow("Full chart · tap a row") }
            item(key = "chart_head") {
                ChartRow(GradeConversion.systems(discipline).map { it.label }, highlighted = false, header = true)
            }
            itemsIndexed(GradeConversion.rows(discipline), key = { index, _ -> "row_${discipline}_$index" }) { _, row ->
                val lit = row in covered
                ChartRow(
                    GradeConversion.systems(discipline).map { row.values[it].orEmpty() },
                    highlighted = lit,
                    onClick = { pick(system, row.values.getValue(system)) },
                )
            }
            item(key = "note") {
                Text(
                    "Conversions are approximate: grades vary between areas, rock and styles, and British grades rate trad climbs. " +
                        "Based on Mountain Project's international grade chart and Wikipedia's grade comparison tables. Your logged climbs keep the grade you gave them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartRow(cells: List<String>, highlighted: Boolean, header: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (header) 0.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp)
            .then(if (highlighted) Modifier.testTag("chart_row_lit") else Modifier),
    ) {
        cells.forEach { cell ->
            Text(
                cell,
                style = if (header) MaterialTheme.typography.labelSmall else Mono.copy(fontSize = 13.sp),
                color = when {
                    header -> colors.onSurfaceVariant
                    highlighted -> colors.onPrimaryContainer
                    else -> colors.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
