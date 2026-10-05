package com.hardtekpt.crux.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val GradeCell = 64.dp

/**
 * Grades in scale order on a sideways strip; the one in the middle is picked. Swipe, or tap
 * a grade to pick it. Never converts between scales: it shows the one it is given.
 */
@Composable
fun GradeStrip(
    grades: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "grade",
    description: String = "Grade",
    /** Tape colours (ARGB) for colour grades, drawn as a dot by each name. */
    colours: List<Long?>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val picker = rememberPickerScroll(grades.size, selectedIndex, onSelect)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val side = ((maxWidth - GradeCell) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = picker.list,
            flingBehavior = picker.fling,
            contentPadding = PaddingValues(horizontal = side),
            modifier = Modifier
                .fillMaxWidth()
                .fadeEdges(vertical = false, edge = 0.18f)
                .semantics {
                    contentDescription = description
                    grades.getOrNull(selectedIndex)?.let { stateDescription = it }
                }
                .testTag("${tagPrefix}_picker"),
        ) {
            itemsIndexed(grades, key = { index, grade -> "$index$grade" }) { index, grade ->
                val isSelected = index == selectedIndex
                val colour = colours?.getOrNull(index)?.let(::argb)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(GradeCell)
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                        ) {
                            onSelect(index)
                            picker.scrollTo(index)
                        }
                        .testTag("${tagPrefix}_$grade"),
                ) {
                    if (isSelected) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 58.dp, height = 52.dp)
                                .background(colors.primaryContainer, RoundedCornerShape(16.dp))
                                .border(CruxTheme.size.borderEmphasis, colors.primary, RoundedCornerShape(16.dp)),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                colour?.let { TapeSwatch(it, 10.dp) }
                                Text(
                                    grade,
                                    style = TextStyle(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = when {
                                            grade.length > 5 -> 12.sp
                                            grade.length > 4 -> 15.sp
                                            colour != null -> 16.sp
                                            else -> 22.sp
                                        },
                                        letterSpacing = (-0.4).sp,
                                    ),
                                    color = colors.onPrimaryContainer,
                                    maxLines = 1,
                                )
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            colour?.let { TapeSwatch(it, 14.dp) }
                            Text(
                                grade,
                                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = if (grade.length > 5) 11.sp else 15.sp),
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The last two weeks as a row of days, today at the end and picked by default. The calendar
 * button reaches older days; an older pick shows next to it.
 */
@Composable
fun DayStrip(
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    days: Int = 14,
) {
    val recent = remember(today, days) { (0 until days).map { today.minusDays(it.toLong()) } }
    val older = selected.takeIf { it !in recent && !it.isAfter(today) }
    // Reversed so today sits at the right edge and the strip opens there.
    LazyRow(
        reverseLayout = true,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = CruxTheme.space.s4),
        modifier = modifier.fillMaxWidth().testTag("day_strip"),
    ) {
        items(recent, key = { it.toEpochDay() }) { day ->
            DayCell(day, day == selected, today) { onSelect(day) }
        }
        if (older != null) {
            item(key = "older") { DayCell(older, true, today) {} }
        }
        item(key = "calendar") {
            val colors = MaterialTheme.colorScheme
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 48.dp, height = 60.dp)
                    .border(CruxTheme.size.borderHairline, colors.outline, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Pick an older day", onClick = onOpenCalendar)
                    .testTag("pick_date"),
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = "Older day", tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isSelected: Boolean, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val weekday = if (day == today) "TODAY" else day.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.UK).uppercase()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        modifier = Modifier
            .size(width = 48.dp, height = 60.dp)
            .then(
                if (isSelected) {
                    Modifier
                        .background(colors.primaryContainer, shape)
                        .border(CruxTheme.size.borderEmphasis, colors.primary, shape)
                } else {
                    Modifier.border(CruxTheme.size.borderHairline, colors.outline, shape)
                },
            )
            .semantics { selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .testTag("day_$day"),
    ) {
        Text(
            weekday,
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = if (day == today) 8.sp else 10.sp, letterSpacing = 0.8.sp),
            color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
        Text(
            day.dayOfMonth.toString(),
            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontFeatureSettings = "tnum"),
            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
        )
    }
}

/** A round swatch of a tape colour, with a hairline so dark and light tapes both show. */
@Composable
fun TapeSwatch(colour: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(colour, androidx.compose.foundation.shape.CircleShape)
            .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape),
    )
}

/** An ARGB value stored as a Long, as a Compose colour. */
fun argb(value: Long): Color = Color(value.toInt())
