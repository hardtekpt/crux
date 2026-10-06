package com.hardtekpt.crux.data.model

enum class Discipline(val label: String) {
    BOULDER("Boulder"),
    ROUTE("Route");

    /** The scales a climber can pick for this discipline in Settings; the first is the default. */
    val scales: List<GradeScale>
        get() = GradeScale.entries.filter { it.discipline == this && !it.isLocal }

    /** Marks a grade as one of a place's own; the place holds the actual list. */
    val localScale: GradeScale
        get() = if (this == BOULDER) GradeScale.LOCAL_BOULDER else GradeScale.LOCAL_ROUTE

    val defaultScale: GradeScale get() = scales.first()
}

/**
 * A grade scale is an ordered list, so a grade is stored as its scale plus an index:
 * it sorts and compares as a number and prints exactly as the climber picked it.
 */
enum class GradeScale(
    val label: String,
    val discipline: Discipline,
    val grades: List<String>,
    val defaultIndex: Int,
) {
    FONT(
        label = "Font",
        discipline = Discipline.BOULDER,
        grades = listOf(
            "3", "4", "4+", "5", "5+", "6A", "6A+", "6B", "6B+", "6C", "6C+",
            "7A", "7A+", "7B", "7B+", "7C", "7C+", "8A", "8A+", "8B", "8B+", "8C", "8C+", "9A",
        ),
        defaultIndex = 5,
    ),
    V_SCALE(
        label = "V scale",
        discipline = Discipline.BOULDER,
        grades = listOf("VB") + (0..17).map { "V$it" },
        defaultIndex = 4,
    ),
    FRENCH(
        label = "French",
        discipline = Discipline.ROUTE,
        grades = listOf(
            "4a", "4b", "4c", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
            "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c", "8c+",
            "9a", "9a+", "9b", "9b+", "9c",
        ),
        defaultIndex = 6,
    ),
    YDS(
        label = "YDS",
        discipline = Discipline.ROUTE,
        grades = listOf("5.5", "5.6", "5.7", "5.8", "5.9") +
            (10..15).flatMap { n -> listOf("a", "b", "c", "d").map { "5.$n$it" } },
        defaultIndex = 5,
    ),

    /** A place's own grades (numbers or colour tapes); the list lives on the place. */
    LOCAL_BOULDER(label = "Local", discipline = Discipline.BOULDER, grades = emptyList(), defaultIndex = 0),
    LOCAL_ROUTE(label = "Local", discipline = Discipline.ROUTE, grades = emptyList(), defaultIndex = 0),
    ;

    val isLocal: Boolean get() = grades.isEmpty()

    fun label(index: Int): String = if (isLocal) "#${index + 1}" else grades[index.coerceIn(grades.indices)]
}

/** Where the climb happened: indoors on plastic or outdoors on rock. */
enum class Venue(val label: String) {
    GYM("Gym"),
    CRAG("Crag"),
    BOARD("Board"),
}

/** The kinds of place a climber logs at. Names match [Venue] so climbs map across. */
enum class PlaceType(val label: String, val areaLabel: String) {
    GYM("Gym", "Wall"),
    CRAG("Crag", "Sector"),
    BOARD("Board", "Set"),
    ;

    val venue: Venue get() = Venue.valueOf(name)
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

/**
 * Body stats a climber tracks. Stored by name, so adding a type needs no database change.
 * [range] is what the entry form accepts.
 */
enum class MeasurementType(
    val label: String,
    val unit: String,
    val range: ClosedFloatingPointRange<Double>,
    val description: String,
) {
    WEIGHT("Weight", "kg", 20.0..300.0, "Bodyweight"),
    HEIGHT("Height", "cm", 100.0..250.0, "Standing height, barefoot"),
    WINGSPAN("Wingspan", "cm", 100.0..260.0, "Fingertip to fingertip, arms straight out"),
    STANDING_REACH("Standing reach", "cm", 150.0..320.0, "Highest point you touch flat-footed, one arm up"),
    BODY_FAT("Body fat", "%", 3.0..60.0, "From a scale or calipers"),
    // Circumferences, measured relaxed with a soft tape.
    FOREARM("Forearm", "cm", 15.0..60.0, "Widest point, arm relaxed"),
    BICEP("Bicep", "cm", 15.0..70.0, "Widest point, arm relaxed"),
    CHEST("Chest", "cm", 60.0..160.0, "Across the nipples, breathing out"),
    WAIST("Waist", "cm", 50.0..160.0, "At the navel, relaxed"),
    THIGH("Thigh", "cm", 30.0..100.0, "Widest point, standing"),
    ;

    companion object {
        /** The climbing body stats on You, in display order. Weight has its own card. */
        val bodyStats = listOf(HEIGHT, WINGSPAN, STANDING_REACH, BODY_FAT)
    }
}
