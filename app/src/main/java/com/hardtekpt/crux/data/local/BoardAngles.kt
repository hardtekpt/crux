package com.hardtekpt.crux.data.local

/**
 * Board angles stopped being their own field in schema 22. An angle logged on a climb lives on
 * in its note, and a wall's in its name, so nothing logged is lost.
 */
object BoardAngles {
    fun line(angle: Int) = "Board angle $angle°"

    /** [notes] with the angle's line added at the end. */
    fun intoNotes(notes: String?, angle: Int?): String? {
        if (angle == null) return notes
        val text = notes?.trim().orEmpty()
        return if (text.isEmpty()) line(angle) else "$text\n${line(angle)}"
    }

    /** A wall's [name] with its angle, unless the name already says it. */
    fun intoName(name: String, angle: Int?): String = if (angle == null || angle.toString() in name) name else "$name · $angle°"

    /** Schema 22: folds the angle columns into notes and names, then clears them. */
    fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE climbs SET notes = CASE WHEN notes IS NULL OR trim(notes) = '' THEN 'Board angle ' || angle || '°' " +
                "ELSE trim(notes) || char(10) || 'Board angle ' || angle || '°' END WHERE angle IS NOT NULL",
        )
        db.execSQL("UPDATE areas SET name = name || ' · ' || angle || '°' WHERE angle IS NOT NULL AND instr(name, CAST(angle AS TEXT)) = 0")
        db.execSQL("UPDATE climbs SET angle = NULL")
        db.execSQL("UPDATE areas SET angle = NULL")
        db.execSQL("UPDATE places SET defaultAngle = NULL")
    }
}
