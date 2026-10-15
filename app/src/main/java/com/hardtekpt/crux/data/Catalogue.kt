package com.hardtekpt.crux.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A board catalogue's climbs: a board's whole climb list, imported from a Boardsesh snapshot file
 * the climber downloaded. They live in their own database file, apart from the climber's data, so
 * they are never in backups, exports or the copies made before a migration; a re-import brings
 * them back. Climbs are keyed by the catalogue ([catalogueKey], see [CatalogueKey]) and the board's
 * own climb id, which stays the same from one snapshot to the next.
 */
@Entity(tableName = "catalogue_climbs", primaryKeys = ["catalogueKey", "uuid"], indices = [Index("catalogueKey", "name")])
data class CatalogueClimbEntity(
    val catalogueKey: String,
    val uuid: String,
    val name: String,
    val setter: String? = null,
    val description: String? = null,
    /** The board's hold list ("p1145r12p1146r13…"), kept for hold diagrams. */
    val frames: String? = null,
    /** The angle the setter set it at, if the board has more than one. */
    val setterAngle: Int? = null,
    val createdAt: String? = null,
)

/**
 * A catalogue climb at one angle: its grade on the board's scale (10 is 4a, 22 is 7a; a fraction
 * is between two), whether it's a benchmark, how many have sent it and how it's rated (0 to 5).
 */
@Entity(tableName = "catalogue_grades", primaryKeys = ["catalogueKey", "uuid", "angle"])
data class CatalogueGradeEntity(
    val catalogueKey: String,
    val uuid: String,
    val angle: Int,
    val difficulty: Double? = null,
    val benchmark: Boolean = false,
    val ascents: Int = 0,
    val quality: Double? = null,
)

/** A catalogue climb with its grade at one angle, as lists show it. */
data class CatalogueRow(
    val uuid: String,
    val name: String,
    val setter: String?,
    val difficulty: Double?,
    val benchmark: Boolean?,
    val ascents: Int?,
    val quality: Double?,
)

