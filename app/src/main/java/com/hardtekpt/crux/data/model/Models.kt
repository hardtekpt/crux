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

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val description: String,
    val estimatedMinutes: Int,
    val blocks: List<TemplateBlock>,
) {
    val exerciseCount: Int get() = blocks.sumOf { it.exercises.size }
}

data class TemplateBlock(
    val name: String,
    val exercises: List<TemplateExercise>,
)

data class TemplateExercise(
    val name: String,
    val target: String,
    val rest: String?,
)
