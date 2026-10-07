package com.hardtekpt.crux.data.seed

import androidx.room.withTransaction
import com.hardtekpt.crux.data.ExerciseRecordEntity
import com.hardtekpt.crux.data.NoteEntity
import com.hardtekpt.crux.data.local.AreaEntity
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.local.PlaceEntity
import com.hardtekpt.crux.data.local.ProblemEntity
import com.hardtekpt.crux.data.local.SectionEntity
import com.hardtekpt.crux.data.local.TemplateBlockEntity
import com.hardtekpt.crux.data.local.TemplateExerciseEntity
import com.hardtekpt.crux.data.local.WorkoutTemplateEntity
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.AscentStyle.ATTEMPT
import com.hardtekpt.crux.data.model.AscentStyle.FLASH
import com.hardtekpt.crux.data.model.AscentStyle.ONSIGHT
import com.hardtekpt.crux.data.model.AscentStyle.REDPOINT
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Venue
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Demo mode's data set and the optional starter library. The demo database is filled with
 * the starter library, plans and a few weeks of sample climbs; the climber's own database
 * starts empty and only gets starter exercises when they ask for them.
 */
@Singleton
class StarterData @Inject constructor(
    private val dbs: CruxDatabases,
    private val preferences: com.hardtekpt.crux.data.prefs.UserPreferencesRepository,
    clock: Clock,
) {
    private val seeder = StarterDataSeeder(clock)

    /**
     * Fills the demo database, and refills it from scratch when the sample data has changed
     * since it was filled. Only ever touches the demo database, never the climber's own.
     */
    suspend fun seedDemoIfEmpty() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (preferences.demoDataVersion.first() < SAMPLE_DATA_VERSION) {
            dbs.demo.clearAllTables()
            preferences.setDemoDataVersion(SAMPLE_DATA_VERSION)
        }
        seeder.seed(dbs.demo, includeSampleData = true)
    }

    /** Adds the starter exercises and plans to whichever data set is active. */
    suspend fun addStarterLibraryToCurrent(): Int = seeder.addStarterLibrary(dbs.current())
}

/**
 * Bump when the sample data changes, so demo databases filled with an older set are refilled.
 * 2: Block Lab became a gym with a board (6 Oct 2026).
 * 3: places have named sections; Block Lab is "Main gym" plus "Kilter board".
 */
const val SAMPLE_DATA_VERSION = 3

class StarterDataSeeder(private val clock: Clock) {
    /**
     * Fills an empty database: the starter library and plans, plus sample climbs and
     * weigh-ins when [includeSampleData]. Checks contents rather than a flag.
     */
    suspend fun seed(db: CruxDatabase, includeSampleData: Boolean) {
        db.withTransaction {
            if (db.exerciseDao().count() == 0 && db.templateDao().count() == 0) insertLibraryAndPlans(db)
            if (includeSampleData && db.climbDao().count() == 0 && db.bodyMeasurementDao().count() == 0) {
                insertSampleData(db)
            }
            // Added on their own check so demo data sets made before the profile page get them too.
            if (includeSampleData && db.exerciseRecordDao().getAll().isEmpty() && db.noteDao().getAll().isEmpty()) {
                insertProfileSamples(db)
            }
        }
    }

    /** Adds the starter exercises and plans to a database that may already hold the climber's own. */
    suspend fun addStarterLibrary(db: CruxDatabase): Int {
        val existingPlans = db.templateDao().getAll().map { it.template.name.lowercase() }.toSet()
        var added = 0
        db.withTransaction {
            insertLibraryAndPlans(
                db,
                skipPlans = existingPlans,
                onAdded = { added++ },
            )
        }
        return added
    }

