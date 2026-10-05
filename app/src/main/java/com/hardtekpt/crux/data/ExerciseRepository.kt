package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.ExerciseDao
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.MetricType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

/** What the exercise form edits. `id == 0` creates a new exercise. */
data class ExerciseInput(
    val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    val metric: MetricType,
    val notes: String?,
)

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun saveExercise(input: ExerciseInput): Long
    /** Plans that use the exercise; deleting it removes it from them. */
    suspend fun planCount(id: Long): Int
    suspend fun deleteExercise(id: Long)
}

class OfflineExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeExercises(): Flow<List<Exercise>> =
        dao.observeAll().map { rows -> rows.map(ExerciseEntity::toModel) }

    override suspend fun getExercise(id: Long): Exercise? = dao.get(id)?.toModel()

    override suspend fun saveExercise(input: ExerciseInput): Long {
        val name = input.name.trim()
        val notes = input.notes?.trim()?.takeIf { it.isNotEmpty() }
        val existing = if (input.id != 0L) dao.get(input.id) else null
        return if (existing != null) {
            dao.update(existing.copy(name = name, category = input.category, metric = input.metric, notes = notes))
            existing.id
        } else {
            dao.insert(
                ExerciseEntity(
                    name = name,
                    category = input.category,
                    metric = input.metric,
                    notes = notes,
                    createdAtMillis = clock.millis(),
                ),
            )
        }
    }

    override suspend fun planCount(id: Long): Int = dao.planCount(id)

    override suspend fun deleteExercise(id: Long) = dao.delete(id)
}

internal fun ExerciseEntity.toModel() = Exercise(
    id = id,
    name = name,
    category = category,
    metric = metric,
    notes = notes,
)