@Dao
interface CatalogueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClimbs(climbs: List<CatalogueClimbEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrades(grades: List<CatalogueGradeEntity>)

    @Query("DELETE FROM catalogue_climbs WHERE catalogueKey = :key")
    suspend fun deleteClimbs(key: String)

    @Query("DELETE FROM catalogue_grades WHERE catalogueKey = :key")
    suspend fun deleteGrades(key: String)

    @Query("SELECT COUNT(*) FROM catalogue_climbs WHERE catalogueKey = :key")
    suspend fun count(key: String): Int

    @Query("SELECT DISTINCT angle FROM catalogue_grades WHERE catalogueKey = :key ORDER BY angle")
    suspend fun angles(key: String): List<Int>

    @Query("SELECT * FROM catalogue_climbs WHERE catalogueKey = :key AND uuid = :uuid")
    suspend fun climb(key: String, uuid: String): CatalogueClimbEntity?

    @Query("SELECT * FROM catalogue_climbs WHERE uuid = :uuid LIMIT 1")
    suspend fun climbAnywhere(uuid: String): CatalogueClimbEntity?

    @Query("SELECT * FROM catalogue_grades WHERE catalogueKey = :key AND uuid = :uuid ORDER BY angle")
    suspend fun grades(key: String, uuid: String): List<CatalogueGradeEntity>

    @Query("SELECT * FROM catalogue_climbs WHERE catalogueKey = :key AND uuid IN (:uuids)")
    suspend fun climbs(key: String, uuids: List<String>): List<CatalogueClimbEntity>

    /**
     * Climbs at [angle] whose name or setter has [query]: by most sent ([sort] 0), easiest first
     * (1) or name (2); benchmarks only when asked; at most [limit].
     */
    @Query(
        """
        SELECT c.uuid AS uuid, c.name AS name, c.setter AS setter, g.difficulty AS difficulty, g.benchmark AS benchmark,
               g.ascents AS ascents, g.quality AS quality
        FROM catalogue_climbs c JOIN catalogue_grades g ON g.catalogueKey = c.catalogueKey AND g.uuid = c.uuid AND g.angle = :angle
        WHERE c.catalogueKey = :key
          AND (:query = '' OR c.name LIKE '%' || :query || '%' OR c.setter LIKE '%' || :query || '%')
          AND (:benchmarks = 0 OR g.benchmark = 1)
          AND (:minDifficulty IS NULL OR g.difficulty >= :minDifficulty)
          AND (:maxDifficulty IS NULL OR g.difficulty < :maxDifficulty)
        ORDER BY
          CASE WHEN :sort = 0 THEN -g.ascents END,
          CASE WHEN :sort = 1 THEN g.difficulty END,
          c.name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun search(
        key: String,
        angle: Int,
        query: String,
        sort: Int,
        benchmarks: Boolean,
        minDifficulty: Double?,
        maxDifficulty: Double?,
        limit: Int,
    ): List<CatalogueRow>
}

@Database(entities = [CatalogueClimbEntity::class, CatalogueGradeEntity::class], version = 1, exportSchema = false)
abstract class CatalogueDatabase : RoomDatabase() {
    abstract fun catalogueDao(): CatalogueDao

    companion object {
        const val NAME = "crux-catalogue.db"
    }
}

/**
 * Which catalogue: "boardsesh:kilter:8:25" is Boardsesh's Kilter Homewall layout kept for size
 * 25 (10×12, Full ride). A MoonBoard has no size.
 */
object CatalogueKey {
    fun of(boardType: String, layoutId: Int, sizeId: Int?): String = listOfNotNull("boardsesh", boardType, layoutId, sizeId).joinToString(":")
}

/**
 * What Crux knows of Boardsesh's boards: which of its layouts is which board model, the product
 * size ids a Kilter size and holds are, and the board's difficulty numbers as Font grades.
 */
object BoardseshBoards {
    /** (board type, layout id) for each board Crux reads. */
    private val layouts = mapOf(
        ("kilter" to 1) to BoardModel.KILTER_ORIGINAL,
        ("kilter" to 8) to BoardModel.KILTER_HOMEWALL,
        ("moonboard" to 2) to BoardModel.MOON_2016,
        ("moonboard" to 4) to BoardModel.MOON_2017,
        ("moonboard" to 5) to BoardModel.MOON_2019,
        ("moonboard" to 3) to BoardModel.MOON_2024,
        ("moonboard" to 6) to BoardModel.MOON_MINI_2020,
        ("moonboard" to 7) to BoardModel.MOON_MINI_2025,
    )

    fun model(boardType: String, layoutId: Int): BoardModel? = layouts[boardType to layoutId]

    /**
     * The product sizes a Kilter board of [size] (and a Homewall's [holds]) is. An Original
     * 12×12 is either with or without its kickboard.
     */
    fun sizeIds(model: BoardModel, size: String?, holds: BoardHolds?): List<Int> = when (model) {
        BoardModel.KILTER_ORIGINAL -> when (size) {
            "7x10" -> listOf(14)
            "8x12" -> listOf(8)
            "12x12" -> listOf(10, 27)
            "16x12" -> listOf(28)
            else -> emptyList()
        }

        BoardModel.KILTER_HOMEWALL -> {
            val mainline = holds == BoardHolds.MAINLINE
            when (size) {
                "7x10" -> listOf(if (mainline) 18 else 17)
                "10x10" -> listOf(if (mainline) 22 else 21)
                "8x12" -> listOf(if (mainline) 24 else 23)
                "10x12" -> listOf(if (mainline) 26 else 25)
                else -> emptyList()
            }
        }

        else -> emptyList()
    }

    /**
     * A board difficulty as a Font grade's index: 16 is 6A, each step up a grade, and below that
     * 4a to 5c fold into Font's 4 to 5+. Fractions round to the nearest.
     */
    fun fontIndex(difficulty: Double): Int {
        val d = Math.round(difficulty).toInt()
        val grades = GradeScale.FONT.grades
        return when {
            d <= 11 -> grades.indexOf("4")
            d == 12 -> grades.indexOf("4+")
            d <= 14 -> grades.indexOf("5")
            d == 15 -> grades.indexOf("5+")
            else -> (d - 11).coerceAtMost(grades.lastIndex)
        }
    }

    /** The board difficulties a Font grade covers, for a grade filter: [from, until). */
    fun difficultyRange(fontIndex: Int): ClosedFloatingPointRange<Double> {
        val grades = GradeScale.FONT.grades
        val low = when (grades.getOrNull(fontIndex)) {
            "3", "4" -> 0.0
            "4+" -> 11.5
            "5" -> 12.5
            "5+" -> 14.5
            else -> fontIndex + 11 - 0.5
        }
        val high = when (grades.getOrNull(fontIndex)) {
            "3", "4" -> 11.5
            "4+" -> 12.5
            "5" -> 14.5
            "5+" -> 15.5
            else -> fontIndex + 11 + 0.5
        }
        return low..high
    }
}

/** A catalogue climb with its grade at every angle the board has it. */
data class CatalogueClimb(
    val uuid: String,
    val name: String,
    val setter: String?,
    val description: String?,
    val setterAngle: Int?,
    val grades: List<CatalogueGradeEntity>,
) {
    /** The grade at [angle] as a Font grade's index, or at the setter's angle. */
    fun fontIndex(angle: Int?): Int? = gradeAt(angle)?.difficulty?.let(BoardseshBoards::fontIndex)

    fun gradeAt(angle: Int?): CatalogueGradeEntity? =
        grades.firstOrNull { it.angle == angle } ?: grades.firstOrNull { it.angle == setterAngle } ?: grades.firstOrNull()

    /** As a dataset climb, graded at [angle], for Log climb and playlists. */
    fun asDatasetClimb(angle: Int?): DatasetClimb {
        val index = fontIndex(angle)
        return DatasetClimb(
            name = name,
            discipline = Discipline.BOULDER,
            gradeScale = GradeScale.FONT.takeIf { index != null },
            gradeIndex = index,
            setter = setter,
            note = description?.ifBlank { null },
            catalogueUuid = uuid,
        )
    }
}

/** Opens the catalogue database once; its climbs can always be imported again, so it never migrates. */
@Singleton
class CatalogueDatabases @Inject constructor(@ApplicationContext private val context: Context) {
    val db: CatalogueDatabase by lazy {
        Room.databaseBuilder(context, CatalogueDatabase::class.java, CatalogueDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    /** For tests: a database in memory. */
    internal var override: CatalogueDatabase? = null

    fun dao(): CatalogueDao = (override ?: db).catalogueDao()
}

/** Reads board catalogues; importing one is [CatalogueImporter]'s. */
class CatalogueRepository @Inject constructor(private val dbs: CatalogueDatabases) {
    suspend fun search(
        key: String,
        angle: Int,
        query: String = "",
        sort: Int = 0,
        benchmarks: Boolean = false,
        fontIndex: Int? = null,
        limit: Int = PAGE,
    ): List<CatalogueRow> {
        val range = fontIndex?.let(BoardseshBoards::difficultyRange)
        return dbs.dao().search(key, angle, query.trim(), sort, benchmarks, range?.start, range?.endInclusive, limit)
    }

    suspend fun angles(key: String): List<Int> = dbs.dao().angles(key)

    suspend fun climb(key: String, uuid: String): CatalogueClimb? {
        val dao = dbs.dao()
        val climb = dao.climb(key, uuid) ?: return null
        return CatalogueClimb(climb.uuid, climb.name, climb.setter, climb.description, climb.setterAngle, dao.grades(key, uuid))
    }

    /** The climb of [uuid] in any catalogue, with the key of the one it was found in. */
    suspend fun find(uuid: String): Pair<String, CatalogueClimb>? {
        val found = dbs.dao().climbAnywhere(uuid) ?: return null
        return climb(found.catalogueKey, uuid)?.let { found.catalogueKey to it }
    }

    suspend fun delete(key: String) {
        dbs.dao().deleteGrades(key)
        dbs.dao().deleteClimbs(key)
    }

    companion object {
        /** How many climbs a list shows at once; search narrows it. */
        const val PAGE = 200
    }
}