    private suspend fun insertLibraryAndPlans(db: CruxDatabase, skipPlans: Set<String> = emptySet(), onAdded: () -> Unit = {}) {
        val now = clock.millis()
        val present = db.exerciseDao().getAll().associate { it.name.lowercase() to it.id }
        val exerciseIds = STARTER_EXERCISES.associate { exercise ->
            // An exercise the climber already has (by name) is reused, not duplicated.
            present[exercise.name.lowercase()]?.let { return@associate exercise.name to it }
            onAdded()
            exercise.name to db.exerciseDao().insert(
                ExerciseEntity(
                    name = exercise.name,
                    category = exercise.category,
                    metric = exercise.metric,
                    notes = exercise.notes,
                    createdAtMillis = now,
                ),
            )
        }
        val dao = db.templateDao()
        STARTER_TEMPLATES.forEachIndexed { templatePosition, template ->
            if (template.name.lowercase() in skipPlans) return@forEachIndexed
            onAdded()
            val templateId = dao.insertTemplate(
                WorkoutTemplateEntity(
                    name = template.name,
                    description = template.description,
                    position = templatePosition,
                ),
            )
            template.blocks.forEachIndexed { blockPosition, (blockName, items) ->
                val blockId = dao.insertBlock(
                    TemplateBlockEntity(templateId = templateId, position = blockPosition, name = blockName),
                )
                dao.insertExercises(
                    items.mapIndexed { position, (exerciseName, target) ->
                        TemplateExerciseEntity(
                            blockId = blockId,
                            exerciseId = exerciseIds.getValue(exerciseName),
                            position = position,
                            sets = target.sets,
                            reps = target.reps,
                            seconds = target.seconds,
                            loadKg = target.loadKg,
                            restSeconds = target.restSeconds,
                        )
                    },
                )
            }
        }
    }

    /** A few exercise results, notes and circumferences so the demo profile isn't empty. */
    private suspend fun insertProfileSamples(db: CruxDatabase) {
        val today = LocalDate.now(clock)
        val now = clock.millis()
        val exercises = db.exerciseDao().getAll().associate { it.name to it.id }
        SAMPLE_RECORDS.forEachIndexed { index, record ->
            val exerciseId = exercises[record.exercise] ?: return@forEachIndexed
            db.exerciseRecordDao().insert(
                ExerciseRecordEntity(
                    exerciseId = exerciseId,
                    dateEpochDay = today.minusDays(record.daysAgo.toLong()).toEpochDay(),
                    reps = record.reps,
                    seconds = record.seconds,
                    loadKg = record.loadKg,
                    createdAtMillis = now + index,
                ),
            )
        }
        SAMPLE_NOTES.forEachIndexed { index, (daysAgo, text) ->
            val at = now - daysAgo * 86_400_000L + index
            db.noteDao().insert(NoteEntity(text = text, createdAtMillis = at, updatedAtMillis = at, pinned = index == 0, tag = SAMPLE_NOTE_TAGS[index]))
        }
        val girths = listOf(
            MeasurementType.FOREARM to 29.5,
            MeasurementType.FOREARM_RIGHT to 30.5,
            MeasurementType.BICEP_RIGHT to 33.5,
            MeasurementType.BICEP to 33.0,
            MeasurementType.CHEST to 98.0,
            MeasurementType.WAIST to 78.0,
        )
        val measured = db.bodyMeasurementDao().getAll().map { it.type }.toSet()
        db.bodyMeasurementDao().insertAll(
            girths.filter { it.first !in measured }.map { (type, cm) ->
                BodyMeasurementEntity(type = type, value = cm, dateEpochDay = today.minusDays(12).toEpochDay(), createdAtMillis = now)
            },
        )
    }

