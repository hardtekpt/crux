package com.hardtekpt.crux.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.hardtekpt.crux.data.local.CruxDatabases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A free note: how training feels, an injury to watch, beta to remember (schema 13). */
@Entity(tableName = "notes", indices = [Index("createdAtMillis")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Pinned notes stay at the top of the list. */
    val pinned: Boolean = false,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAtMillis")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

data class Note(
    val id: Long,
    val text: String,
    val created: LocalDate,
    val pinned: Boolean,
) {
    /** The first line, for lists; the rest is the body. */
    val title: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val body: String get() = text.trim().removePrefix(title).trim()
}

interface NoteRepository {
    /** Pinned first, then newest first. */
    fun observeNotes(): Flow<List<Note>>
    suspend fun getNote(id: Long): Note?
    /** Creates when [id] is 0; returns the note's id. */
    suspend fun saveNote(id: Long, text: String, pinned: Boolean): Long
    suspend fun deleteNote(id: Long)
}

class OfflineNoteRepository @Inject constructor(
    private val dbs: CruxDatabases,
    private val clock: Clock,
) : NoteRepository {
    override fun observeNotes(): Flow<List<Note>> = dbs.observe { it.noteDao().observeAll() }.map { list -> list.map { it.toModel() } }

    override suspend fun getNote(id: Long): Note? = dbs.current().noteDao().get(id)?.toModel()

    override suspend fun saveNote(id: Long, text: String, pinned: Boolean): Long {
        val dao = dbs.current().noteDao()
        val now = clock.millis()
        val existing = if (id != 0L) dao.get(id) else null
        return if (existing != null) {
            dao.update(existing.copy(text = text.trim(), pinned = pinned, updatedAtMillis = now))
            existing.id
        } else {
            dao.insert(NoteEntity(text = text.trim(), createdAtMillis = now, updatedAtMillis = now, pinned = pinned))
        }
    }

    override suspend fun deleteNote(id: Long) = dbs.current().noteDao().delete(id)

    private fun NoteEntity.toModel() = Note(
        id = id,
        text = text,
        created = Instant.ofEpochMilli(createdAtMillis).atZone(clock.zone ?: ZoneId.systemDefault()).toLocalDate(),
        pinned = pinned,
    )
}
