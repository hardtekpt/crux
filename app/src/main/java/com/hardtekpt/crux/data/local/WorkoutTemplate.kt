package com.hardtekpt.crux.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.MetricType
import kotlinx.coroutines.flow.Flow

/** An exercise in the climber's library. Plans reference it; it is not copied into them. */
@Entity(tableName = "exercises", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    val metric: MetricType,
    val notes: String? = null,
    val createdAtMillis: Long,
)

/** A session plan. */
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val position: Int,
)

/** A named section of a plan: Warm-up, Max hangs, Limit bouldering. */
@Entity(
    tableName = "template_blocks",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val position: Int,
    val name: String,
)

/** One library exercise placed in a block, with its targets. */
@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TemplateBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("blockId"), Index("exerciseId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val loadKg: Double,
    val restSeconds: Int,
)

data class TemplateExerciseWithExercise(
    @Embedded val item: TemplateExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class BlockWithExercises(
    @Embedded val block: TemplateBlockEntity,
    @Relation(entity = TemplateExerciseEntity::class, parentColumn = "id", entityColumn = "blockId")
    val exercises: List<TemplateExerciseWithExercise>,
)

data class TemplateWithBlocks(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(entity = TemplateBlockEntity::class, parentColumn = "id", entityColumn = "templateId")
    val blocks: List<BlockWithExercises>,
)

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<ExerciseEntity>

    /** How many plans use an exercise; shown before deleting it. */
    @Query(
        """
        SELECT COUNT(DISTINCT b.templateId) FROM template_exercises e
        JOIN template_blocks b ON b.id = e.blockId
        WHERE e.exerciseId = :id
        """,
    )
    suspend fun planCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY position, id")
    fun observeAll(): Flow<List<TemplateWithBlocks>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id")
    fun observe(id: Long): Flow<TemplateWithBlocks?>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun get(id: Long): TemplateWithBlocks?

    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY position, id")
    suspend fun getAll(): List<TemplateWithBlocks>

    @Query("SELECT COUNT(*) FROM workout_templates")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_templates")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM template_blocks WHERE templateId = :templateId")
    suspend fun deleteBlocks(templateId: Long)

    @Insert
    suspend fun insertBlock(block: TemplateBlockEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<TemplateExerciseEntity>)
}