    private suspend fun insertSampleData(db: CruxDatabase) {
        val today = LocalDate.now(clock)
        val now = clock.millis()
        // Saved places with their walls; every named sample climb is a problem on one of them.
        val placeDao = db.placeDao()
        val placeIds = SAMPLE_PLACES.associate { sample ->
            sample.name to placeDao.insertPlace(
                PlaceEntity(
                    name = sample.name,
                    type = sample.type,
                    location = sample.location,
                    createdAtMillis = now,
                    extraTypes = if (sample.boardAreas.isNotEmpty()) PlaceType.BOARD.name else "",
                    defaultAngle = 40.takeIf { sample.boardAreas.isNotEmpty() },
                ),
            )
        }
        // Each place gets its main section; a place with a board gets a board section too.
        val mainSections = SAMPLE_PLACES.associate { sample ->
            sample.name to placeDao.insertSection(
                SectionEntity(placeId = placeIds.getValue(sample.name), type = sample.type, name = sample.sectionName ?: sample.type.label, position = 0),
            )
        }
        val areaIds = SAMPLE_PLACES.flatMap { sample ->
            sample.areas.mapIndexed { position, area ->
                (sample.name to area) to placeDao.insertArea(
                    AreaEntity(placeId = placeIds.getValue(sample.name), name = area, position = position, sectionId = mainSections.getValue(sample.name)),
                )
            }
        }.toMap()
        SAMPLE_PLACES.filter { it.boardAreas.isNotEmpty() }.forEach { sample ->
            val board = placeDao.insertSection(
                SectionEntity(placeId = placeIds.getValue(sample.name), type = PlaceType.BOARD, name = sample.boardName, position = 1),
            )
            sample.boardAreas.forEachIndexed { index, area ->
                placeDao.insertArea(
                    AreaEntity(
                        placeId = placeIds.getValue(sample.name),
                        name = area,
                        position = sample.areas.size + index,
                        type = PlaceType.BOARD,
                        angle = 40,
                        sectionId = board,
                    ),
                )
            }
        }
        val problemIds = mutableMapOf<Pair<String, String>, Long>()
        SAMPLE_CLIMBS.filter { it.name != null }.forEach { climb ->
            val key = climb.place to climb.name!!
            if (key in problemIds) return@forEach
            problemIds[key] = placeDao.insertProblem(
                ProblemEntity(
                    placeId = placeIds.getValue(climb.place),
                    areaId = climb.area?.let { areaIds[climb.place to it] },
                    name = climb.name,
                    discipline = climb.discipline,
                    gradeScale = climb.discipline.defaultScale,
                    gradeIndex = climb.discipline.defaultScale.grades.indexOf(climb.grade),
                    tape = TAPES.indexOfFirst { climb.name.startsWith(it) }.takeIf { it >= 0 },
                    setEpochDay = today.minusDays(30).toEpochDay(),
                    createdAtMillis = now,
                ),
            )
        }
        db.climbDao().insertAll(
            SAMPLE_CLIMBS.mapIndexed { index, climb ->
                ClimbEntity(
                    discipline = climb.discipline,
                    gradeScale = climb.discipline.defaultScale,
                    gradeIndex = climb.discipline.defaultScale.grades.indexOf(climb.grade),
                    style = climb.style,
                    attempts = climb.attempts,
                    // Arco is the outdoor crag in the sample; the rest are gyms.
                    venue = if (climb.place == "Arco") Venue.CRAG else Venue.GYM,
                    dateEpochDay = today.minusDays(climb.daysAgo.toLong()).toEpochDay(),
                    createdAtMillis = now - (SAMPLE_CLIMBS.size - index) * 60_000L,
                    name = climb.name,
                    place = climb.place,
                    notes = climb.notes,
                    placeId = placeIds[climb.place],
                    areaId = climb.area?.let { areaIds[climb.place to it] },
                    problemId = climb.name?.let { problemIds[climb.place to it] },
                )
            },
        )
        db.bodyMeasurementDao().insertAll(
            SAMPLE_WEIGHTS.map { (daysAgo, kg) ->
                BodyMeasurementEntity(
                    type = MeasurementType.WEIGHT,
                    value = kg,
                    dateEpochDay = today.minusDays(daysAgo.toLong()).toEpochDay(),
                    createdAtMillis = now,
                )
            } + listOf(
                MeasurementType.HEIGHT to 178.0,
                MeasurementType.WINGSPAN to 184.0,
                MeasurementType.STANDING_REACH to 231.0,
                MeasurementType.BODY_FAT to 12.5,
            ).map { (type, value) ->
                BodyMeasurementEntity(
                    type = type,
                    value = value,
                    dateEpochDay = today.minusDays(40).toEpochDay(),
                    createdAtMillis = now,
                )
            },
        )
    }
}

private data class SampleRecord(val exercise: String, val daysAgo: Int, val reps: Int? = null, val seconds: Int? = null, val loadKg: Double? = null)

private val SAMPLE_RECORDS = listOf(
    SampleRecord("Half-crimp hang", 30, seconds = 10, loadKg = 12.5),
    SampleRecord("Half-crimp hang", 16, seconds = 10, loadKg = 15.0),
    SampleRecord("Half-crimp hang", 3, seconds = 10, loadKg = 17.5),
    SampleRecord("Weighted pull-ups", 21, reps = 5, loadKg = 20.0),
    SampleRecord("Weighted pull-ups", 6, reps = 5, loadKg = 22.5),
    SampleRecord("Front lever tucks", 9, seconds = 14),
)

