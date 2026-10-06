package com.hardtekpt.crux.ui.journal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.ui.graphics.vector.ImageVector
import com.hardtekpt.crux.data.LoggedResult
import com.hardtekpt.crux.data.Note
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.ui.dayLabel
import java.time.LocalDate

/** One journal group: a day at a place. */
data class JournalDay(
    val date: LocalDate,
    val place: String?,
    val climbs: List<Climb>,
) {
    val key: String get() = "$date|${place.orEmpty()}|$venue"
    val venue: Venue get() = climbs.first().venue
    val title: String get() = listOfNotNull(date.dayLabel(), place, venue.label).joinToString(" · ")
}

/** Climbs arrive newest first; grouping keeps that order. */
fun List<Climb>.groupByDayAndPlace(): List<JournalDay> =
    groupBy { Triple(it.date, it.place, it.venue) }.map { (key, climbs) -> JournalDay(key.first, key.second, climbs) }

/**
 * Something that happened on a day. Workout sessions will be another kind once the session
 * logger exists; until then training shows as the results logged on exercises.
 */
sealed interface TimelineEntry {
    val key: String
    val kind: JournalFilter

    /** Climbs at one place on one day. */
    data class Climbs(val day: JournalDay) : TimelineEntry {
        override val key get() = "climbs|${day.key}"
        override val kind get() = JournalFilter.Climbs
    }

    /** Results logged on exercises that day. */
    data class Training(val date: LocalDate, val results: List<LoggedResult>) : TimelineEntry {
        override val key get() = "training|$date"
        override val kind get() = JournalFilter.Training
    }

    data class NoteEntry(val note: Note) : TimelineEntry {
        override val key get() = "note|${note.id}"
        override val kind get() = JournalFilter.Notes
    }

    /** How many things this entry counts as: climbs, results, or one note. */
    val size: Int
        get() = when (this) {
            is Climbs -> day.climbs.size
            is Training -> results.size
            is NoteEntry -> 1
        }
}

/** A day on the timeline with everything logged on it. */
data class TimelineDay(val date: LocalDate, val entries: List<TimelineEntry>)

enum class JournalFilter(val label: String, val icon: ImageVector) {
    All("All", Icons.Rounded.Timeline),
    Climbs("Climbs", Icons.Rounded.Landscape),
    Training("Training", Icons.Rounded.FitnessCenter),
    Notes("Notes", Icons.AutoMirrored.Rounded.Notes),
}

enum class JournalPeriod(val label: String, val days: Long?) {
    Week("Last 7 days", 7),
    Month("Last 30 days", 30),
    Quarter("Last 3 months", 91),
    Year("Last 12 months", 365),
    AllTime("All time", null),
}

enum class JournalResult(val label: String) { Any("Any"), Sent("Sends"), NotSent("Not sent yet") }

/**
 * What the journal shows. Every part combines with the others: a place or a result keeps
 * only climbs; a tag keeps only notes; search matches names, places, grades and text.
 */
data class JournalQuery(
    val kind: JournalFilter = JournalFilter.All,
    val search: String = "",
    val period: JournalPeriod = JournalPeriod.AllTime,
    val result: JournalResult = JournalResult.Any,
    val places: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
) {
    /** Filters set in the sheet (not the type pills or search), for the badge. */
    val activeFilters: Int get() =
        (if (period != JournalPeriod.AllTime) 1 else 0) + (if (result != JournalResult.Any) 1 else 0) + places.size + tags.size

    val isFiltered: Boolean get() = activeFilters > 0 || search.isNotBlank() || kind != JournalFilter.All
}

/** Newest day first; within a day, climbs, then training, then notes. */
fun buildTimeline(climbs: List<Climb>, results: List<LoggedResult>, notes: List<Note>): List<TimelineDay> {
    val entries = climbs.groupByDayAndPlace().map { it.date to TimelineEntry.Climbs(it) } +
        results.groupBy { it.record.date }.map { (date, list) -> date to TimelineEntry.Training(date, list) } +
        notes.sortedByDescending { it.id }.map { it.created to TimelineEntry.NoteEntry(it) }
    return entries.groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it })
        .map { (date, list) -> TimelineDay(date, list.sortedBy { it.kind.ordinal }) }
}

/** The days and entries that match [query], with empty ones dropped. */
fun List<TimelineDay>.matching(query: JournalQuery, today: LocalDate): List<TimelineDay> {
    val words = query.search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun matches(vararg text: String?): Boolean {
        if (words.isEmpty()) return true
        val haystack = text.filterNotNull().joinToString(" ").lowercase()
        return words.all { it in haystack }
    }
    val from = query.period.days?.let { today.minusDays(it - 1) }
    val climbsOnly = query.places.isNotEmpty() || query.result != JournalResult.Any
    val notesOnly = query.tags.isNotEmpty()
    return mapNotNull { day ->
        if (from != null && day.date.isBefore(from)) return@mapNotNull null
        val entries = day.entries.mapNotNull { entry ->
            if (query.kind != JournalFilter.All && entry.kind != query.kind) return@mapNotNull null
            when (entry) {
                is TimelineEntry.Climbs -> {
                    if (notesOnly) return@mapNotNull null
                    if (query.places.isNotEmpty() && entry.day.place !in query.places) return@mapNotNull null
                    val kept = entry.day.climbs.filter { climb ->
                        when (query.result) {
                            JournalResult.Any -> true
                            JournalResult.Sent -> climb.style.isSend
                            JournalResult.NotSent -> !climb.style.isSend
                        } && matches(climb.name, climb.place, climb.notes, climb.grade, climb.style.label, climb.venue.label)
                    }
                    if (kept.isEmpty()) null else TimelineEntry.Climbs(entry.day.copy(climbs = kept))
                }
                is TimelineEntry.Training -> {
                    if (climbsOnly || notesOnly) return@mapNotNull null
                    val kept = entry.results.filter { matches(it.exercise.name, it.record.notes) }
                    if (kept.isEmpty()) null else entry.copy(results = kept)
                }
                is TimelineEntry.NoteEntry -> {
                    if (climbsOnly) return@mapNotNull null
                    if (notesOnly && entry.note.tag !in query.tags) return@mapNotNull null
                    if (matches(entry.note.text, entry.note.tag)) entry else null
                }
            }
        }
        if (entries.isEmpty()) null else day.copy(entries = entries)
    }
}

fun List<TimelineDay>.count(): Int = sumOf { day -> day.entries.sumOf { it.size } }
