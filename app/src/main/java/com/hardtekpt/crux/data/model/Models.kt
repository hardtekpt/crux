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
    /** Local grades only: the label and tape colour as logged. */
    val gradeLabel: String? = null,
    val gradeColour: Long? = null,
    /** An attached photo and video, as file names in app storage. */
    val imagePath: String? = null,
    val videoPath: String? = null,
    /** The section of the place it was in, e.g. the place's Moonboard. */
    val sectionId: Long? = null,
    /** The live session it was logged in. */
    val sessionId: Long? = null,
) {
    val grade: String get() = gradeLabel(gradeScale, gradeIndex, gradeLabel)
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
    val gradeLabel: String? = null,
    val gradeColour: Long? = null,
    val sectionId: Long? = null,
    val sessionId: Long? = null,
)

data class PersonalBest(
    val discipline: Discipline,
    val style: AscentStyle,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val name: String?,
    val place: String?,
    val date: LocalDate,
    /** Local grades: the place they belong to, and the label as logged. */
    val placeId: Long? = null,
    val gradeLabel: String? = null,
    val gradeColour: Long? = null,
) {
    val grade: String get() = gradeLabel(gradeScale, gradeIndex, gradeLabel)
}

data class Measurement(val id: Long, val value: Double, val date: LocalDate)