private val SAMPLE_NOTE_TAGS = listOf("injury", "beta", "training")

private val SAMPLE_NOTES = listOf(
    2 to "Left ring finger a bit tender after the crimpy session. Open-hand only this week.",
    5 to "Blue at the cave: heel hook on the arete, then match the sloper before going left.",
    11 to "Felt strong on the board. Warm-up on the 4x4 circuit worked well.",
)

private data class StarterExercise(val name: String, val category: ExerciseCategory, val metric: MetricType, val notes: String? = null)

private val STARTER_EXERCISES = listOf(
    StarterExercise("Half-crimp hang", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, "20 mm edge"),
    StarterExercise("Open-hand hang", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, "20 mm edge"),
    StarterExercise("Repeaters", ExerciseCategory.FINGERS, MetricType.WEIGHTED_INTERVALS, "20 mm edge, half crimp"),
    StarterExercise("Tabata core", ExerciseCategory.CORE, MetricType.INTERVALS, "Hollow holds or mountain climbers"),
    StarterExercise("Scap pull-ups", ExerciseCategory.PULLING, MetricType.REPS),
    StarterExercise("Weighted pull-ups", ExerciseCategory.PULLING, MetricType.WEIGHTED_REPS),
    StarterExercise("Front lever tucks", ExerciseCategory.CORE, MetricType.TIME),
    StarterExercise("Hanging leg raises", ExerciseCategory.CORE, MetricType.REPS),
    StarterExercise("Easy traversing", ExerciseCategory.CLIMBING, MetricType.TIME),
    StarterExercise("Limit boulders", ExerciseCategory.CLIMBING, MetricType.REPS, "Count attempts as reps"),
    StarterExercise("4×4 circuit", ExerciseCategory.CLIMBING, MetricType.REPS, "Four problems back to back"),
    StarterExercise("Push-ups", ExerciseCategory.ANTAGONIST, MetricType.REPS),
    StarterExercise("Dips", ExerciseCategory.ANTAGONIST, MetricType.WEIGHTED_REPS),
    StarterExercise("Reverse wrist curls", ExerciseCategory.ANTAGONIST, MetricType.WEIGHTED_REPS),
    StarterExercise("Forearm stretch", ExerciseCategory.MOBILITY, MetricType.TIME),
)

private data class StarterTemplate(val name: String, val description: String, val blocks: List<Pair<String, List<Pair<String, ExerciseTarget>>>>)

private val STARTER_TEMPLATES = listOf(
    StarterTemplate(
        name = "Max hangs + limit bouldering",
        description = "Finger strength first, then your hardest moves while you are fresh.",
        blocks = listOf(
            "Warm-up" to listOf(
                "Easy traversing" to ExerciseTarget(sets = 1, seconds = 600, restSeconds = 0),
                "Scap pull-ups" to ExerciseTarget(sets = 2, reps = 8, restSeconds = 60),
            ),
            "Max hangs" to listOf(
                "Half-crimp hang" to ExerciseTarget(sets = 6, seconds = 10, loadKg = 5.0, restSeconds = 180),
            ),
            "Limit bouldering" to listOf(
                "Limit boulders" to ExerciseTarget(sets = 4, reps = 4, restSeconds = 180),
            ),
        ),
    ),
    StarterTemplate(
        name = "Power endurance 4×4",
        description = "Four problems back to back, four rounds. Pick grades you flash on a good day.",
        blocks = listOf(
            "Warm-up" to listOf(
                "Easy traversing" to ExerciseTarget(sets = 1, seconds = 600, restSeconds = 0),
            ),
            "4×4" to listOf(
                "4×4 circuit" to ExerciseTarget(sets = 4, reps = 4, restSeconds = 240),
            ),
            "Cool-down" to listOf(
                "Push-ups" to ExerciseTarget(sets = 3, reps = 12, restSeconds = 60),
                "Forearm stretch" to ExerciseTarget(sets = 2, seconds = 60, restSeconds = 0),
            ),
        ),
    ),
    StarterTemplate(
        name = "Strength day",
        description = "Pulling, core and antagonists for the days off the wall.",
        blocks = listOf(
            "Pulling" to listOf(
                "Weighted pull-ups" to ExerciseTarget(sets = 5, reps = 5, loadKg = 10.0, restSeconds = 180),
                "Front lever tucks" to ExerciseTarget(sets = 4, seconds = 8, restSeconds = 120),
            ),
            "Core" to listOf(
                "Hanging leg raises" to ExerciseTarget(sets = 3, reps = 10, restSeconds = 90),
            ),
            "Antagonists" to listOf(
                "Dips" to ExerciseTarget(sets = 3, reps = 8, restSeconds = 120),
                "Reverse wrist curls" to ExerciseTarget(sets = 3, reps = 15, loadKg = 5.0, restSeconds = 60),
            ),
        ),
    ),
)

