package com.hardtekpt.crux.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.gradeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every log belongs to a climb (schema 21). Climbs live in the `problems` table, logs in
 * `climbs`. This gives each log that has no climb yet one: the climb with the same name at the
 * same place (and discipline), else a new one named by the log, or by its grade and where it
 * was when the log has no name. Unnamed logs never merge; each is its own climb.
 *
 * Works on the raw database so the schema 21 migration, the demo seeder and backup import can
 * all use it. The log takes the climb's name, so lists show the same name everywhere.
 */
object ClimbLinks {
    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    private data class Key(val placeId: Long?, val name: String, val discipline: String)

    fun linkUnlinked(db: SupportSQLiteDatabase, nowMillis: Long) {
        val places = names(db, "SELECT id, name FROM places")
        val sections = names(db, "SELECT id, name FROM sections")
        val areas = names(db, "SELECT id, name FROM areas")
        val placeSections = mutableMapOf<Long, Int>()
        db.query("SELECT placeId, COUNT(*) FROM sections GROUP BY placeId").use { c ->
            while (c.moveToNext()) placeSections[c.getLong(0)] = c.getInt(1)
        }

        // The climbs there are, by place, name and discipline.
        val climbs = mutableMapOf<Key, Long>()
        db.query("SELECT id, placeId, name, discipline FROM problems").use { c ->
            while (c.moveToNext()) {
                val placeId = if (c.isNull(1)) null else c.getLong(1)
                climbs[Key(placeId, c.getString(2).trim().lowercase(), c.getString(3))] = c.getLong(0)
            }
        }

        data class Log(
            val id: Long,
            val name: String?,
            val placeId: Long?,
            val sectionId: Long?,
            val areaId: Long?,
            val discipline: String,
            val scale: String,
            val index: Int,
            val label: String?,
            val colour: Long?,
            val day: Long,
            val createdAt: Long,
        )
        val logs = mutableListOf<Log>()
        db.query(
            "SELECT id, name, placeId, sectionId, areaId, discipline, gradeScale, gradeIndex, gradeLabel, gradeColour, dateEpochDay, createdAtMillis " +
                "FROM climbs WHERE problemId IS NULL ORDER BY dateEpochDay, createdAtMillis, id",
        ).use { c ->
            fun long(i: Int) = if (c.isNull(i)) null else c.getLong(i)
            while (c.moveToNext()) {
                // Only links to rows that exist: a log can still name a place that's gone.
                logs += Log(
                    id = c.getLong(0),
                    name = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() },
                    placeId = long(2)?.takeIf { it in places },
                    sectionId = long(3)?.takeIf { it in sections },
                    areaId = long(4)?.takeIf { it in areas },
                    discipline = c.getString(5),
                    scale = c.getString(6),
                    index = c.getInt(7),
                    label = c.getString(8),
                    colour = long(9),
                    day = c.getLong(10),
                    createdAt = c.getLong(11),
                )
            }
        }

        for (log in logs) {
            val name = log.name ?: run {
                // Grade and where it was: the wall, else the facility when there are several, else the place.
                val grade = gradeLabel(GradeScale.valueOf(log.scale), log.index, log.label)
                val where = log.areaId?.let(areas::get)
                    ?: log.sectionId?.takeIf { (placeSections[log.placeId] ?: 0) > 1 }?.let(sections::get)
                    ?: log.placeId?.let(places::get)
                    ?: LocalDate.ofEpochDay(log.day).format(DAY)
                unique("$grade · $where") { candidate -> Key(log.placeId, candidate.lowercase(), log.discipline) in climbs }
            }
            val key = Key(log.placeId, name.lowercase(), log.discipline)
            val climbId = climbs[key] ?: db.insert(
                "problems",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                android.content.ContentValues().apply {
                    put("placeId", log.placeId)
                    put("sectionId", log.sectionId)
                    put("areaId", log.areaId)
                    put("name", name)
                    put("discipline", log.discipline)
                    put("gradeScale", log.scale)
                    put("gradeIndex", log.index)
                    put("gradeLabel", log.label)
                    put("gradeColour", log.colour)
                    put("retired", 0)
                    put("createdAtMillis", minOf(log.createdAt, nowMillis))
                },
            ).also { climbs[key] = it }
            db.execSQL("UPDATE climbs SET problemId = ?, name = ? WHERE id = ?", arrayOf<Any>(climbId, name, log.id))
        }
    }

    /**
     * A flash or onsight is a send on the very first go. Once logs share a climb, a first-go send
     * logged after earlier goes on it was really a redpoint.
     */
    fun settleStyles(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE climbs SET style = 'REDPOINT' WHERE style IN ('FLASH', 'ONSIGHT') AND problemId IS NOT NULL AND EXISTS (" +
                "SELECT 1 FROM climbs e WHERE e.problemId = climbs.problemId AND e.id != climbs.id AND " +
                "(e.dateEpochDay < climbs.dateEpochDay OR e.createdAtMillis < climbs.createdAtMillis))",
        )
    }

    /** [base], or [base] with the first free number after it. */
    fun unique(base: String, taken: (String) -> Boolean): String {
        if (!taken(base)) return base
        var n = 2
        while (taken("$base $n")) n++
        return "$base $n"
    }

    private fun names(db: SupportSQLiteDatabase, sql: String): Map<Long, String> = buildMap {
        db.query(sql).use { c -> while (c.moveToNext()) put(c.getLong(0), c.getString(1)) }
    }
}
