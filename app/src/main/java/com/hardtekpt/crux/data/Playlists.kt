package com.hardtekpt.crux.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An ordered pick of climbs from any datasets, like "Tonight" (schema 28). */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * A climb in a playlist. It points at a dataset climb and goes with it; one imported before its
 * dataset is here has no climb yet and waits, greyed, to be matched by its name and grade.
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DatasetClimbEntity::class, parentColumns = ["id"], childColumns = ["datasetClimbId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId"), Index("datasetClimbId")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val datasetClimbId: Long? = null,
    /** A copy of the climb's dataset, name and grade, for matching it again after an import. */
    val datasetName: String? = null,
    val name: String,
    val discipline: Discipline = Discipline.BOULDER,
    val gradeScale: GradeScale? = null,
    val gradeIndex: Int? = null,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY id")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items ORDER BY playlistId, position, id")
    fun observeItems(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId, position, id")
    suspend fun getAllItems(): List<PlaylistItemEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position, id")
    suspend fun getItems(playlistId: Long): List<PlaylistItemEntity>

    @Insert suspend fun insertItem(item: PlaylistItemEntity): Long

    @Update suspend fun updateItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    /** Dataset climbs logged on a day, by the climbs picked from them: whether any of those goes was a send. */
    @Query(
        """
        SELECT p.sourceDatasetClimbId AS climbId, MAX(c.sends > 0) AS sent
        FROM climbs c JOIN problems p ON p.id = c.problemId
        WHERE c.dateEpochDay = :day AND p.sourceDatasetClimbId IS NOT NULL
        GROUP BY p.sourceDatasetClimbId
        """,
    )
    fun observeTriedOn(day: Long): Flow<List<TriedRow>>
}

/** A dataset climb tried on a day, and whether it was sent. */
data class TriedRow(val climbId: Long, val sent: Boolean)

/** A climb in a playlist as the screens show it: the dataset climb as it is now, when it's still there. */
data class PlaylistItem(
    val id: Long,
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale?,
    val gradeIndex: Int?,
    val datasetName: String?,
    /** The dataset climb, or null while it isn't here. */
    val climb: DatasetClimb? = null,
    val dataset: Dataset? = null,
    val position: Int = 0,
) {
    val matched: Boolean get() = climb != null
    val gradeLabel: String? get() = gradeScale?.takeIf { !it.isLocal }?.let { scale -> gradeIndex?.let(scale::label) }
}

