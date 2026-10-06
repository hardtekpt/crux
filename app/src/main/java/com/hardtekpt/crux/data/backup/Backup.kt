package com.hardtekpt.crux.data.backup

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.ClimbMediaEntity
import com.hardtekpt.crux.data.local.MediaKind
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.local.AreaEntity
import com.hardtekpt.crux.data.local.PlaceEntity
import com.hardtekpt.crux.data.local.ProblemEntity
import com.hardtekpt.crux.data.local.TemplateBlockEntity
import com.hardtekpt.crux.data.local.TemplateExerciseEntity
import com.hardtekpt.crux.data.local.WorkoutTemplateEntity
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.gradeLabel
import com.hardtekpt.crux.data.model.Venue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.hardtekpt.crux.data.images.AreaImageStore
import java.time.Clock
import java.util.Base64
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup can carry. Each can be switched on or off for export and import. */
enum class BackupSection(val label: String, val description: String, val available: Boolean = true) {
    EXERCISES("Exercise list", "Your exercise library"),
    PLANS("Plan list", "Session plans, with the exercises they use"),
    JOURNAL("Journal", "Every climb you logged, with its photo"),
    PLACES("Places", "Gyms, crags and boards, with their walls, wall images and problems"),
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
    val places: List<PlaceDto>? = null,
    val bodyMeasurements: List<MeasurementDto>? = null,
    /** Reserved for the session logger; always null for now. */
    val sessions: List<String>? = null,
) {
    /** How many records each section holds; null when the section is not in the file. */
    fun count(section: BackupSection): Int? = when (section) {
        BackupSection.EXERCISES -> exercises?.size
        BackupSection.PLANS -> plans?.size
        BackupSection.JOURNAL -> climbs?.size
        BackupSection.PLACES -> places?.size
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
    /** Set when the climb is linked to a saved place; with [place] it finds that place again. */
    val placeType: PlaceType? = null,
    val area: String? = null,
    val problem: String? = null,
    val angle: Int? = null,
    val effort: Int? = null,
    /** Local grades: the position in the place's scale and its tape colour; [grade] holds the label. */
    val gradeIndex: Int? = null,
    val gradeColour: Long? = null,
    /** The climb's photo as base64 JPEG. Older backups omit it. */
    val image: String? = null,
)

@Serializable
data class AreaDto(
    val name: String,
    val angle: Int? = null,
    val resetDate: String? = null,
    /** The wall's photo or map as base64 JPEG. Older backups omit it. */
    val image: String? = null,
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
    /** Wall images travel inside the file; without a store they are left out. */
    private val images: AreaImageStore? = null,
) {
    @Inject
    constructor(dbs: CruxDatabases, clock: Clock, images: AreaImageStore) : this({ dbs.current() }, clock, images)

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
            climbs = if (BackupSection.JOURNAL in sections) {
                val places = db.placeDao().getPlaces().associateBy { it.id }
                val areas = db.placeDao().getAllAreas().associateBy { it.id }
                val problems = db.placeDao().getAllProblems().associateBy { it.id }
                val photos = db.climbMediaDao().getAll().filter { it.kind == MediaKind.IMAGE }.associate { it.climbId to it.path }
                db.climbDao().getAll().map { climb ->
                    val place = climb.placeId?.let(places::get)
                    climb.toDto().copy(
                        place = place?.name ?: climb.place,
                        placeType = place?.type,
                        area = climb.areaId?.let(areas::get)?.name,
                        problem = climb.problemId?.let(problems::get)?.name,
                        angle = climb.angle,
                        effort = climb.effort,
                        image = photos[climb.id]?.let { images?.readBytes(it) }?.let { Base64.getEncoder().encodeToString(it) },
                    )
                }
            } else {
                null
            },
            places = if (BackupSection.PLACES in sections) {
                val areas = db.placeDao().getAllAreas().groupBy { it.placeId }
                val problems = db.placeDao().getAllProblems().groupBy { it.placeId }
                db.placeDao().getPlaces().map { place ->
                    val placeAreas = areas[place.id].orEmpty().sortedBy { it.position }
                    val areaNames = placeAreas.associate { it.id to it.name }
                    place.toDto(
                        areas = placeAreas.map { area ->
                            area.toDto().copy(
                                image = area.imagePath?.let { images?.readBytes(it) }?.let { Base64.getEncoder().encodeToString(it) },
                            )
                        },
                        problems = problems[place.id].orEmpty().map { it.toDto(it.areaId?.let(areaNames::get)) },
                    )
                }
            } else {
                null
            },
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

        // Places go in before the journal so imported climbs can link to them.
        val placeDao = db.placeDao()
        if (BackupSection.PLACES in sections) {
            val known = placeDao.getPlaces().associateBy { it.type to it.name.lowercase() }
            file.places?.forEach { dto ->
                if ((dto.type to dto.name.lowercase()) in known) {
                    skipped.merge(BackupSection.PLACES, 1, Int::plus)
                    return@forEach
                }
                val placeId = placeDao.insertPlace(
                    PlaceEntity(
                        name = dto.name.trim(),
                        type = dto.type,
                        location = dto.location,
                        boulderScale = dto.boulderScale,
                        routeScale = dto.routeScale,
                        defaultAngle = dto.defaultAngle,
                        notes = dto.notes,
                        createdAtMillis = now,
                        localScale = dto.localScale?.encode(),
                        favourite = dto.favourite,
                        latitude = dto.latitude,
                        longitude = dto.longitude,
                        address = dto.address,
                    ),
                )
                val areaIds = dto.areas.mapIndexed { position, area ->
                    area.name.lowercase() to placeDao.insertArea(
                        AreaEntity(
                            placeId = placeId,
                            name = area.name,
                            angle = area.angle,
                            resetEpochDay = area.resetDate?.let { java.time.LocalDate.parse(it).toEpochDay() },
                            position = position,
                            imagePath = area.image?.let { encoded ->
                                runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()?.let { images?.importBytes(it) }
                            },
                        ),
                    )
                }.toMap()
                dto.problems.forEach { problem ->
                    val index = if (problem.gradeScale.isLocal) problem.gradeIndex ?: 0 else problem.gradeScale.grades.indexOf(problem.grade)
                    if (index < 0) return@forEach
                    placeDao.insertProblem(
                        ProblemEntity(
                            placeId = placeId,
                            areaId = problem.area?.lowercase()?.let(areaIds::get),
                            name = problem.name,
                            discipline = problem.discipline,
                            gradeScale = problem.gradeScale,
                            gradeIndex = index,
                            tape = problem.tape,
                            setEpochDay = problem.setDate?.let { java.time.LocalDate.parse(it).toEpochDay() },
                            retired = problem.retired,
                            notes = problem.notes,
                            createdAtMillis = now,
                            gradeLabel = problem.grade.takeIf { problem.gradeScale.isLocal },
                            gradeColour = problem.gradeColour,
                        ),
                    )
                }
                added.merge(BackupSection.PLACES, 1, Int::plus)
            }
        }

        if (BackupSection.JOURNAL in sections) {
            // Climbs relink by name to whichever saved places, walls and problems exist now.
            val places = placeDao.getPlaces().associateBy { it.type to it.name.lowercase() }
            val areas = placeDao.getAllAreas().groupBy { it.placeId }
            val problems = placeDao.getAllProblems().groupBy { it.placeId }
            val climbDao = db.climbDao()
            val seen = climbDao.getAll().map { it.toDto().identity() }.toMutableSet()
            file.climbs?.forEach { dto ->
                val index = if (dto.gradeScale.isLocal) dto.gradeIndex ?: 0 else dto.gradeScale.grades.indexOf(dto.grade)
                if (index < 0 || dto.identity() in seen) {
                    skipped.merge(BackupSection.JOURNAL, 1, Int::plus)
                    return@forEach
                }
                val place = dto.placeType?.let { type -> dto.place?.let { places[type to it.lowercase()] } }
                val climbId = climbDao.insert(
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
                        placeId = place?.id,
                        areaId = dto.area?.let { name -> areas[place?.id].orEmpty().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id },
                        problemId = dto.problem?.let { name -> problems[place?.id].orEmpty().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id },
                        angle = dto.angle,
                        effort = dto.effort,
                        gradeLabel = dto.grade.takeIf { dto.gradeScale.isLocal },
                        gradeColour = dto.gradeColour,
                    ),
                )
                dto.image
                    ?.let { encoded -> runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() }
                    ?.let { images?.importBytes(it) }
                    ?.let { path -> db.climbMediaDao().insert(ClimbMediaEntity(climbId = climbId, kind = MediaKind.IMAGE, path = path, createdAtMillis = now)) }
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
    grade = gradeLabel(gradeScale, gradeIndex, gradeLabel),
    style = style,
    attempts = attempts,
    venue = venue,
    date = java.time.LocalDate.ofEpochDay(dateEpochDay).toString(),
    loggedAt = createdAtMillis,
    name = name,
    place = place,
    notes = notes,
    gradeIndex = gradeIndex.takeIf { gradeScale.isLocal },
    gradeColour = gradeColour,
)

/** Two climbs are the same entry when everything but notes and saved-place links matches. */
private fun ClimbDto.identity() = copy(notes = null, placeType = null, area = null, problem = null, angle = null, effort = null, image = null)

private fun PlaceEntity.toDto(areas: List<AreaDto>, problems: List<ProblemDto>) = PlaceDto(
    name = name,
    type = type,
    location = location,
    boulderScale = boulderScale,
    routeScale = routeScale,
    defaultAngle = defaultAngle,
    notes = notes,
    areas = areas,
    problems = problems,
    localScale = LocalScale.decode(localScale),
    favourite = favourite,
    latitude = latitude,
    longitude = longitude,
    address = address,
)

private fun AreaEntity.toDto() = AreaDto(
    name = name,
    angle = angle,
    resetDate = resetEpochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() },
)

private fun ProblemEntity.toDto(area: String?) = ProblemDto(
    name = name,
    discipline = discipline,
    gradeScale = gradeScale,
    grade = gradeLabel(gradeScale, gradeIndex, gradeLabel),
    area = area,
    tape = tape,
    setDate = setEpochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() },
    retired = retired,
    notes = notes,
    gradeIndex = gradeIndex.takeIf { gradeScale.isLocal },
    gradeColour = gradeColour,
)

private fun BodyMeasurementEntity.toDto() = MeasurementDto(
    type = type,
    value = value,
    date = java.time.LocalDate.ofEpochDay(dateEpochDay).toString(),
    loggedAt = createdAtMillis,
)
