package com.hardtekpt.crux.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.MetricType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * A result on an exercise, logged by hand until the session logger records them (schema 13).
 * Which fields matter follows the exercise's metric: reps, a hold time, added load.
 */
@Entity(
    tableName = "exercise_records",
    foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId")],
)
data class ExerciseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val dateEpochDay: Long,
    val reps: Int? = null,
    val seconds: Int? = null,
    val loadKg: Double? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
)

@Dao
interface ExerciseRecordDao {
    @Query("SELECT * FROM exercise_records ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<ExerciseRecordEntity>>

    @Query("SELECT * FROM exercise_records ORDER BY dateEpochDay, createdAtMillis")
    suspend fun getAll(): List<ExerciseRecordEntity>

    @Insert
    suspend fun insert(record: ExerciseRecordEntity): Long

    @Query("DELETE FROM exercise_records WHERE id = :id")
    suspend fun delete(id: Long)
}

data class ExerciseRecord(
    val id: Long,
    val exerciseId: Long,
    val date: LocalDate,
    val reps: Int?,
    val seconds: Int?,
    val loadKg: Double?,
    val notes: String?,
)

/** One logged result with its exercise, and whether it is that exercise's PR. */
data class LoggedResult(val exercise: Exercise, val record: ExerciseRecord, val isBest: Boolean)

/** An exercise's personal record, and how many results it was chosen from. */
data class ExerciseBest(val exercise: Exercise, val best: ExerciseRecord, val results: Int)

/**
 * What "better" means for a metric: more load first where load counts, then more reps or a
 * longer hold. Interval work without load is scored on repeats.
 */
fun MetricType.score(record: ExerciseRecord): List<Double> = when {
    usesLoad -> listOf(record.loadKg ?: 0.0, (record.reps ?: 0).toDouble(), (record.seconds ?: 0).toDouble())
    usesTime -> listOf((record.seconds ?: 0).toDouble())
    else -> listOf((record.reps ?: 0).toDouble())
}

fun MetricType.best(records: List<ExerciseRecord>): ExerciseRecord? =
    records.maxWithOrNull { a, b ->
        val sa = score(a)
        val sb = score(b)
        sa.indices.map { sa[it].compareTo(sb[it]) }.firstOrNull { it != 0 } ?: b.date.compareTo(a.date)
    }

interface RecordRepository {
    /** Every exercise that has a result, with its best; most recently set PRs first. */
    fun observeBests(): Flow<List<ExerciseBest>>
    /** Every result, newest first, for the journal. */
    fun observeResults(): Flow<List<LoggedResult>>
    /** One exercise's results, newest first. */
    fun observeRecords(exerciseId: Long): Flow<List<ExerciseRecord>>
    suspend fun addRecord(exerciseId: Long, date: LocalDate, reps: Int?, seconds: Int?, loadKg: Double?, notes: String?): Long
    suspend fun deleteRecord(id: Long)
}

class OfflineRecordRepository @Inject constructor(
    private val dbs: CruxDatabases,
    private val clock: Clock,
) : RecordRepository {
    override fun observeBests(): Flow<List<ExerciseBest>> = dbs.observe { db ->
        combine(db.exerciseDao().observeAll(), db.exerciseRecordDao().observeAll()) { exercises, records ->
            val byExercise = records.map { it.toModel() }.groupBy { it.exerciseId }
            exercises.mapNotNull { entity ->
                val mine = byExercise[entity.id] ?: return@mapNotNull null
                val exercise = Exercise(entity.id, entity.name, entity.category, entity.metric, entity.notes)
                exercise.metric.best(mine)?.let { ExerciseBest(exercise, it, mine.size) }
            }.sortedByDescending { it.best.date }
        }
    }

    override fun observeResults(): Flow<List<LoggedResult>> = dbs.observe { db ->
        combine(db.exerciseDao().observeAll(), db.exerciseRecordDao().observeAll()) { exercises, records ->
            val byId = exercises.associateBy { it.id }
            val models = records.map { it.toModel() }
            val bests = models.groupBy { it.exerciseId }.mapNotNull { (id, list) -> byId[id]?.metric?.best(list)?.id }.toSet()
            models.mapNotNull { record ->
                val entity = byId[record.exerciseId] ?: return@mapNotNull null
                LoggedResult(Exercise(entity.id, entity.name, entity.category, entity.metric, entity.notes), record, record.id in bests)
            }.sortedByDescending { it.record.date }
        }
    }

    override fun observeRecords(exerciseId: Long): Flow<List<ExerciseRecord>> =
        dbs.observe { it.exerciseRecordDao().observeAll() }.map { list -> list.filter { it.exerciseId == exerciseId }.map { it.toModel() } }

    override suspend fun addRecord(exerciseId: Long, date: LocalDate, reps: Int?, seconds: Int?, loadKg: Double?, notes: String?): Long =
        dbs.current().exerciseRecordDao().insert(
            ExerciseRecordEntity(
                exerciseId = exerciseId,
                dateEpochDay = date.toEpochDay(),
                reps = reps,
                seconds = seconds,
                loadKg = loadKg,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = clock.millis(),
            ),
        )

    override suspend fun deleteRecord(id: Long) = dbs.current().exerciseRecordDao().delete(id)
}

internal fun ExerciseRecordEntity.toModel() = ExerciseRecord(id, exerciseId, LocalDate.ofEpochDay(dateEpochDay), reps, seconds, loadKg, notes)
