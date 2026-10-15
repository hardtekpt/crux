package com.hardtekpt.crux.data.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Climbs and sessions as a table for a spreadsheet app, worked out from a [BackupFile]. RFC 4180:
 * comma separated, CRLF line ends, fields with a comma, quote or line break quoted, dates in ISO form.
 */
object CsvExport {
    private const val EOL = "\r\n"

    private val CLIMB_HEADER =
        listOf("Date", "Name", "Grade", "Scale", "Discipline", "Style", "Attempts", "Sends", "Place", "Facility", "Wall", "Effort", "Notes", "Setter")
    private val SESSION_HEADER = listOf("Started", "Session", "Finished", "Effort", "Exercise", "Set", "Reps", "Seconds", "Load (kg)", "Skipped")

    /** One row per climb, oldest first. */
    fun climbs(file: BackupFile): String = table(
        CLIMB_HEADER,
        file.climbs.orEmpty().sortedWith(compareBy({ it.date }, { it.loggedAt })).map { climb ->
            listOf(
                climb.date,
                climb.name,
                climb.grade,
                climb.gradeScale.label,
                climb.discipline.label,
                climb.style.label,
                climb.attempts.toString(),
                (climb.sends ?: if (climb.style.isSend) 1 else 0).toString(),
                climb.place ?: climb.loggedPlace,
                climb.section,
                climb.area,
                climb.effort?.toString(),
                climb.notes,
                climb.setter,
            )
        },
    )

    /** One row per set done or skipped, and one row for a session with none. Times are in [zone]. */
    fun sessions(file: BackupFile, zone: ZoneId = ZoneId.systemDefault()): String = table(
        SESSION_HEADER,
        file.sessions.orEmpty().sortedBy { it.startedAt }.flatMap { session ->
            val head = listOf(time(session.startedAt, zone), session.name, session.endedAt?.let { time(it, zone) }, session.effort?.toString())
            val sets = session.items.flatMap { item -> item.done.sortedBy { it.set }.map { item to it } }
            if (sets.isEmpty()) {
                listOf(head + List(6) { null })
            } else {
                sets.map { (item, set) ->
                    head + listOf(
                        item.exercise.name,
                        (set.set + 1).toString(),
                        set.reps?.toString(),
                        set.seconds?.toString(),
                        set.loadKg?.let(::number),
                        set.skipped.toString(),
                    )
                }
            }
        },
    )

    private fun time(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))

    private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun table(header: List<String>, rows: List<List<String?>>): String =
        (listOf(header) + rows).joinToString(separator = "", transform = { row -> row.joinToString(",") { field(it) } + EOL })

    /** A field, quoted when it holds a comma, a quote or a line break; quotes are doubled. */
    private fun field(value: String?): String {
        if (value == null) return ""
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
