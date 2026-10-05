package com.hardtekpt.crux.data.seed

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.TemplateBlockEntity
import com.hardtekpt.crux.data.local.TemplateExerciseEntity
import com.hardtekpt.crux.data.local.WorkoutTemplateEntity
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.AscentStyle.ATTEMPT
import com.hardtekpt.crux.data.model.AscentStyle.FLASH
import com.hardtekpt.crux.data.model.AscentStyle.ONSIGHT
import com.hardtekpt.crux.data.model.AscentStyle.REDPOINT
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.Venue
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills a fresh install. Starter workout templates go into every build; a few weeks of
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
            if (db.templateDao().count() == 0) insertTemplates()
            if (includeSampleData && db.climbDao().count() == 0 && db.bodyMeasurementDao().count() == 0) {
                insertSampleData()
            }
        }
    }

    private suspend fun insertTemplates() {
        val dao = db.templateDao()
        STARTER_TEMPLATES.forEachIndexed { templatePosition, template ->
            val templateId = dao.insertTemplate(
                WorkoutTemplateEntity(
                    name = template.name,
                    description = template.description,
                    estimatedMinutes = template.minutes,
                    position = templatePosition,
                ),
            )
            template.blocks.forEachIndexed { blockPosition, (blockName, exercises) ->
                val blockId = dao.insertBlock(
                    TemplateBlockEntity(templateId = templateId, position = blockPosition, name = blockName),
                )
                dao.insertExercises(
                    exercises.mapIndexed { position, exercise ->
                        TemplateExerciseEntity(
                            blockId = blockId,
                            position = position,
                            name = exercise.name,
                            target = exercise.target,
                            rest = exercise.rest,
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

private data class StarterExercise(val name: String, val target: String, val rest: String? = null)
private data class StarterTemplate(
    val name: String,
    val description: String,
    val minutes: Int,
    val blocks: List<Pair<String, List<StarterExercise>>>,
)

private val STARTER_TEMPLATES = listOf(
    StarterTemplate(
        name = "Max hangs + limit bouldering",
        description = "Finger strength first, then your hardest moves while you are fresh.",
        minutes = 75,
        blocks = listOf(
            "Warm-up" to listOf(
                StarterExercise("Easy traversing", "10 min · easy terrain"),
                StarterExercise("Scap pull-ups", "2 × 8 · bodyweight", "1 min"),
            ),
            "Max hangs" to listOf(
                StarterExercise("Half-crimp hang", "6 × 10 s · 20 mm · +5 kg", "3 min"),
            ),
            "Limit bouldering" to listOf(
                StarterExercise("Limit problems", "4 problems · 4 attempts each", "3 min"),
            ),
        ),
    ),
    StarterTemplate(
        name = "Power endurance 4×4",
        description = "Four problems back to back, four rounds. Pick grades you flash on a good day.",
        minutes = 60,
        blocks = listOf(
            "Warm-up" to listOf(
                StarterExercise("Easy boulders", "8 problems · 2 grades below max"),
            ),
            "4×4" to listOf(
                StarterExercise("Boulder circuit", "4 problems × 4 rounds · 6A–6B", "4 min"),
            ),
            "Cool-down" to listOf(
                StarterExercise("Push-ups", "3 × 12", "1 min"),
                StarterExercise("Forearm stretch", "2 min each side"),
            ),
        ),
    ),
    StarterTemplate(
        name = "Strength day",
        description = "Pulling, core and antagonists for the days off the wall.",
        minutes = 50,
        blocks = listOf(
            "Pulling" to listOf(
                StarterExercise("Weighted pull-ups", "5 × 5 · +10 kg", "3 min"),
                StarterExercise("Front lever tucks", "4 × 8 s", "2 min"),
            ),
            "Core" to listOf(
                StarterExercise("Hanging leg raises", "3 × 10", "90 s"),
            ),
            "Antagonists" to listOf(
                StarterExercise("Dips", "3 × 8", "2 min"),
                StarterExercise("Reverse wrist curls", "3 × 15 · 5 kg", "1 min"),
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
