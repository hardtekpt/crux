package com.hardtekpt.crux.data.model

import java.time.LocalDate

data class Place(
    val id: Long,
    val name: String,
    val type: PlaceType,
    val location: String?,
    /** Null means "use my settings" for that discipline. */
    val boulderScale: GradeScale?,
    val routeScale: GradeScale?,
    val defaultAngle: Int?,
    val notes: String?,
) {
    fun scaleFor(discipline: Discipline): GradeScale? = when (discipline) {
        Discipline.BOULDER -> boulderScale
        Discipline.ROUTE -> routeScale
    }
}

data class Area(
    val id: Long,
    val placeId: Long,
    val name: String,
    val angle: Int?,
    val resetDate: LocalDate?,
)

data class Problem(
    val id: Long,
    val placeId: Long,
    val areaId: Long?,
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val tape: Int?,
    val setDate: LocalDate?,
    val retired: Boolean,
    val notes: String?,
) {
    val grade: String get() = gradeScale.label(gradeIndex)
}

/** Every go the climber logged on one problem, summed. */
data class ProblemStats(
    val sessions: Int,
    val attempts: Int,
    val firstSend: LocalDate?,
    val lastGo: LocalDate,
) {
    val sent: Boolean get() = firstSend != null
}

data class ProblemWithStats(val problem: Problem, val stats: ProblemStats?) {
    /** Tried but not sent yet. */
    val isProject: Boolean get() = stats != null && !stats.sent
}

data class PlaceSummary(
    val place: Place,
    val climbs: Int,
    val lastVisit: LocalDate?,
)

data class PlaceDetail(
    val place: Place,
    val areas: List<Area>,
    val problems: List<ProblemWithStats>,
)

/** An open project: a problem with goes but no send, and where it is. */
data class Project(
    val problem: Problem,
    val placeName: String,
    val areaName: String?,
    val stats: ProblemStats,
)
