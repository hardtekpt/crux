package com.hardtekpt.crux.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.PlaceType
import kotlinx.coroutines.flow.Flow

/** A gym, crag or board the climber logs at. */
@Entity(tableName = "places", indices = [Index("name")])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaceType,
    /** City, region or "home" — whatever helps tell places apart. */
    val location: String? = null,
    /** The scales this place grades in; null means use the climber's settings. */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    /** Boards: the angle the board is usually set to. */
    val defaultAngle: Int? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
    /** The place's own grades as JSON ([com.hardtekpt.crux.data.model.LocalScale]); schema 9. */
    val localScale: String? = null,
)

/** A wall or sector inside a place; for a board, a named angle or set. */
@Entity(
    tableName = "areas",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("placeId")],
)
data class AreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val name: String,
    val angle: Int? = null,
    /** Gyms: the day the wall was last reset. */
    val resetEpochDay: Long? = null,
    val position: Int = 0,
    /** File name of an attached photo or map in app storage (schema 8). */
    val imagePath: String? = null,
)

/** A problem or route at a place, optionally on one of its areas. */
@Entity(
    tableName = "problems",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AreaEntity::class, parentColumns = ["id"], childColumns = ["areaId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("placeId"), Index("areaId")],
)
data class ProblemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val areaId: Long? = null,
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    /** Index into the design system's tape colours (red … purple), if the climber tagged one. */
    val tape: Int? = null,
    val setEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "0") val retired: Boolean = false,
    val notes: String? = null,
    val createdAtMillis: Long,
    /** For local grades: the label and tape colour when it was set (schema 9). */
    val gradeLabel: String? = null,
    val gradeColour: Long? = null,
)

/** How the climber has done on one problem, summed over every logged go. */
data class ProblemStatsRow(
    val problemId: Long,
    /** Distinct days with a logged go. */
    val sessions: Int,
    val attempts: Int,
    /** Day of the first send, if any. */
    val firstSendEpochDay: Long?,
    val lastEpochDay: Long,
)

/** Activity at a place, for the places list. */
data class PlaceActivityRow(
    val placeId: Long,
    val climbs: Int,
    val lastEpochDay: Long?,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    fun observePlace(id: Long): Flow<PlaceEntity?>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlace(id: Long): PlaceEntity?

    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    suspend fun getPlaces(): List<PlaceEntity>

    @Query(
        """
        SELECT placeId, COUNT(*) AS climbs, MAX(dateEpochDay) AS lastEpochDay
        FROM climbs WHERE placeId IS NOT NULL GROUP BY placeId
        """,
    )
    fun observeActivity(): Flow<List<PlaceActivityRow>>

    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    /** Climbs keep their typed place name; only the link goes. */
    @Query("UPDATE climbs SET placeId = NULL, areaId = NULL, problemId = NULL WHERE placeId = :id")
    suspend fun unlinkClimbsFromPlace(id: Long)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Query("SELECT * FROM areas WHERE placeId = :placeId ORDER BY position, name COLLATE NOCASE")
    fun observeAreas(placeId: Long): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas ORDER BY placeId, position")
    suspend fun getAllAreas(): List<AreaEntity>

    @Query("SELECT * FROM areas ORDER BY placeId, position")
    fun observeAllAreas(): Flow<List<AreaEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM areas WHERE placeId = :placeId")
    suspend fun nextAreaPosition(placeId: Long): Int

    @Insert
    suspend fun insertArea(area: AreaEntity): Long

    @Update
    suspend fun updateArea(area: AreaEntity)

    @Query("UPDATE climbs SET areaId = NULL WHERE areaId = :id")
    suspend fun unlinkClimbsFromArea(id: Long)

    @Query("DELETE FROM areas WHERE id = :id")
    suspend fun deleteArea(id: Long)

    /** A reset retires everything still up on the wall. */
    @Query("UPDATE problems SET retired = 1 WHERE areaId = :areaId AND retired = 0")
    suspend fun retireProblemsOnArea(areaId: Long)

    @Query("SELECT * FROM problems WHERE placeId = :placeId ORDER BY retired, gradeIndex DESC, name COLLATE NOCASE")
    fun observeProblems(placeId: Long): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM problems ORDER BY placeId")
    suspend fun getAllProblems(): List<ProblemEntity>

    @Query("SELECT * FROM problems ORDER BY placeId")
    fun observeAllProblems(): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM problems WHERE id = :id")
    fun observeProblem(id: Long): Flow<ProblemEntity?>

    @Query("SELECT * FROM problems WHERE id = :id")
    suspend fun getProblem(id: Long): ProblemEntity?

    @Insert
    suspend fun insertProblem(problem: ProblemEntity): Long

    @Update
    suspend fun updateProblem(problem: ProblemEntity)

    @Query("UPDATE climbs SET problemId = NULL WHERE problemId = :id")
    suspend fun unlinkClimbsFromProblem(id: Long)

    @Query("DELETE FROM problems WHERE id = :id")
    suspend fun deleteProblem(id: Long)

    @Query(
        """
        SELECT problemId,
               COUNT(DISTINCT dateEpochDay) AS sessions,
               SUM(attempts) AS attempts,
               MIN(CASE WHEN style != 'ATTEMPT' THEN dateEpochDay END) AS firstSendEpochDay,
               MAX(dateEpochDay) AS lastEpochDay
        FROM climbs WHERE problemId IS NOT NULL GROUP BY problemId
        """,
    )
    fun observeProblemStats(): Flow<List<ProblemStatsRow>>
}
