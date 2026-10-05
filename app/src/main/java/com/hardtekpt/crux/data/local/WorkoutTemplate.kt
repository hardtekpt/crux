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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    /** Rough length, shown on the template card. */
    val estimatedMinutes: Int,
    val position: Int,
)

/** A named section of a template: Warm-up, Max hangs, Limit bouldering. */
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

/** One exercise in a block with its target written as a prescription: `6 × 10 s · 20 mm · +5 kg`. */
@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TemplateBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("blockId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val position: Int,
    val name: String,
    val target: String,
    val rest: String? = null,
)

data class BlockWithExercises(
    @Embedded val block: TemplateBlockEntity,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val exercises: List<TemplateExerciseEntity>,
)

data class TemplateWithBlocks(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(entity = TemplateBlockEntity::class, parentColumn = "id", entityColumn = "templateId")
    val blocks: List<BlockWithExercises>,
)

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY position")
    fun observeAll(): Flow<List<TemplateWithBlocks>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id")
    fun observe(id: Long): Flow<TemplateWithBlocks?>

    @Query("SELECT COUNT(*) FROM workout_templates")
    suspend fun count(): Int

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertBlock(block: TemplateBlockEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<TemplateExerciseEntity>)
}