data class Playlist(
    val id: Long,
    val name: String,
    val note: String? = null,
    val items: List<PlaylistItem> = emptyList(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    /** The datasets its climbs come from, in order, each once. */
    val datasetNames: List<String> get() = items.mapNotNull { it.dataset?.name ?: it.datasetName }.distinct()
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    /** Creates a playlist when [id] is 0, else renames it; returns its id. */
    suspend fun savePlaylist(id: Long, name: String, note: String?): Long
    suspend fun deletePlaylist(id: Long)

    /** A copy with the same climbs, named "<name> copy". */
    suspend fun duplicate(id: Long): Long

    /** Adds climbs at the end, leaving out those already in it; returns how many went in. */
    suspend fun addClimbs(playlistId: Long, climbs: List<ClimbSource>): Int
    suspend fun removeItem(itemId: Long)

    /** Puts back an item just removed, where it was. */
    suspend fun restoreItem(playlistId: Long, item: PlaylistItem)

    /** Puts the playlist's items in this order. */
    suspend fun reorder(playlistId: Long, itemIds: List<Long>)

    /** Makes a playlist from a file; returns its id and how many of its climbs found theirs. */
    suspend fun import(file: PlaylistFiles.Parsed): Pair<Long, Int>

    /** Dataset climbs tried on [date] (by dataset climb id): true where one was sent. */
    fun observeTriedOn(date: java.time.LocalDate): Flow<Map<Long, Boolean>>
}

class OfflinePlaylistRepository @Inject constructor(private val dbs: CruxDatabases, private val clock: Clock) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<Playlist>> = dbs.observe { db ->
        combine(db.playlistDao().observePlaylists(), db.playlistDao().observeItems(), db.datasetDao().observeDatasets(), db.datasetDao().observeClimbs()) {
                lists,
                items,
                datasets,
                climbs,
            ->
            val byList = items.groupBy { it.playlistId }
            val datasetById = datasets.associateBy { it.id }
            val climbById = climbs.associateBy { it.id }
            lists.map { list ->
                Playlist(
                    id = list.id,
                    name = list.name,
                    note = list.note,
                    createdAtMillis = list.createdAtMillis,
                    updatedAtMillis = list.updatedAtMillis,
                    items = byList[list.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).map { item ->
                        val climb = item.datasetClimbId?.let(climbById::get)
                        val dataset = climb?.let { datasetById[it.datasetId] }
                        val model = climb?.let {
                            DatasetClimb(it.id, it.name, it.discipline, it.gradeScale, it.gradeIndex, it.setter, it.note, it.createdAtMillis)
                        }
                        PlaylistItem(
                            id = item.id,
                            name = model?.name ?: item.name,
                            discipline = model?.discipline ?: item.discipline,
                            gradeScale = if (model != null) model.gradeScale else item.gradeScale,
                            gradeIndex = if (model != null) model.gradeIndex else item.gradeIndex,
                            datasetName = dataset?.name ?: item.datasetName,
                            climb = model,
                            dataset = dataset?.toModel(emptyList()),
                            position = item.position,
                        )
                    },
                )
            }
        }
    }

    override suspend fun savePlaylist(id: Long, name: String, note: String?): Long {
        val dao = dbs.current().playlistDao()
        val now = clock.millis()
        val existing = id.takeIf { it != 0L }?.let { dao.getPlaylist(it) }
        val clean = name.trim().take(MAX_NAME).ifEmpty { "Playlist" }
        val text = note?.trim()?.ifEmpty { null }
        return if (existing != null) {
            dao.updatePlaylist(existing.copy(name = clean, note = text, updatedAtMillis = now))
            existing.id
        } else {
            dao.insertPlaylist(PlaylistEntity(name = clean, note = text, createdAtMillis = now, updatedAtMillis = now))
        }
    }

    override suspend fun deletePlaylist(id: Long) = dbs.current().playlistDao().deletePlaylist(id)

    override suspend fun duplicate(id: Long): Long {
        val db = dbs.current()
        return db.withTransaction {
            val dao = db.playlistDao()
            val list = dao.getPlaylist(id) ?: return@withTransaction 0L
            val now = clock.millis()
            val copy = dao.insertPlaylist(list.copy(id = 0, name = "${list.name} copy".take(MAX_NAME), createdAtMillis = now, updatedAtMillis = now))
            dao.getItems(id).forEach { dao.insertItem(it.copy(id = 0, playlistId = copy)) }
            copy
        }
    }

    override suspend fun addClimbs(playlistId: Long, climbs: List<ClimbSource>): Int {
        val db = dbs.current()
        return db.withTransaction {
            val dao = db.playlistDao()
            val items = dao.getItems(playlistId)
            val have = items.mapNotNull { it.datasetClimbId }.toMutableSet()
            var position = (items.maxOfOrNull { it.position } ?: -1) + 1
            var added = 0
            climbs.forEach { (climb, dataset) ->
                if (climb.id in have) return@forEach
                have += climb.id
                dao.insertItem(
                    PlaylistItemEntity(
                        playlistId = playlistId,
                        position = position++,
                        datasetClimbId = climb.id,
                        datasetName = dataset.name,
                        name = climb.name,
                        discipline = climb.discipline,
                        gradeScale = climb.gradeScale,
                        gradeIndex = climb.gradeIndex,
                    ),
                )
                added++
            }
            touch(playlistId)
            added
        }
    }

    override suspend fun removeItem(itemId: Long) = dbs.current().playlistDao().deleteItem(itemId)

    override suspend fun restoreItem(playlistId: Long, item: PlaylistItem) {
        val db = dbs.current()
        db.withTransaction {
            val dao = db.playlistDao()
            // Those after it move down one, so it goes back where it was.
            dao.updateItems(dao.getItems(playlistId).filter { it.position >= item.position }.map { it.copy(position = it.position + 1) })
            dao.insertItem(
                PlaylistItemEntity(
                    id = item.id,
                    playlistId = playlistId,
                    position = item.position,
                    datasetClimbId = item.climb?.id?.takeIf { db.datasetDao().getClimb(it) != null },
                    datasetName = item.datasetName,
                    name = item.name,
                    discipline = item.discipline,
                    gradeScale = item.gradeScale,
                    gradeIndex = item.gradeIndex,
                ),
            )
        }
    }

    override suspend fun reorder(playlistId: Long, itemIds: List<Long>) {
        val db = dbs.current()
        db.withTransaction {
            val dao = db.playlistDao()
            val order = itemIds.withIndex().associate { (index, id) -> id to index }
            dao.updateItems(dao.getItems(playlistId).map { it.copy(position = order[it.id] ?: (itemIds.size + it.position)) })
            touch(playlistId)
        }
    }

    override suspend fun import(file: PlaylistFiles.Parsed): Pair<Long, Int> {
        val db = dbs.current()
        return db.withTransaction {
            val datasets = db.datasetDao().getDatasets()
            val climbs = db.datasetDao().getAllClimbs()
            val id = savePlaylist(0, uniqueName(file.name, db.playlistDao().getPlaylists().map { it.name }), file.note)
            var matched = 0
            file.items.forEachIndexed { index, item ->
                val climb = PlaylistFiles.match(item, datasets, climbs)
                if (climb != null) matched++
                db.playlistDao().insertItem(
                    PlaylistItemEntity(
                        playlistId = id,
                        position = index,
                        datasetClimbId = climb?.id,
                        datasetName = item.dataset,
                        name = item.name,
                        discipline = item.discipline,
                        gradeScale = climb?.gradeScale,
                        gradeIndex = climb?.gradeIndex,
                    ),
                )
            }
            id to matched
        }
    }

    override fun observeTriedOn(date: java.time.LocalDate): Flow<Map<Long, Boolean>> = dbs.observe { db ->
        db.playlistDao().observeTriedOn(date.toEpochDay()).map { rows -> rows.associate { it.climbId to it.sent } }
    }

    private suspend fun touch(playlistId: Long) {
        val dao = dbs.current().playlistDao()
        dao.getPlaylist(playlistId)?.let { dao.updatePlaylist(it.copy(updatedAtMillis = clock.millis())) }
    }

    private fun uniqueName(name: String, taken: List<String>): String {
        val names = taken.map { it.trim().lowercase() }.toSet()
        val base = name.trim().ifEmpty { "Playlist" }
        return if (base.lowercase() !in names) base else generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { it.lowercase() !in names }
    }

    private companion object {
        const val MAX_NAME = 60
    }
}

