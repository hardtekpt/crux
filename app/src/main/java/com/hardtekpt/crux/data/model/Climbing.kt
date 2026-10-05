package com.hardtekpt.crux.data.model

enum class Discipline(val label: String) {
    BOULDER("Boulder"),
    ROUTE("Route");

    /** MVP: one fixed scale per discipline (Font for boulders, French for routes). */
    val scale: GradeScale
        get() = when (this) {
            BOULDER -> GradeScale.FONT
            ROUTE -> GradeScale.FRENCH
        }
}

/**
 * A grade scale is an ordered list, so a grade is stored as its scale plus an index:
 * it sorts and compares as a number and prints exactly as the climber picked it.
 */
enum class GradeScale(val grades: List<String>, val defaultIndex: Int) {
    FONT(
        grades = listOf(
            "3", "4", "4+", "5", "5+", "6A", "6A+", "6B", "6B+", "6C", "6C+",
            "7A", "7A+", "7B", "7B+", "7C", "7C+", "8A", "8A+", "8B", "8B+", "8C", "8C+", "9A",
        ),
        defaultIndex = 5,
    ),
    FRENCH(
        grades = listOf(
            "4a", "4b", "4c", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
            "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c", "8c+",
            "9a", "9a+", "9b", "9b+", "9c",
        ),
        defaultIndex = 6,
    );

    fun label(index: Int): String = grades[index.coerceIn(grades.indices)]
}

/** How the climb went. Everything but [ATTEMPT] is a send. */
enum class AscentStyle(val label: String, val isSend: Boolean, val singleAttempt: Boolean) {
    FLASH("Flash", isSend = true, singleAttempt = true),
    ONSIGHT("Onsight", isSend = true, singleAttempt = true),
    REDPOINT("Redpoint", isSend = true, singleAttempt = false),
    ATTEMPT("Attempt", isSend = false, singleAttempt = false);

    companion object {
        fun forDiscipline(discipline: Discipline): List<AscentStyle> = when (discipline) {
            // Onsight is a route idea; boulders are flashed or sent.
            Discipline.BOULDER -> listOf(FLASH, REDPOINT, ATTEMPT)
            Discipline.ROUTE -> listOf(ONSIGHT, FLASH, REDPOINT, ATTEMPT)
        }
    }
}

enum class MeasurementType(val unit: String) {
    WEIGHT("kg"),
    HEIGHT("cm"),
}
