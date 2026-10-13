package com.hardtekpt.crux.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** What a day of the week holds: a session plan, or a day of climbing. A day with nothing is rest. */
enum class ScheduleKind { PLAN, CLIMB }

/**
 * One thing planned on a day of the week (schema 25). The week repeats. A day can hold several,
 * in [position] order. Deleting a plan takes it off the week; deleting a place keeps the
 * climbing day without one.
 */
@Entity(
    tableName = "schedule_slots",
    foreignKeys = [
        ForeignKey(entity = WorkoutTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("templateId"), Index("placeId"), Index("dayOfWeek")],
)
data class ScheduleSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO day of the week: 1 is Monday, 7 Sunday. */
    val dayOfWeek: Int,
    val position: Int,
    val kind: ScheduleKind,
    val templateId: Long? = null,
    /** A climbing day's place, if it has one. */
    val placeId: Long? = null,
)

/** A slot with the names it shows. */
data class ScheduleSlotRow(
    val id: Long,
    val dayOfWeek: Int,
    val position: Int,
    val kind: ScheduleKind,
    val templateId: Long?,
    val placeId: Long?,
    val planName: String?,
    val placeName: String?,
)

@Dao
interface ScheduleDao {
    @Query(
        """
        SELECT s.id, s.dayOfWeek, s.position, s.kind, s.templateId, s.placeId, t.name AS planName, p.name AS placeName
        FROM schedule_slots s
        LEFT JOIN workout_templates t ON t.id = s.templateId
        LEFT JOIN places p ON p.id = s.placeId
        ORDER BY s.dayOfWeek, s.position, s.id
        """,
    )
    fun observeAll(): Flow<List<ScheduleSlotRow>>

    @Query("SELECT * FROM schedule_slots ORDER BY dayOfWeek, position, id")
    suspend fun getAll(): List<ScheduleSlotEntity>

    @Query("DELETE FROM schedule_slots WHERE dayOfWeek = :day")
    suspend fun clearDay(day: Int)

    @Insert
    suspend fun insert(slots: List<ScheduleSlotEntity>)

    @Query("SELECT COUNT(*) FROM schedule_slots WHERE templateId = :templateId")
    suspend fun countForPlan(templateId: Long): Int
}
