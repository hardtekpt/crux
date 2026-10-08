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
import kotlinx.coroutines.flow.Flow

enum class SessionStatus { RUNNING, FINISHED, DISCARDED }

/**
 * A live session: a plan being run, or a session without one (a climbing day, an unplanned
 * workout). Schema 17. A session copies its plan's exercises when it starts, so editing the
 * plan later doesn't change it.
 */
@Entity(tableName = "sessions", indices = [Index("status"), Index("startedAtMillis")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The plan's name, or "Climbing session" without one. */
    val name: String,
    /** The plan it was started from; kept even if the plan is deleted later. */
    val templateId: Long? = null,
    val placeId: Long? = null,
    val sectionId: Long? = null,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val status: SessionStatus = SessionStatus.RUNNING,
    /** How hard it felt, 1 to 10. */
    val effort: Int? = null,
    val notes: String? = null,
)

/** One exercise in a session, with its target as planned (or as added on the fly). */
@Entity(
    tableName = "session_items",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    /** The plan block it came from; empty for exercises added during the session. */
    @ColumnInfo(defaultValue = "") val blockName: String = "",
    val position: Int,
    val sets: Int,
    val reps: Int,
    val seconds: Int,
    val loadKg: Double,
    val restSeconds: Int,
    val repRestSeconds: Int = 0,
)

/** A set done (or skipped) in a session, with what was actually done. */
@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(entity = SessionItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** 0-based set number within the item. */
    val setIndex: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val loadKg: Double? = null,
    val skipped: Boolean = false,
    val completedAtMillis: Long,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeRunning(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getRunning(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'FINISHED' ORDER BY startedAtMillis DESC")
    fun observeFinished(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertItems(items: List<SessionItemEntity>)

    @Insert
    suspend fun insertItem(item: SessionItemEntity): Long

    @Query("SELECT * FROM session_items WHERE sessionId = :sessionId ORDER BY position")
    fun observeItems(sessionId: Long): Flow<List<SessionItemEntity>>

    @Query("SELECT * FROM session_items ORDER BY sessionId, position")
    fun observeAllItems(): Flow<List<SessionItemEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM session_items WHERE sessionId = :sessionId")
    suspend fun nextItemPosition(sessionId: Long): Int

    @Insert
    suspend fun insertSet(set: SessionSetEntity): Long

    @Query("DELETE FROM session_sets WHERE itemId = :itemId AND setIndex = :setIndex")
    suspend fun deleteSet(itemId: Long, setIndex: Int)

    @Query("SELECT s.* FROM session_sets s JOIN session_items i ON i.id = s.itemId WHERE i.sessionId = :sessionId ORDER BY s.itemId, s.setIndex")
    fun observeSets(sessionId: Long): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets ORDER BY itemId, setIndex")
    fun observeAllSets(): Flow<List<SessionSetEntity>>

    @Query("UPDATE climbs SET sessionId = NULL WHERE sessionId = :sessionId")
    suspend fun unlinkClimbs(sessionId: Long)

    @Query("SELECT * FROM sessions WHERE status = 'FINISHED' ORDER BY startedAtMillis")
    suspend fun getFinished(): List<SessionEntity>

    @Query("SELECT * FROM session_items ORDER BY sessionId, position")
    suspend fun getAllItems(): List<SessionItemEntity>

    @Query("SELECT * FROM session_sets ORDER BY itemId, setIndex")
    suspend fun getAllSets(): List<SessionSetEntity>

    /** Its sets go with them. */
    @Query("DELETE FROM session_items WHERE sessionId = :sessionId")
    suspend fun deleteItems(sessionId: Long)
}
