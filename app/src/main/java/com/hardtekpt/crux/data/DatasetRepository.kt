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
import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * A list of climbs to pick from (schema 27): a home board's problems, a spray wall, a gym circuit.
 * Only your own for now ([kind] "own"); board catalogues arrive later as "catalogue". It can be
 * for a board (tier 1's keys) and has its own grade scales; null scales follow the climber's
 * settings.
 */
@Entity(tableName = "datasets")
data class DatasetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = KIND_OWN,
    val boardModel: String? = null,
    val boardSize: String? = null,
    val boardHolds: String? = null,
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** A board catalogue's key in the catalogue database ([CatalogueKey]); null for your own (schema 29). */
    val catalogueKey: String? = null,
    /** A catalogue's climb count, kept here since its climbs are in another file. */
    @androidx.room.ColumnInfo(defaultValue = "0") val climbCount: Int = 0,
) {
    companion object {
        const val KIND_OWN = "own"
        const val KIND_CATALOGUE = "catalogue"

        /** [boardModel] of a dataset for a custom board; null is no board. */
        const val CUSTOM_BOARD = "custom"
    }
}

/**
 * An entry in a dataset, not a Crux climb: its name, boulder or route, an optional grade (the same
 * scale and index climbs keep), setter and note.
 */
@Entity(
    tableName = "dataset_climbs",
    foreignKeys = [ForeignKey(entity = DatasetEntity::class, parentColumns = ["id"], childColumns = ["datasetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("datasetId")],
)
data class DatasetClimbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long,
    val name: String,
    val discipline: Discipline = Discipline.BOULDER,
    val gradeScale: GradeScale? = null,
    val gradeIndex: Int? = null,
    val setter: String? = null,
    val note: String? = null,
    val createdAtMillis: Long,
)

/**
 * A dataset linked by hand to a section of a place (schema 28), such as a gym circuit on the
 * main gym. Datasets made for a board need no link: they belong to every section on that board.
 */
@Entity(
    tableName = "section_datasets",
    primaryKeys = ["sectionId", "datasetId"],
    foreignKeys = [
        ForeignKey(
            entity = com.hardtekpt.crux.data.local.SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = DatasetEntity::class, parentColumns = ["id"], childColumns = ["datasetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("datasetId")],
)
data class SectionDatasetEntity(val sectionId: Long, val datasetId: Long)

@Dao
interface DatasetDao {
    @Query("SELECT * FROM section_datasets")
    fun observeLinks(): Flow<List<SectionDatasetEntity>>

    @Query("SELECT * FROM section_datasets")
    suspend fun getLinks(): List<SectionDatasetEntity>

    @Query("DELETE FROM section_datasets WHERE sectionId = :sectionId")
    suspend fun clearLinks(sectionId: Long)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<SectionDatasetEntity>)

    @Query("SELECT * FROM dataset_climbs WHERE id = :id")
    suspend fun getClimb(id: Long): DatasetClimbEntity?

    @Query("SELECT * FROM datasets ORDER BY name COLLATE NOCASE")
    fun observeDatasets(): Flow<List<DatasetEntity>>

    @Query("SELECT * FROM datasets ORDER BY id")
    suspend fun getDatasets(): List<DatasetEntity>

    @Query("SELECT * FROM datasets WHERE id = :id")
    suspend fun getDataset(id: Long): DatasetEntity?

    @Insert suspend fun insertDataset(dataset: DatasetEntity): Long

    @Update suspend fun updateDataset(dataset: DatasetEntity)

    @Query("DELETE FROM datasets WHERE id = :id")
    suspend fun deleteDataset(id: Long)

    @Query("SELECT * FROM dataset_climbs ORDER BY datasetId, id")
    fun observeClimbs(): Flow<List<DatasetClimbEntity>>

    @Query("SELECT * FROM dataset_climbs WHERE datasetId = :datasetId ORDER BY id")
    suspend fun getClimbs(datasetId: Long): List<DatasetClimbEntity>

    @Query("SELECT * FROM dataset_climbs ORDER BY datasetId, id")
    suspend fun getAllClimbs(): List<DatasetClimbEntity>

    @Insert suspend fun insertClimb(climb: DatasetClimbEntity): Long

    @Update suspend fun updateClimb(climb: DatasetClimbEntity)

    @Query("DELETE FROM dataset_climbs WHERE id = :id")
    suspend fun deleteClimb(id: Long)

    @Query("DELETE FROM dataset_climbs WHERE datasetId = :datasetId AND id IN (:ids)")
    suspend fun deleteClimbs(datasetId: Long, ids: List<Long>)
}

/** A dataset climb as the screens and files use it. */
data class DatasetClimb(
    val id: Long = 0,
    val name: String,
    val discipline: Discipline = Discipline.BOULDER,
    val gradeScale: GradeScale? = null,
    val gradeIndex: Int? = null,
    val setter: String? = null,
    val note: String? = null,
    val createdAtMillis: Long = 0,
    /** A board catalogue's climb: its id on the board (then [id] is 0). */
    val catalogueUuid: String? = null,
) {
    /** "6C+", or null when ungraded. */
    val gradeLabel: String? get() = gradeScale?.takeIf { !it.isLocal }?.let { scale -> gradeIndex?.let(scale::label) }
}

data class Dataset(
    val id: Long = 0,
    val name: String,
    val board: BoardModel? = null,
    /** For a custom board (spray wall, home board) rather than no board. */
    val customBoard: Boolean = false,
    val boardSize: String? = null,
    val boardHolds: BoardHolds? = null,
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    val climbs: List<DatasetClimb> = emptyList(),
    /** A board catalogue: its key in the catalogue database, and how many climbs it has. */
    val catalogueKey: String? = null,
    val catalogueCount: Int = 0,
) {
    val isCatalogue: Boolean get() = catalogueKey != null

    /** How many climbs it has. */
    val size: Int get() = if (isCatalogue) catalogueCount else climbs.size

    /** "Kilter Original 12×12", "Custom board", or "No board". */
    val boardLabel: String get() = com.hardtekpt.crux.data.model.boardLabel(board, boardSize, boardHolds) ?: if (customBoard) "Custom board" else "No board"

    fun scaleFor(discipline: Discipline): GradeScale? = if (discipline == Discipline.BOULDER) boulderScale else routeScale

    /**
     * Whether this dataset is made for [section]'s board: a board section of the same model and,
     * for Kilter, the same size (and a Homewall's holds). A custom board matches nothing.
     */
    fun fitsBoard(section: com.hardtekpt.crux.data.model.Section): Boolean {
        val model = board ?: return false
        return section.type == com.hardtekpt.crux.data.model.PlaceType.BOARD &&
            section.board == model &&
            (!model.needsSize || boardSize == section.boardSize) &&
            (!model.hasHolds || boardHolds == section.boardHolds)
    }
}

/** Which dataset a climb came from, as Log climb shows it: "from Garage board · set by Ana". */
data class ClimbSource(val climb: DatasetClimb, val dataset: Dataset)

/**
 * The datasets a section uses: those made for its board, then those linked to it by hand
 * ([linked], the section's links), each once.
 */
fun datasetsFor(section: com.hardtekpt.crux.data.model.Section?, datasets: List<Dataset>, linked: Set<Long>): List<Dataset> {
    section ?: return emptyList()
    return datasets.filter { it.fitsBoard(section) } + datasets.filter { it.id in linked && !it.fitsBoard(section) }
}

/** What to do with an imported climb whose name and type are already in the dataset. */
enum class ClashChoice(val label: String) { SKIP("Skip"), REPLACE("Replace"), KEEP_BOTH("Keep both") }

/** What an import did: climbs added, replaced and skipped. */
data class ImportCount(val added: Int, val replaced: Int, val skipped: Int)

interface DatasetRepository {
    fun observeDatasets(): Flow<List<Dataset>>

    /** Creates a dataset when [Dataset.id] is 0, else changes its name, board and scales; returns its id. */
    suspend fun saveDataset(dataset: Dataset): Long

    /** Deletes a dataset and its climbs. Climbs logged in Crux are never touched. */
    suspend fun deleteDataset(id: Long)

    /** Adds or changes a climb in [datasetId]; returns its id. */
    suspend fun saveClimb(datasetId: Long, climb: DatasetClimb): Long
    suspend fun deleteClimb(id: Long)

    /** Puts back a climb just deleted, with its id. */
    suspend fun restoreClimb(datasetId: Long, climb: DatasetClimb)

    /**
     * Adds [climbs] to a dataset: a new one named [newDataset] (with its board and scales), or
     * the one with id [intoId]. A climb whose name and type are already there follows [clash].
     */
    suspend fun importClimbs(newDataset: Dataset?, intoId: Long?, climbs: List<DatasetClimb>, clash: ClashChoice): Pair<Long, ImportCount>

    /** Adds or refreshes the board catalogue of [key] with [count] climbs; returns its id. */
    suspend fun saveCatalogue(key: String, name: String, board: BoardModel, size: String?, holds: BoardHolds?, count: Int): Long = 0

    /** The datasets linked to each section by hand, by section id. */
    fun observeLinks(): Flow<Map<Long, Set<Long>>>

    /** Sets the datasets linked to a section by hand. */
    suspend fun setLinks(sectionId: Long, datasetIds: Set<Long>)
}

class OfflineDatasetRepository @Inject constructor(private val dbs: CruxDatabases, private val clock: Clock) : DatasetRepository {
    override fun observeDatasets(): Flow<List<Dataset>> = dbs.observe { db ->
        combine(db.datasetDao().observeDatasets(), db.datasetDao().observeClimbs()) { sets, climbs ->
            val byDataset = climbs.groupBy { it.datasetId }
            sets.map { it.toModel(byDataset[it.id].orEmpty()) }
        }
    }

    override suspend fun saveDataset(dataset: Dataset): Long {
        val dao = dbs.current().datasetDao()
        val now = clock.millis()
        val existing = dataset.id.takeIf { it != 0L }?.let { dao.getDataset(it) }
        val board = dataset.board
        val fresh = (existing ?: DatasetEntity(name = "", createdAtMillis = now, updatedAtMillis = now)).copy(
            name = dataset.name.trim().take(MAX_NAME),
            boardModel = board?.key ?: DatasetEntity.CUSTOM_BOARD.takeIf { dataset.customBoard },
            boardSize = dataset.boardSize?.takeIf { board?.sizes?.contains(it) == true },
            boardHolds = dataset.boardHolds?.key?.takeIf { board?.hasHolds == true },
            boulderScale = dataset.boulderScale?.takeIf { !it.isLocal },
            routeScale = dataset.routeScale?.takeIf { !it.isLocal },
            updatedAtMillis = now,
        )
        return if (existing != null) {
            dao.updateDataset(fresh)
            existing.id
        } else {
            dao.insertDataset(fresh)
        }
    }

    override suspend fun deleteDataset(id: Long) = dbs.current().datasetDao().deleteDataset(id)

    override suspend fun saveClimb(datasetId: Long, climb: DatasetClimb): Long {
        val db = dbs.current()
        return db.withTransaction {
            val dao = db.datasetDao()
            val existing = climb.id.takeIf { it != 0L }?.let { id -> dao.getClimbs(datasetId).firstOrNull { it.id == id } }
            val entity = climb.toEntity(datasetId, existing?.createdAtMillis ?: clock.millis())
            val id = if (existing != null) {
                dao.updateClimb(entity.copy(id = existing.id))
                existing.id
            } else {
                dao.insertClimb(entity.copy(id = 0))
            }
            touch(db, datasetId)
            id
        }
    }

    override suspend fun deleteClimb(id: Long) = dbs.current().datasetDao().deleteClimb(id)

    override suspend fun restoreClimb(datasetId: Long, climb: DatasetClimb) {
        dbs.current().datasetDao().insertClimb(climb.toEntity(datasetId, climb.createdAtMillis.takeIf { it > 0 } ?: clock.millis()).copy(id = climb.id))
    }

    override suspend fun importClimbs(newDataset: Dataset?, intoId: Long?, climbs: List<DatasetClimb>, clash: ClashChoice): Pair<Long, ImportCount> {
        val db = dbs.current()
        return db.withTransaction {
            val dao = db.datasetDao()
            val id = intoId ?: saveDataset((newDataset ?: Dataset(name = "Imported")).copy(id = 0))
            val have = dao.getClimbs(id).groupBy { it.discipline to it.name.trim().lowercase() }
            var added = 0
            var replaced = 0
            var skipped = 0
            val now = clock.millis()
            climbs.forEach { climb ->
                val existing = have[climb.discipline to climb.name.trim().lowercase()]?.firstOrNull()
                val entity = climb.toEntity(id, now)
                when {
                    existing == null || clash == ClashChoice.KEEP_BOTH -> {
                        dao.insertClimb(entity)
                        added++
                    }

                    clash == ClashChoice.REPLACE -> {
                        dao.updateClimb(entity.copy(id = existing.id, createdAtMillis = existing.createdAtMillis))
                        replaced++
                    }

                    else -> skipped++
                }
            }
            touch(db, id)
            id to ImportCount(added, replaced, skipped)
        }
    }

    override suspend fun saveCatalogue(key: String, name: String, board: BoardModel, size: String?, holds: BoardHolds?, count: Int): Long {
        val dao = dbs.current().datasetDao()
        val now = clock.millis()
        val existing = dao.getDatasets().firstOrNull { it.catalogueKey == key }
        val entity = (existing ?: DatasetEntity(name = name, createdAtMillis = now, updatedAtMillis = now)).copy(
            kind = DatasetEntity.KIND_CATALOGUE,
            boardModel = board.key,
            boardSize = size,
            boardHolds = holds?.key,
            catalogueKey = key,
            climbCount = count,
            updatedAtMillis = now,
        )
        return if (existing != null) {
            dao.updateDataset(entity)
            existing.id
        } else {
            dao.insertDataset(entity)
        }
    }

    override fun observeLinks(): Flow<Map<Long, Set<Long>>> = dbs.observe { db ->
        db.datasetDao().observeLinks().map { links -> links.groupBy({ it.sectionId }, { it.datasetId }).mapValues { it.value.toSet() } }
    }

    override suspend fun setLinks(sectionId: Long, datasetIds: Set<Long>) {
        val db = dbs.current()
        db.withTransaction {
            db.datasetDao().clearLinks(sectionId)
            db.datasetDao().insertLinks(datasetIds.map { SectionDatasetEntity(sectionId, it) })
        }
    }

    private suspend fun touch(db: com.hardtekpt.crux.data.local.CruxDatabase, datasetId: Long) {
        val dao = db.datasetDao()
        dao.getDataset(datasetId)?.let { dao.updateDataset(it.copy(updatedAtMillis = clock.millis())) }
    }

    private companion object {
        const val MAX_NAME = 60
    }
}

internal fun DatasetClimb.toEntity(datasetId: Long, createdAt: Long) = DatasetClimbEntity(
    id = id,
    datasetId = datasetId,
    name = name.trim().take(60),
    discipline = discipline,
    gradeScale = gradeScale?.takeIf { gradeIndex != null && !it.isLocal },
    gradeIndex = gradeIndex?.takeIf { gradeScale != null && !gradeScale.isLocal },
    setter = setter?.trim()?.ifEmpty { null },
    note = note?.trim()?.ifEmpty { null },
    createdAtMillis = createdAt,
)

internal fun DatasetEntity.toModel(climbs: List<DatasetClimbEntity>) = Dataset(
    id = id,
    name = name,
    board = BoardModel.fromKey(boardModel),
    customBoard = boardModel == DatasetEntity.CUSTOM_BOARD,
    boardSize = boardSize,
    boardHolds = BoardHolds.fromKey(boardHolds),
    boulderScale = boulderScale,
    routeScale = routeScale,
    climbs = climbs.map { DatasetClimb(it.id, it.name, it.discipline, it.gradeScale, it.gradeIndex, it.setter, it.note, it.createdAtMillis) },
    catalogueKey = catalogueKey,
    catalogueCount = climbCount,
)

/** One dataset as it changes; null once deleted. */
fun DatasetRepository.observeDataset(id: Long): Flow<Dataset?> = observeDatasets().map { list -> list.firstOrNull { it.id == id } }

/**
 * Grade [index] of [from] as a grade of [to]: the same one in the same scale, else through the
 * grade converter (the easier end of a range). Null when it can't be: local grades, another
 * discipline, or a grade the converter doesn't know.
 */
fun convertGrade(from: GradeScale, index: Int, to: GradeScale): Int? {
    if (from == to) return index.takeIf { it in from.grades.indices }
    if (from.isLocal || to.isLocal || from.discipline != to.discipline) return null
    fun system(scale: GradeScale) = when (scale) {
        GradeScale.FONT -> com.hardtekpt.crux.data.model.GradeConversion.System.FONT
        GradeScale.V_SCALE -> com.hardtekpt.crux.data.model.GradeConversion.System.V
        GradeScale.FRENCH -> com.hardtekpt.crux.data.model.GradeConversion.System.FRENCH
        GradeScale.YDS -> com.hardtekpt.crux.data.model.GradeConversion.System.YDS
        else -> null
    }
    val label = from.grades.getOrNull(index) ?: return null
    val converted = com.hardtekpt.crux.data.model.GradeConversion.convert(system(from) ?: return null, label, system(to) ?: return null) ?: return null
    return to.grades.indexOf(converted.substringBefore('–')).takeIf { it >= 0 }
}