/**
 * A playlist as a file (`.crux-playlist.json`, format "crux-playlist", version 1): its name and
 * note, and each climb by its dataset's name, its own name, type and grade. Importing matches
 * each to a dataset climb here by dataset and name, then by name and grade.
 */
object PlaylistFiles {
    const val FORMAT = "crux-playlist"
    const val VERSION = 1

    @Serializable
    data class CruxFile(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exported: String? = null,
        val playlist: CruxPlaylist,
        val items: List<CruxItem> = emptyList(),
    )

    @Serializable
    data class CruxPlaylist(val name: String, val note: String? = null)

    @Serializable
    data class CruxItem(val dataset: String? = null, val name: String, val type: String = "boulder", val grade: String? = null)

    /** A playlist read from a file, before it's matched. */
    data class Parsed(val name: String, val note: String?, val items: List<Item>)

    data class Item(val dataset: String?, val name: String, val discipline: Discipline, val grade: String?)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun write(playlist: Playlist, exportedAt: String): String = json.encodeToString(
        CruxFile.serializer(),
        CruxFile(
            exported = exportedAt,
            playlist = CruxPlaylist(playlist.name, playlist.note),
            items = playlist.items.map { CruxItem(it.datasetName, it.name, if (it.discipline == Discipline.ROUTE) "route" else "boulder", it.gradeLabel) },
        ),
    )

    fun parse(text: String): Parsed {
        val file = runCatching {
            json.decodeFromString(CruxFile.serializer(), text.removePrefix(0xFEFF.toChar().toString()))
        }.getOrElse { throw DatasetFiles.Unreadable("This file isn't a Crux playlist.") }
        if (file.format != FORMAT) throw DatasetFiles.Unreadable("This file isn't a Crux playlist.")
        if (file.version > VERSION) throw DatasetFiles.Unreadable("This playlist comes from a newer version of Crux. Update the app to import it.")
        return Parsed(
            name = file.playlist.name.trim().ifEmpty { "Playlist" },
            note = file.playlist.note,
            items = file.items.filter { it.name.isNotBlank() }.map {
                Item(
                    it.dataset?.trim()?.ifEmpty {
                        null
                    },
                    it.name.trim(),
                    if (it.type.trim().lowercase().startsWith("route")) Discipline.ROUTE else Discipline.BOULDER,
                    it.grade?.trim()?.ifEmpty {
                        null
                    },
                )
            },
        )
    }

    /** Whether [text] looks like a playlist file rather than a dataset. */
    fun isPlaylist(text: String): Boolean = "\"$FORMAT\"" in text.take(200)

    /**
     * The dataset climb [item] is: one of that name and type in the dataset of its name, else the
     * only one of that name and type anywhere, else the one of those that also has its grade.
     */
    fun match(item: Item, datasets: List<DatasetEntity>, climbs: List<DatasetClimbEntity>): DatasetClimbEntity? {
        val named = climbs.filter { it.discipline == item.discipline && it.name.trim().equals(item.name, ignoreCase = true) }
        val datasetIds = datasets.filter { it.name.trim().equals(item.dataset ?: return@filter false, ignoreCase = true) }.map { it.id }.toSet()
        named.firstOrNull { it.datasetId in datasetIds }?.let { return it }
        if (named.size == 1) return named.single()
        val grade = item.grade ?: return null
        return named.firstOrNull { c -> c.gradeScale?.takeIf { !it.isLocal }?.let { s -> c.gradeIndex?.let(s::label) }.equals(grade, ignoreCase = true) }
    }
}
