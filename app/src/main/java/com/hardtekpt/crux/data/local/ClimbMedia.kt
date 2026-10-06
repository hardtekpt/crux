package com.hardtekpt.crux.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** What a piece of climb media is. Only images can be attached so far; video is planned. */
enum class MediaKind { IMAGE, VIDEO }

/**
 * A photo (and later a video) attached to a logged climb (schema 10). Files live in app
 * storage; this keeps the file name. Deleting the climb deletes its rows.
 */
@Entity(
    tableName = "climb_media",
    foreignKeys = [
        ForeignKey(entity = ClimbEntity::class, parentColumns = ["id"], childColumns = ["climbId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("climbId")],
)
data class ClimbMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val climbId: Long,
    val kind: MediaKind,
    val path: String,
    val createdAtMillis: Long,
)

@Dao
interface ClimbMediaDao {
    @Query("SELECT * FROM climb_media WHERE kind = 'IMAGE'")
    fun observeImages(): Flow<List<ClimbMediaEntity>>

    @Query("SELECT * FROM climb_media WHERE climbId = :climbId AND kind = :kind LIMIT 1")
    suspend fun get(climbId: Long, kind: MediaKind): ClimbMediaEntity?

    @Query("SELECT * FROM climb_media")
    suspend fun getAll(): List<ClimbMediaEntity>

    @Insert
    suspend fun insert(media: ClimbMediaEntity): Long

    @Query("DELETE FROM climb_media WHERE climbId = :climbId AND kind = :kind")
    suspend fun delete(climbId: Long, kind: MediaKind)
}