private data class SampleClimb(
    val daysAgo: Int,
    val discipline: Discipline,
    val grade: String,
    val style: AscentStyle,
    val attempts: Int,
    val name: String?,
    val place: String,
    val notes: String? = null,
    val area: String? = null,
)

private data class SamplePlace(
    val name: String,
    val type: PlaceType,
    val location: String,
    val areas: List<String>,
    /** Sets on a board inside the place, which makes it a gym with a board. */
    val boardAreas: List<String> = emptyList(),
    val sectionName: String? = null,
    val boardName: String = "Board",
)

private val SAMPLE_PLACES = listOf(
    SamplePlace(
        "Block Lab",
        PlaceType.GYM,
        "Lisbon",
        listOf("Cave", "Slab", "Comp wall"),
        boardAreas = listOf("Benchmarks", "Circuits"),
        sectionName = "Main gym",
        boardName = "Kilter board",
    ),
    SamplePlace("Arco", PlaceType.CRAG, "Trentino", listOf("Policromuro", "Massi di Prabi")),
    SamplePlace("The Arch", PlaceType.GYM, "London", listOf("Overhang", "Lead wall")),
)

/** Matches the tape colours the problem editor offers, in order. */
private val TAPES = listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple")

private val SAMPLE_CLIMBS = listOf(
    SampleClimb(26, Discipline.BOULDER, "6B", FLASH, 1, "Green slab", "Block Lab", area = "Slab"),
    SampleClimb(26, Discipline.BOULDER, "6C", REDPOINT, 4, "Pinch roof", "Block Lab", area = "Cave"),
    SampleClimb(26, Discipline.BOULDER, "7A", ATTEMPT, 6, "Yellow dyno", "Block Lab", "Missing the left foot.", area = "Comp wall"),
    SampleClimb(19, Discipline.ROUTE, "6b+", ONSIGHT, 1, "Diedro", "Arco", area = "Policromuro"),
    SampleClimb(19, Discipline.ROUTE, "7a", REDPOINT, 3, "Pilastro", "Arco", "Clipped the chains on the third go.", area = "Policromuro"),
    SampleClimb(19, Discipline.ROUTE, "6c", FLASH, 1, null, "Arco"),
    SampleClimb(12, Discipline.BOULDER, "6C+", FLASH, 1, "Blue crimps", "Block Lab", area = "Cave"),
    SampleClimb(12, Discipline.BOULDER, "7A", REDPOINT, 5, "Yellow dyno", "Block Lab", "Left foot high, then commit.", area = "Comp wall"),
    SampleClimb(5, Discipline.BOULDER, "6B+", FLASH, 1, null, "The Arch"),
    SampleClimb(5, Discipline.BOULDER, "7A+", ATTEMPT, 8, "Overhang project", "The Arch", area = "Overhang"),
    SampleClimb(2, Discipline.ROUTE, "6c+", ONSIGHT, 1, "Red arete", "The Arch", area = "Lead wall"),
    SampleClimb(2, Discipline.ROUTE, "7a+", ATTEMPT, 2, "Black roof", "The Arch", "Pumped out at the third bolt.", area = "Lead wall"),
    SampleClimb(0, Discipline.BOULDER, "6C", FLASH, 1, "Purple sloper", "Block Lab", area = "Slab"),
)

/** Days ago to kg. */
private val SAMPLE_WEIGHTS = listOf(
    40 to 73.4,
    33 to 73.1,
    26 to 72.8,
    19 to 72.9,
    12 to 72.5,
    5 to 72.2,
    1 to 72.4,
)
