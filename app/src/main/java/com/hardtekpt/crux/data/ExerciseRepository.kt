package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.IntervalSettings
import com.hardtekpt.crux.data.model.MetricType
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the exercise form edits. `id == 0` creates a new exercise. */
data class ExerciseInput(
    val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    val metric: MetricType,
    val notes: String?,
    /** Kept only for interval metrics. */
    val intervals: IntervalSettings? = null,
)

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun saveExercise(input: ExerciseInput): Long

    /** Plans that use the exercise; deleting it removes it from them. */
    suspend fun planCount(id: Long): Int
    suspend fun deleteExercise(id: Long)
}

class OfflineExerciseRepository @Inject constructor(private val dbs: CruxDatabases, private val clock: Clock) : ExerciseRepository {
    override fun observeExercises(): Flow<List<Exercise>> = dbs.observe { it.exerciseDao().observeAll() }.map { rows -> rows.map(ExerciseEntity::toModel) }

    override suspend fun getExercise(id: Long): Exercise? = dbs.current().exerciseDao().get(id)?.toModel()

    override suspend fun saveExercise(input: ExerciseInput): Long {
        val name = input.name.trim()
        val notes = input.notes?.trim()?.takeIf { it.isNotEmpty() }
        val dao = dbs.current().exerciseDao()
        val existing = if (input.id != 0L) dao.get(input.id) else null
        val intervals = input.intervals?.takeIf { input.metric.usesIntervals }
        return if (existing != null) {
            dao.update(existing.copy(name = name, category = input.category, metric = input.metric, notes = notes).withIntervals(intervals))
            existing.id
        } else {
            dao.insert(
                ExerciseEntity(
                    name = name,
                    category = input.category,
                    metric = input.metric,
                    notes = notes,
                    createdAtMillis = clock.millis(),
                ).withIntervals(intervals),
            )
        }
    }

    override suspend fun planCount(id: Long): Int = dbs.current().exerciseDao().planCount(id)

    override suspend fun deleteExercise(id: Long) = dbs.current().exerciseDao().delete(id)
}

internal fun ExerciseEntity.toModel() = Exercise(
    id = id,
    name = name,
    category = category,
    metric = metric,
    notes = notes,
    intervals = intervals,
)
