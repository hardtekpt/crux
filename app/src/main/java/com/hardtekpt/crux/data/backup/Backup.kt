package com.hardtekpt.crux.data.backup

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.ThemeMode
import com.hardtekpt.crux.data.prefs.UnitSystem
import java.io.Closeable
import java.io.File
import kotlinx.serialization.Serializable

/** What a backup can carry. Each can be switched on or off for export and import. */
enum class BackupSection(val label: String, val description: String, val noun: String) {
    EXERCISES("Exercise list", "Your exercise library", "exercises"),
    PLANS("Plan list", "Session plans, with the exercises they use", "plans"),
    JOURNAL("Journal", "Every climb you logged, with its photo and video", "climbs"),
    SESSIONS("Session history", "Finished sessions, with every set you logged", "sessions"),
    PLACES("Places", "Gyms, crags and boards, with their walls, wall images and climbs", "places"),
    BODY("Body stats", "Weigh-ins, body stats and circumferences", "body stats"),
    RECORDS("Personal records", "Results logged on exercises", "records"),
    NOTES("Notes", "Everything in your notes", "notes"),
    SETTINGS("Settings", "Grades, theme, units, timer and your Home layout", "settings"),
}

/** Which media go into a backup, and come out of one. Both are on unless the climber says otherwise. */
data class BackupMedia(val photos: Boolean = true, val videos: Boolean = true) {
    companion object {
        val NONE = BackupMedia(photos = false, videos = false)
    }
}

// The file format. Plain names and enum names, no database ids, so a backup imports cleanly
// into any install and stays readable. `version` lets later formats read older files.
// Version 1 was this JSON on its own, with photos inside it as base64. Version 2 is a zip
// archive: this JSON as `backup.json`, and the photos and videos it names under `media/`.

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val exercises: List<ExerciseDto>? = null,
    val plans: List<PlanDto>? = null,
    val climbs: List<ClimbDto>? = null,
    val places: List<PlaceDto>? = null,
    val bodyMeasurements: List<MeasurementDto>? = null,
    val records: List<RecordDto>? = null,
    val notes: List<NoteDto>? = null,
    /** Finished sessions. Version 1 reserved the key and always left it null. */
    val sessions: List<SessionDto>? = null,
    val settings: SettingsDto? = null,
) {
    /** How many records each section holds; null when the section is not in the file. */
    fun count(section: BackupSection): Int? = when (section) {
        BackupSection.EXERCISES -> exercises?.size
        BackupSection.PLANS -> plans?.size
        BackupSection.JOURNAL -> climbs?.size
        BackupSection.SESSIONS -> sessions?.size
        BackupSection.PLACES -> places?.size
        BackupSection.BODY -> bodyMeasurements?.size
        BackupSection.RECORDS -> records?.size
        BackupSection.NOTES -> notes?.size
        BackupSection.SETTINGS -> settings?.let { 1 }
    }

    companion object {
        const val FORMAT = "crux-backup"
        const val VERSION = 2
    }
}

/** Just enough of a file to tell whether this app can read the rest. */
@Serializable
internal data class BackupHeader(val format: String? = null, val version: Int = 1)

@Serializable
data class ExerciseDto(
    val name: String,
    val category: ExerciseCategory,
    val metric: MetricType,
    val notes: String? = null,
    /** What plans and sessions start it at. Older backups omit it. */
    val defaults: ExerciseDefaultsDto? = null,
    /** When it was added, epoch millis. Older backups omit it; it becomes the import time. */
    val createdAt: Long? = null,
)

/** An exercise's defaults; for interval exercises, also the timer's preparation. */
@Serializable
data class ExerciseDefaultsDto(
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val loadKg: Double,
    val restSeconds: Int,
    val repRestSeconds: Int = 0,
    val prepSeconds: Int? = null,
)

@Serializable
data class PlanItemDto(
    val exercise: ExerciseDto,
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val loadKg: Double,
    val restSeconds: Int,
    /** Interval exercises: rest between repeats. Older backups omit it. */
    val repRestSeconds: Int = 0,
)

@Serializable
data class PlanBlockDto(val name: String, val items: List<PlanItemDto>)

@Serializable
data class PlanDto(val name: String, val description: String, val blocks: List<PlanBlockDto>)

@Serializable
data class ClimbDto(
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val grade: String,
    val style: AscentStyle,
    val attempts: Int,
    val venue: Venue,
    val date: String,
    /** When it was logged, epoch millis. Also what tells one climb from another on import. */
    val loggedAt: Long,
    val name: String? = null,
    /** The saved place's name now, or the name typed when it was logged. */
    val place: String? = null,
    val notes: String? = null,
    /** Set when the climb is linked to a saved place; with [place] it finds that place again. */
    val placeType: PlaceType? = null,
    val area: String? = null,
    val problem: String? = null,
    val angle: Int? = null,
    val effort: Int? = null,
    /** Local grades: the position in the place's scale and its tape colour; [grade] holds the label. */
    val gradeIndex: Int? = null,
    val gradeColour: Long? = null,
    /** Version 1: the climb's photo as base64 JPEG. Older backups omit it. */
    val image: String? = null,
    /** The name of the place's section it was in. Older backups omit it. */
    val section: String? = null,
    /** The place name as typed when it was logged, kept if the place is renamed. Older backups omit it. */
    val loggedPlace: String? = null,
    /** The session it was logged in, by the session's [SessionDto.startedAt]. */
    val session: Long? = null,
    /** Of [attempts], how many were sends. Older backups omit it: one for a send, none for an attempt. */
    val sends: Int? = null,
    /** Version 2: the photo and video, by their file names under `media/` in the archive. */
    val photoFile: String? = null,
    val videoFile: String? = null,
)

