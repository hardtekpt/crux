package com.hardtekpt.crux.data.model

import java.time.LocalDate

data class Climb(
    val id: Long,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val style: AscentStyle,
    val attempts: Int,
    val venue: Venue,
    val date: LocalDate,
    val name: String?,
    val place: String?,
    val notes: String?,
    val placeId: Long? = null,
    val areaId: Long? = null,
    val problemId: Long? = null,
    val angle: Int? = null,
    /** How hard it felt, 1 to 10. */
    val effort: Int? = null,
) {
    val grade: String get() = gradeScale.label(gradeIndex)
}

/** What the climber fills in on the Log climb form. */
data class NewClimb(
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val style: AscentStyle,
    val attempts: Int,
    val venue: Venue,
    val date: LocalDate,
    val name: String?,
    val place: String?,
    val notes: String?,
    val placeId: Long? = null,
    val areaId: Long? = null,
    val problemId: Long? = null,
    val angle: Int? = null,
    /** How hard it felt, 1 to 10. */
    val effort: Int? = null,
)

data class PersonalBest(
    val discipline: Discipline,
    val style: AscentStyle,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val name: String?,
    val place: String?,
    val date: LocalDate,
) {
    val grade: String get() = gradeScale.label(gradeIndex)
}

data class Measurement(
    val id: Long,
    val value: Double,
    val date: LocalDate,
)
