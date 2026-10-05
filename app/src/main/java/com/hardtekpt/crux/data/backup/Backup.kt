package com.hardtekpt.crux.data.backup

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.local.TemplateBlockEntity
import com.hardtekpt.crux.data.local.TemplateExerciseEntity
import com.hardtekpt.crux.data.local.WorkoutTemplateEntity
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.Venue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup can carry. Each can be switched on or off for export and import. */
enum class BackupSection(val label: String, val description: String, val available: Boolean = true) {
    EXERCISES("Exercise list", "Your exercise library"),
    PLANS("Plan list", "Session plans, with the exercises they use"),
    JOURNAL("Journal", "Every climb you logged"),
    BODY("Body stats", "Weigh-ins and height"),
    SESSIONS("Session history", "Arrives with the session logger", available = false),
}

// The file format. Plain names and enum names, no database ids, so a backup imports cleanly
// into any install and stays readable. `version` lets later formats read older files.

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val exercises: List<ExerciseDto>? = null,
    val plans: List<PlanDto>? = null,
    val climbs: List<ClimbDto>? = null,
    val bodyMeasurements: List<MeasurementDto>? = null,
    /** Reserved for the session logger; always null for now. */
    val sessions: List<String>? = null,
) {
    /** How many records each section holds; null when the section is not in the file. */
    fun count(section: BackupSection): Int? = when (section) {
        BackupSection.EXERCISES -> exercises?.size
        BackupSection.PLANS -> plans?.size
        BackupSection.JOURNAL -> climbs?.size
        BackupSection.BODY -> bodyMeasurements?.size
        BackupSection.SESSIONS -> sessions?.size
    }

    companion object {
        const val FORMAT = "crux-backup"
        const val VERSION = 1
    }
}

@Serializable
data class ExerciseDto(val name: String, val category: ExerciseCategory, val metric: MetricType, val notes: String? = null)

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
    val loggedAt: Long,
    val name: String? = null,
    val place: String? = null,
    val notes: String? = null,
)

@Serializable
data class MeasurementDto(val type: MeasurementType, val value: Double, val date: String, val loggedAt: Long)

/** How many records an import added and how many it skipped as already present. */
data class ImportResult(val added: Map<BackupSection, Int>, val skipped: Map<BackupSection, Int>)

class BackupFormatException(message: String) : Exception(message)

