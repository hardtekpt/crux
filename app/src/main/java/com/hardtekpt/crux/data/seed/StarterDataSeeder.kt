package com.hardtekpt.crux.data.seed

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.ExerciseEntity
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
import com.hardtekpt.crux.data.model.Venue
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills a fresh install. A starter exercise library and session plans go into every build; a few weeks of
 * sample climbs and weigh-ins only go into debug builds so the dashboard looks alive.
 */
@Singleton
class StarterDataSeeder @Inject constructor(
    private val db: CruxDatabase,
    private val clock: Clock,
) {
    /**
     * Checks the database rather than a flag, so the MVP's destructive schema changes
     * repopulate an emptied database on the next launch.
     */
    suspend fun seed(includeSampleData: Boolean) {
        db.withTransaction {
            if (db.exerciseDao().count() == 0 && db.templateDao().count() == 0) insertLibraryAndPlans()
            if (includeSampleData && db.climbDao().count() == 0 && db.bodyMeasurementDao().count() == 0) {
                insertSampleData()
            }
        }
    }

    private suspend fun insertLibraryAndPlans() {
        val now = clock.millis()
        val exerciseIds = STARTER_EXERCISES.associate { exercise ->
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

    private suspend fun insertSampleData() {
        val today = LocalDate.now(clock)
        val now = clock.millis()
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
            } + BodyMeasurementEntity(
                type = MeasurementType.HEIGHT,
                value = 178.0,
                dateEpochDay = today.minusDays(40).toEpochDay(),
                createdAtMillis = now,
            ),
        )
    }
}

private data class StarterExercise(
    val name: String,
    val category: ExerciseCategory,
    val metric: MetricType,
    val notes: String? = null,
)

private val STARTER_EXERCISES = listOf(
    StarterExercise("Half-crimp hang", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, "20 mm edge"),
    StarterExercise("Open-hand hang", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, "20 mm edge"),
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

private data class StarterTemplate(
    val name: String,
    val description: String,
    val blocks: List<Pair<String, List<Pair<String, ExerciseTarget>>>>,
)

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
)

private val SAMPLE_CLIMBS = listOf(
    SampleClimb(26, Discipline.BOULDER, "6B", FLASH, 1, "Green slab", "Block Lab"),
    SampleClimb(26, Discipline.BOULDER, "6C", REDPOINT, 4, "Pinch roof", "Block Lab"),
    SampleClimb(26, Discipline.BOULDER, "7A", ATTEMPT, 6, "Yellow dyno", "Block Lab", "Missing the left foot."),
    SampleClimb(19, Discipline.ROUTE, "6b+", ONSIGHT, 1, "Diedro", "Arco"),
    SampleClimb(19, Discipline.ROUTE, "7a", REDPOINT, 3, "Pilastro", "Arco", "Clipped the chains on the third go."),
    SampleClimb(19, Discipline.ROUTE, "6c", FLASH, 1, null, "Arco"),
    SampleClimb(12, Discipline.BOULDER, "6C+", FLASH, 1, "Blue crimps", "Block Lab"),
    SampleClimb(12, Discipline.BOULDER, "7A", REDPOINT, 5, "Yellow dyno", "Block Lab", "Left foot high, then commit."),
    SampleClimb(5, Discipline.BOULDER, "6B+", FLASH, 1, null, "The Arch"),
    SampleClimb(5, Discipline.BOULDER, "7A+", ATTEMPT, 8, "Overhang project", "The Arch"),
    SampleClimb(2, Discipline.ROUTE, "6c+", ONSIGHT, 1, "Red arete", "The Arch"),
    SampleClimb(2, Discipline.ROUTE, "7a+", ATTEMPT, 2, "Black roof", "The Arch", "Pumped out at the third bolt."),
    SampleClimb(0, Discipline.BOULDER, "6C", FLASH, 1, "Purple sloper", "Block Lab"),
)

/** Days ago to kg. */
private val SAMPLE_WEIGHTS = listOf(
    40 to 73.4, 33 to 73.1, 26 to 72.8, 19 to 72.9, 12 to 72.5, 5 to 72.2, 1 to 72.4,
)
