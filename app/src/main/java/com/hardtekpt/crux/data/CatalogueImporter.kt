package com.hardtekpt.crux.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.ApplicationScope
import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Imports a board catalogue from a Boardsesh snapshot file (`.db`, or gzipped): it reads which
 * board the file is, then copies its listed climbs (only those for the climber's Kilter size) and
 * their grades at each angle into the catalogue database. It runs in the app's scope, so leaving
 * the page doesn't stop it; [state] says how far it got.
 */
@Singleton
class CatalogueImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogue: CatalogueDatabases,
    private val datasets: DatasetRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** A file read and ready to import: which board it is and how many climbs it lists. */
    data class Inspection(val fileName: String, val boardType: String, val layoutId: Int, val board: BoardModel, val climbs: Int)

    sealed interface State {
        data object Idle : State

        data object Reading : State

        data class Ready(val inspection: Inspection) : State

        data class Importing(val board: BoardModel, val done: Int, val total: Int) : State

        data class Done(val datasetId: Long, val name: String, val climbs: Int) : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val file get() = File(context.cacheDir, "catalogue-import.db")

    /** Whether [header] (a file's first bytes) is a Boardsesh snapshot: SQLite, or gzip. */
    fun looksLikeSnapshot(header: ByteArray): Boolean = (header.size >= 2 && header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) ||
        String(header.take(15).toByteArray(), Charsets.US_ASCII) == "SQLite format 3"

    /** Copies the file in (unzipping it) and reads which board it is. */
    fun inspect(uri: Uri) {
        if (_state.value is State.Importing) return
        _state.value = State.Reading
        scope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { read(uri) } }
                .fold({ State.Ready(it) }, { State.Failed((it as? DatasetFiles.Unreadable)?.message ?: UNREADABLE) })
        }
    }

    private fun read(uri: Uri): Inspection {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "file"
        val input = context.contentResolver.openInputStream(uri) ?: throw DatasetFiles.Unreadable(UNREADABLE)
        BufferedInputStream(input).use { stream ->
            stream.mark(2)
            val gzip = stream.read() == 0x1f && stream.read() == 0x8b
            stream.reset()
            (if (gzip) GZIPInputStream(stream) else stream).use { source -> file.outputStream().use { source.copyTo(it) } }
        }
        return inspect(file, name)
    }

    /** Reads which board a snapshot is; for tests too. */
    internal fun inspect(file: File, name: String): Inspection = open(file).use { db ->
        val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'board_climbs'", null).use { it.count }
        if (tables == 0) throw DatasetFiles.Unreadable(NOT_A_SNAPSHOT)
        db.rawQuery("SELECT board_type, layout_id, COUNT(*) FROM board_climbs GROUP BY 1, 2 ORDER BY 3 DESC LIMIT 1", null).use { c ->
            if (!c.moveToFirst()) throw DatasetFiles.Unreadable("This catalogue has no climbs.")
            val type = c.getString(0)
            val layout = c.getInt(1)
            val board = BoardseshBoards.model(type, layout)
                ?: throw DatasetFiles.Unreadable("Crux reads Kilter and MoonBoard catalogues; this one is another board ($type, layout $layout).")
            Inspection(name, type, layout, board, c.getInt(2))
        }
    }

    /** Imports the file read last, keeping only climbs for [size] and [holds] on a Kilter. */
    fun import(name: String, size: String?, holds: BoardHolds?) {
        val ready = _state.value as? State.Ready ?: return
        val inspection = ready.inspection
        _state.value = State.Importing(inspection.board, 0, inspection.climbs)
        scope.launch {
            _state.value = runCatching {
                withContext(Dispatchers.IO) {
                    copy(file, inspection, name, size, holds) { done ->
                        _state.value =
                            State.Importing(inspection.board, done, inspection.climbs)
                    }
                }
            }.fold({ (id, count) -> State.Done(id, name, count) }, { State.Failed((it as? DatasetFiles.Unreadable)?.message ?: UNREADABLE) })
            file.delete()
        }
    }

    /** Copies a snapshot's climbs and grades into the catalogue; returns the dataset's id and the climbs kept. */
    internal suspend fun copy(
        source: File,
        inspection: Inspection,
        name: String,
        size: String?,
        holds: BoardHolds?,
        progress: (Int) -> Unit = {},
    ): Pair<Long, Int> {
        val board = inspection.board
        val sizeIds = BoardseshBoards.sizeIds(board, size, holds)
        if (board.needsSize && sizeIds.isEmpty()) throw DatasetFiles.Unreadable("Pick your board's size first.")
        val key = CatalogueKey.of(inspection.boardType, inspection.layoutId, sizeIds.firstOrNull())
        val db = catalogue.override ?: catalogue.db
        val dao = db.catalogueDao()
        val kept = HashSet<String>()
        db.withTransaction {
            dao.deleteGrades(key)
            dao.deleteClimbs(key)
        }
        open(source).use { snapshot ->
            snapshot.rawQuery(
                "SELECT uuid, name, setter_username, description, frames, angle, created_at, compatible_size_ids FROM board_climbs " +
                    "WHERE board_type = ? AND layout_id = ? AND IFNULL(is_listed, 1) = 1 AND IFNULL(is_draft, 0) = 0 AND IFNULL(is_hidden, 0) = 0",
                arrayOf(inspection.boardType, inspection.layoutId.toString()),
            ).use { c ->
                val batch = ArrayList<CatalogueClimbEntity>(BATCH)
                var seen = 0
                while (c.moveToNext()) {
                    seen++
                    val sizes = c.getString(7)
                    if (sizeIds.isNotEmpty() && sizes != null && fits(sizes, sizeIds).not()) continue
                    val uuid = c.getString(0) ?: continue
                    val climbName = c.getString(1)?.trim()?.ifEmpty { null } ?: continue
                    kept += uuid
                    batch += CatalogueClimbEntity(
                        catalogueKey = key,
                        uuid = uuid,
                        name = climbName,
                        setter = c.getString(2)?.trim()?.ifEmpty { null },
                        description = c.getString(3)?.trim()?.ifEmpty { null },
                        frames = c.getString(4),
                        setterAngle = if (c.isNull(5)) null else c.getInt(5),
                        createdAt = c.getString(6),
                    )
                    if (batch.size == BATCH) {
                        db.withTransaction { dao.insertClimbs(batch) }
                        batch.clear()
                        progress(seen)
                    }
                }
                if (batch.isNotEmpty()) db.withTransaction { dao.insertClimbs(batch) }
            }
            snapshot.rawQuery(
                "SELECT climb_uuid, angle, display_difficulty, benchmark_difficulty, ascensionist_count, quality_average FROM board_climb_stats WHERE board_type = ?",
                arrayOf(inspection.boardType),
            ).use { c ->
                val batch = ArrayList<CatalogueGradeEntity>(BATCH)
                while (c.moveToNext()) {
                    val uuid = c.getString(0)
                    if (uuid !in kept) continue
                    batch += CatalogueGradeEntity(
                        catalogueKey = key,
                        uuid = uuid,
                        angle = c.getInt(1),
                        difficulty = if (c.isNull(2)) null else c.getDouble(2),
                        benchmark = !c.isNull(3),
                        ascents = if (c.isNull(4)) 0 else c.getInt(4),
                        quality = if (c.isNull(5)) null else c.getDouble(5),
                    )
                    if (batch.size == BATCH) {
                        db.withTransaction { dao.insertGrades(batch) }
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) db.withTransaction { dao.insertGrades(batch) }
            }
        }
        progress(inspection.climbs)
        val id = datasets.saveCatalogue(
            key,
            name.trim().ifEmpty {
                board.label
            },
            board,
            size.takeIf { board.needsSize },
            holds.takeIf { board.hasHolds },
            kept.size,
        )
        return id to kept.size
    }

    fun dismiss() {
        if (_state.value is State.Importing) return
        _state.value = State.Idle
        file.delete()
    }

    private fun open(file: File): SQLiteDatabase = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }.getOrElse { throw DatasetFiles.Unreadable(NOT_A_SNAPSHOT) }

    companion object {
        private const val BATCH = 1000
        const val UNREADABLE = "This file can't be read. Pick a Boardsesh snapshot (.db or .db.gz)."
        const val NOT_A_SNAPSHOT = "This file isn't a Boardsesh snapshot."

        /** Whether a JSON list of size ids ("[20,25,26]") has one of [ids]. */
        internal fun fits(sizes: String, ids: List<Int>): Boolean =
            sizes.trim('[', ']', ' ').split(',').mapNotNull { it.trim().toIntOrNull() }.any { it in ids }
    }
}