@Singleton
class BackupRepository(
    /** The data set to back up or restore into: whichever is active. */
    private val database: suspend () -> CruxDatabase,
    private val clock: Clock,
) {
    @Inject
    constructor(dbs: CruxDatabases, clock: Clock) : this({ dbs.current() }, clock)

    constructor(db: CruxDatabase, clock: Clock) : this({ db }, clock)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(sections: Set<BackupSection>): String {
        val db = database()
        val exercises = db.exerciseDao().getAll()
        val byId = exercises.associateBy { it.id }
        val file = BackupFile(
            exportedAt = Instant.now(clock).toString(),
            exercises = if (BackupSection.EXERCISES in sections) exercises.map { it.toDto() } else null,
            plans = if (BackupSection.PLANS in sections) {
                db.templateDao().getAll().map { plan ->
                    PlanDto(
                        name = plan.template.name,
                        description = plan.template.description,
                        blocks = plan.blocks.sortedBy { it.block.position }.map { block ->
                            PlanBlockDto(
                                name = block.block.name,
                                items = block.exercises.sortedBy { it.item.position }.map {
                                    PlanItemDto(
                                        exercise = (byId[it.item.exerciseId] ?: it.exercise).toDto(),
                                        sets = it.item.sets,
                                        reps = it.item.reps,
                                        seconds = it.item.seconds,
                                        loadKg = it.item.loadKg,
                                        restSeconds = it.item.restSeconds,
                                        repRestSeconds = it.item.repRestSeconds,
                                    )
                                },
                            )
                        },
                    )
                }
            } else {
                null
            },
            climbs = if (BackupSection.JOURNAL in sections) db.climbDao().getAll().map { it.toDto() } else null,
            bodyMeasurements = if (BackupSection.BODY in sections) db.bodyMeasurementDao().getAll().map { it.toDto() } else null,
        )
        return json.encodeToString(file)
    }

    /** Reads a backup file, refusing anything that is not one. */
    fun parse(text: String): BackupFile {
        val file = runCatching { json.decodeFromString<BackupFile>(text) }
            .getOrElse { throw BackupFormatException("This file is not a Crux backup.") }
        if (file.format != BackupFile.FORMAT) throw BackupFormatException("This file is not a Crux backup.")
        if (file.version > BackupFile.VERSION) {
            throw BackupFormatException("This backup comes from a newer version of Crux. Update the app to import it.")
        }
        return file
    }

    /**
     * Adds what the file holds for the chosen sections. Nothing is deleted: records that are
     * already here (same exercise or plan name, same climb, same weigh-in) are skipped.
     */
    suspend fun import(file: BackupFile, sections: Set<BackupSection>): ImportResult {
        val db = database()
        return db.withTransaction { importInto(db, file, sections) }
    }

    private suspend fun importInto(db: CruxDatabase, file: BackupFile, sections: Set<BackupSection>): ImportResult {
        val added = mutableMapOf<BackupSection, Int>()
        val skipped = mutableMapOf<BackupSection, Int>()
        val now = clock.millis()
        val exerciseDao = db.exerciseDao()
        val existing = exerciseDao.getAll().associateBy { it.name.lowercase() }.toMutableMap()

        suspend fun exerciseId(dto: ExerciseDto, countAs: BackupSection?): Long {
            existing[dto.name.lowercase()]?.let {
                if (countAs != null) skipped.merge(countAs, 1, Int::plus)
                return it.id
            }
            val entity = ExerciseEntity(
                name = dto.name.trim(),
                category = dto.category,
                metric = dto.metric,
                notes = dto.notes,
                createdAtMillis = now,
            )
            val id = exerciseDao.insert(entity)
            existing[dto.name.lowercase()] = entity.copy(id = id)
            if (countAs != null) added.merge(countAs, 1, Int::plus)
            return id
        }

        if (BackupSection.EXERCISES in sections) {
            file.exercises?.forEach { exerciseId(it, BackupSection.EXERCISES) }
        }

        if (BackupSection.PLANS in sections) {
            val templateDao = db.templateDao()
            val planNames = templateDao.getAll().map { it.template.name.lowercase() }.toMutableSet()
            file.plans?.forEach { plan ->
                if (plan.name.lowercase() in planNames) {
                    skipped.merge(BackupSection.PLANS, 1, Int::plus)
                    return@forEach
                }
                val templateId = templateDao.insertTemplate(
                    WorkoutTemplateEntity(name = plan.name, description = plan.description, position = templateDao.nextPosition()),
                )
                plan.blocks.forEachIndexed { blockPosition, block ->
                    val blockId = templateDao.insertBlock(
                        TemplateBlockEntity(templateId = templateId, position = blockPosition, name = block.name),
                    )
                    templateDao.insertExercises(
                        block.items.mapIndexed { position, item ->
                            TemplateExerciseEntity(
                                blockId = blockId,
                                // A plan brings the exercises it needs, even when the library is not imported.
                                exerciseId = exerciseId(item.exercise, countAs = null),
                                position = position,
                                sets = item.sets,
                                reps = item.reps,
                                seconds = item.seconds,
                                loadKg = item.loadKg,
                                restSeconds = item.restSeconds,
                                repRestSeconds = item.repRestSeconds,
                            )
                        },
                    )
                }
                planNames += plan.name.lowercase()
                added.merge(BackupSection.PLANS, 1, Int::plus)
            }
        }

        if (BackupSection.JOURNAL in sections) {
            val climbDao = db.climbDao()
            val seen = climbDao.getAll().map { it.toDto().identity() }.toMutableSet()
            file.climbs?.forEach { dto ->
                val index = dto.gradeScale.grades.indexOf(dto.grade)
                if (index < 0 || dto.identity() in seen) {
                    skipped.merge(BackupSection.JOURNAL, 1, Int::plus)
                    return@forEach
                }
                climbDao.insert(
                    ClimbEntity(
                        discipline = dto.discipline,
                        gradeScale = dto.gradeScale,
                        gradeIndex = index,
                        style = dto.style,
                        attempts = dto.attempts,
                        venue = dto.venue,
                        dateEpochDay = java.time.LocalDate.parse(dto.date).toEpochDay(),
                        createdAtMillis = dto.loggedAt,
                        name = dto.name,
                        place = dto.place,
                        notes = dto.notes,
                    ),
                )
                seen += dto.identity()
                added.merge(BackupSection.JOURNAL, 1, Int::plus)
            }
        }

        if (BackupSection.BODY in sections) {
            val dao = db.bodyMeasurementDao()
            val seen = dao.getAll().map { it.toDto().copy(loggedAt = 0) }.toMutableSet()
            file.bodyMeasurements?.forEach { dto ->
                val key = dto.copy(loggedAt = 0)
                if (key in seen) {
                    skipped.merge(BackupSection.BODY, 1, Int::plus)
                    return@forEach
                }
                dao.insert(
                    BodyMeasurementEntity(
                        type = dto.type,
                        value = dto.value,
                        dateEpochDay = java.time.LocalDate.parse(dto.date).toEpochDay(),
                        createdAtMillis = dto.loggedAt,
                    ),
                )
                seen += key
                added.merge(BackupSection.BODY, 1, Int::plus)
            }
        }
        return ImportResult(added, skipped)
    }
}

private fun ExerciseEntity.toDto() = ExerciseDto(name, category, metric, notes)

private fun ClimbEntity.toDto() = ClimbDto(
    discipline = discipline,
    gradeScale = gradeScale,
    grade = gradeScale.label(gradeIndex),
    style = style,
    attempts = attempts,
    venue = venue,
    date = java.time.LocalDate.ofEpochDay(dateEpochDay).toString(),
    loggedAt = createdAtMillis,
    name = name,
    place = place,
    notes = notes,
)

/** Two climbs are the same entry when everything but notes matches. */
private fun ClimbDto.identity() = copy(notes = null)

private fun BodyMeasurementEntity.toDto() = MeasurementDto(
    type = type,
    value = value,
    date = java.time.LocalDate.ofEpochDay(dateEpochDay).toString(),
    loggedAt = createdAtMillis,
)
