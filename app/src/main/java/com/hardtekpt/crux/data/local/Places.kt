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

/**
 * A physical place the climber logs at. It can hold several kinds of climbing (a gym with a
 * board, a crag with a bouldering area): [type] is the main one, [extraTypes] the others.
 */
@Entity(tableName = "places", indices = [Index("name")])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaceType,
    /** City, region or "home" — whatever helps tell places apart. */
    val location: String? = null,
    /** Before schema 20 a place graded as a whole; now each section does, and these stay empty. */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    /** Boards: the angle the board is usually set to. */
    val defaultAngle: Int? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
    /** The place's own grades as JSON ([com.hardtekpt.crux.data.model.LocalScale]); schema 9. */
    val localScale: String? = null,
    /** Shown as a quick pick on Log climb (schema 11). */
    @ColumnInfo(defaultValue = "0") val favourite: Boolean = false,
    /** Where it is on the map, and the address found for it (schema 12). Optional. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /** Other kinds of climbing here besides [type], comma-separated names (schema 15). */
    @ColumnInfo(defaultValue = "") val extraTypes: String = "",
) {
    companion object
}

/**
 * One part of a place, with its own kind and name: "Main gym", "Spray wall", "Moonboard".
 * A place has one or more; kinds can repeat (two boards). Schema 16.
 */
@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("placeId")],
)
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val type: PlaceType,
    val name: String,
    val position: Int = 0,
    /** The scales climbs here are graded in; null uses the climber's settings (schema 20). */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    /** This section's own grades as JSON ([com.hardtekpt.crux.data.model.LocalScale]). */
    val localScale: String? = null,
)

/** A wall or sector inside a place; for a board, a named angle or set. */
@Entity(
    tableName = "areas",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("placeId"), Index("sectionId")],
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
    /** Schema 15's kind for the area; replaced by [sectionId] in schema 16 and no longer used. */
    val type: PlaceType? = null,
    /** The section the area is in (schema 16); null means the place's first section. */
    val sectionId: Long? = null,
)

/**
 * A climb: the boulder or route you try, with its name and grade, and where it is if anywhere
 * (a place, one of its facilities, a wall). Its logs are the rows in `climbs` that point here.
 * The table keeps its old name, `problems`; since schema 21 a climb needs no place, and deleting
 * the place keeps the climb.
 */
@Entity(
    tableName = "problems",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AreaEntity::class, parentColumns = ["id"], childColumns = ["areaId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("placeId"), Index("areaId"), Index("sectionId")],
)
data class ProblemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long? = null,
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
    /** The facility it's in (schema 21); the wall's when it has one. */
    val sectionId: Long? = null,
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
data class PlaceActivityRow(val placeId: Long, val climbs: Int, val lastEpochDay: Long?)

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

    /** Logs keep their typed place name and their climb; only the link to the place goes. */
    @Query("UPDATE climbs SET placeId = NULL, areaId = NULL, sectionId = NULL WHERE placeId = :id")
    suspend fun unlinkClimbsFromPlace(id: Long)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Query("SELECT * FROM sections WHERE placeId = :placeId ORDER BY position, id")
    fun observeSections(placeId: Long): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE placeId = :placeId ORDER BY position, id")
    suspend fun getSections(placeId: Long): List<SectionEntity>

    @Query("SELECT * FROM sections ORDER BY placeId, position, id")
    fun observeAllSections(): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections ORDER BY placeId, position, id")
    suspend fun getAllSections(): List<SectionEntity>

    @Insert
    suspend fun insertSection(section: SectionEntity): Long

    @Update
    suspend fun updateSection(section: SectionEntity)

    @Query("DELETE FROM sections WHERE id = :id")
    suspend fun deleteSection(id: Long)

    @Query("UPDATE climbs SET sectionId = NULL WHERE sectionId = :id")
    suspend fun unlinkClimbsFromSection(id: Long)

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

    /** A climb's logs follow its name, grade and where it is. */
    @Query(
        """
        UPDATE climbs SET name = :name, discipline = :discipline, gradeScale = :gradeScale, gradeIndex = :gradeIndex,
            gradeLabel = :gradeLabel, gradeColour = :gradeColour, placeId = :placeId, sectionId = :sectionId, areaId = :areaId,
            place = COALESCE(:placeName, place)
        WHERE problemId = :id
        """,
    )
    suspend fun syncLogs(
        id: Long,
        name: String,
        discipline: Discipline,
        gradeScale: GradeScale,
        gradeIndex: Int,
        gradeLabel: String?,
        gradeColour: Long?,
        placeId: Long?,
        sectionId: Long?,
        areaId: Long?,
        placeName: String?,
    )

    @Query("DELETE FROM climbs WHERE problemId = :id")
    suspend fun deleteLogsOfProblem(id: Long)

    @Query("SELECT * FROM problems WHERE retired = 0 AND ((:placeId IS NULL AND placeId IS NULL) OR placeId = :placeId) ORDER BY name COLLATE NOCASE")
    suspend fun getClimbsAt(placeId: Long?): List<ProblemEntity>

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
