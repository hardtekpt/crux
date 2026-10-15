package com.hardtekpt.crux.data.backup

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.Venue
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {
    private fun climb(date: String, loggedAt: Long, notes: String? = null, style: AscentStyle = AscentStyle.REDPOINT) = ClimbDto(
        discipline = Discipline.BOULDER,
        gradeScale = GradeScale.FONT,
        grade = "6A",
        style = style,
        attempts = 3,
        venue = Venue.GYM,
        date = date,
        loggedAt = loggedAt,
        name = "Pink crimps",
        place = "Test Gym",
        notes = notes,
        area = "Cave",
        section = "Boulders",
        effort = 7,
    )

    private val hangs = ExerciseDto("Max hangs", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME)

    private fun file(climbs: List<ClimbDto>? = null, sessions: List<SessionDto>? = null) =
        BackupFile(exportedAt = "2026-10-14T10:00:00Z", climbs = climbs, sessions = sessions)

    @Test
    fun `climbs have a header and one row each, oldest first`() {
        val csv = CsvExport.climbs(file(climbs = listOf(climb("2026-10-12", 2), climb("2026-10-10", 1, style = AscentStyle.ATTEMPT))))

        assertEquals(
            "Date,Name,Grade,Scale,Discipline,Style,Attempts,Sends,Place,Facility,Wall,Effort,Notes,Setter\r\n" +
                "2026-10-10,Pink crimps,6A,Font,Boulder,Attempt,3,0,Test Gym,Boulders,Cave,7,,\r\n" +
                "2026-10-12,Pink crimps,6A,Font,Boulder,Redpoint,3,1,Test Gym,Boulders,Cave,7,,\r\n",
            csv,
        )
    }

    @Test
    fun `a note with a comma, a quote and a line break is quoted`() {
        val csv = CsvExport.climbs(file(climbs = listOf(climb("2026-10-12", 1, notes = "Heel hook, \"sloper\"\nrest after"))))

        assertEquals(true, csv.endsWith(",7,\"Heel hook, \"\"sloper\"\"\nrest after\",\r\n"))
    }

    @Test
    fun `an empty file is just the header`() {
        assertEquals(
            "Date,Name,Grade,Scale,Discipline,Style,Attempts,Sends,Place,Facility,Wall,Effort,Notes,Setter\r\n",
            CsvExport.climbs(file()),
        )
    }

    @Test
    fun `sessions have a row per set, and one row when they have none`() {
        // 2026-10-12 18:00:00 and 18:45:00 UTC.
        val started = 1_791_828_000_000L
        val withSets = SessionDto(
            name = "Fingers",
            startedAt = started,
            endedAt = started + 45 * 60_000,
            effort = 8,
            items = listOf(
                SessionItemDto(
                    hangs,
                    sets = 2,
                    reps = 1,
                    seconds = 10,
                    loadKg = 20.0,
                    restSeconds = 120,
                    done = listOf(
                        SessionSetDto(set = 1, seconds = 10, loadKg = 22.5, skipped = false, completedAt = started + 1),
                        SessionSetDto(set = 0, seconds = 10, loadKg = 20.0, skipped = false, completedAt = started),
                    ),
                ),
            ),
        )
        val empty = SessionDto(name = "Climbing session", startedAt = started + 86_400_000)

        val csv = CsvExport.sessions(file(sessions = listOf(empty, withSets)), ZoneOffset.UTC)

        assertEquals(
            "Started,Session,Finished,Effort,Exercise,Set,Reps,Seconds,Load (kg),Skipped\r\n" +
                "2026-10-12T18:00:00,Fingers,2026-10-12T18:45:00,8,Max hangs,1,,10,20,false\r\n" +
                "2026-10-12T18:00:00,Fingers,2026-10-12T18:45:00,8,Max hangs,2,,10,22.5,false\r\n" +
                "2026-10-13T18:00:00,Climbing session,,,,,,,,\r\n",
            csv,
        )
    }
}