@Serializable
data class AreaDto(
    val name: String,
    val angle: Int? = null,
    val resetDate: String? = null,
    /** Version 1: the wall's photo or map as base64 JPEG. Older backups omit it. */
    val image: String? = null,
    /** Which of the place's kinds the area is; older backups omit it (the main kind). */
    val type: PlaceType? = null,
    /** The name of the section it's in; older backups omit it. */
    val section: String? = null,
    /** Version 2: the wall's photo or map, by its file name under `media/` in the archive. */
    val imageFile: String? = null,
)

/** One named part of a place. */
@Serializable
data class SectionDto(
    val type: PlaceType,
    val name: String,
    /** The grades this part uses; older backups graded the whole place and omit these. */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    val localScale: LocalScale? = null,
)

@Serializable
data class ProblemDto(
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val grade: String,
    val area: String? = null,
    val tape: Int? = null,
    val setDate: String? = null,
    val retired: Boolean = false,
    val notes: String? = null,
    val gradeIndex: Int? = null,
    val gradeColour: Long? = null,
    /** When it was added, epoch millis. Older backups omit it. */
    val createdAt: Long? = null,
)

@Serializable
data class PlaceDto(
    val name: String,
    val type: PlaceType,
    val location: String? = null,
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    val defaultAngle: Int? = null,
    val notes: String? = null,
    val areas: List<AreaDto> = emptyList(),
    val problems: List<ProblemDto> = emptyList(),
    val localScale: LocalScale? = null,
    val favourite: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /** The other kinds of climbing at the place; older backups omit it. */
    val extraTypes: List<PlaceType> = emptyList(),
    /** The place's named parts, in order; older backups omit it and get one per kind. */
    val sections: List<SectionDto> = emptyList(),
    /** When it was added, epoch millis. Older backups omit it. */
    val createdAt: Long? = null,
)

@Serializable
data class RecordDto(
    val exercise: ExerciseDto,
    val date: String,
    val reps: Int? = null,
    val seconds: Int? = null,
    val loadKg: Double? = null,
    val notes: String? = null,
    val loggedAt: Long,
)

@Serializable
data class NoteDto(val text: String, val createdAt: Long, val updatedAt: Long, val pinned: Boolean = false, val tag: String? = null)

@Serializable
data class MeasurementDto(val type: MeasurementType, val value: Double, val date: String, val loggedAt: Long)

/** A finished session. [startedAt] tells one from another, and links its climbs to it. */
@Serializable
data class SessionDto(
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** The plan it was started from, by name, if that plan still existed. */
    val plan: String? = null,
    val place: String? = null,
    val placeType: PlaceType? = null,
    val section: String? = null,
    val effort: Int? = null,
    val notes: String? = null,
    val items: List<SessionItemDto> = emptyList(),
)

/** One exercise in a session: its target, and what was done. */
@Serializable
data class SessionItemDto(
    val exercise: ExerciseDto,
    /** The plan block it came from; empty for exercises added during the session. */
    val block: String = "",
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val loadKg: Double,
    val restSeconds: Int,
    val repRestSeconds: Int = 0,
    val done: List<SessionSetDto> = emptyList(),
)

@Serializable
data class SessionSetDto(
    /** 0-based set number within the exercise. */
    val set: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val loadKg: Double? = null,
    val skipped: Boolean = false,
    val completedAt: Long,
)

/** App settings. Each is optional, so a later app can add some without breaking older files. */
@Serializable
data class SettingsDto(
    val theme: ThemeMode? = null,
    /** A name, not an enum, so an accent a later app adds doesn't break the file. */
    val accent: String? = null,
    /** A name too, like [accent]. */
    val textSize: String? = null,
    val units: UnitSystem? = null,
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    val timerSounds: Boolean? = null,
    val timerCompact: Boolean? = null,
    /** The Home layout, in order. Names, not enums, so widgets a later app drops don't break the file. */
    val dashboard: List<WidgetDto>? = null,
)

@Serializable
data class WidgetDto(val type: String, val size: String)

/**
 * A backup read from a file. Its photos and videos wait in [mediaDir] until it is imported;
 * [close] removes them.
 */
class OpenedBackup(val file: BackupFile, private val mediaDir: File? = null) : Closeable {
    /** A media file the backup holds, if it holds it. */
    fun media(name: String?): File? = name?.let { mediaDir?.resolve(it) }?.takeIf { it.isFile }

    override fun close() {
        mediaDir?.deleteRecursively()
    }
}

/** What to do with a record from a backup that is already here. */
enum class Resolution(val label: String, val description: String) {
    SKIP("Skip", "Keep what's in Crux"),
    REPLACE("Replace", "Use the backup's version"),
    KEEP_BOTH("Keep both", "Add the backup's as a copy"),
}

/** Names one record in one section of a backup. */
data class ConflictKey(val section: BackupSection, val key: String)

/** A record in the backup that is already here, described both ways for the climber to choose. */
data class ImportConflict(val id: ConflictKey, val title: String, val existing: String, val incoming: String, val identical: Boolean) {
    val section: BackupSection get() = id.section
}

/** What an import did, per section. */
data class ImportResult(
    val added: Map<BackupSection, Int>,
    val skipped: Map<BackupSection, Int>,
    val replaced: Map<BackupSection, Int> = emptyMap(),
    /** Records kept alongside what was already here. */
    val copied: Map<BackupSection, Int> = emptyMap(),
    /** Records the file holds but this app can't read, such as a grade no scale has. */
    val unreadable: Int = 0,
)

class BackupFormatException(message: String) : Exception(message)
