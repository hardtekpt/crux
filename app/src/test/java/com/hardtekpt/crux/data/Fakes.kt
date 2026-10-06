package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.data.model.ProblemStats
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Project
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Monday 5 October 2026, noon UTC. */
val FIXED_CLOCK: Clock = Clock.fixed(
    LocalDate.of(2026, 10, 5).atTime(12, 0).toInstant(ZoneOffset.UTC),
    ZoneOffset.UTC,
)

class FakeClimbRepository : ClimbRepository {
    val climbs = MutableStateFlow<List<Climb>>(emptyList())
    val logged = mutableListOf<NewClimb>()

    override fun observeClimbs(): Flow<List<Climb>> = climbs.map { list -> list.sortedByDescending { it.date } }
    override fun observeRecentClimbs(limit: Int): Flow<List<Climb>> = observeClimbs().map { it.take(limit) }
    override fun observeClimbsSince(from: LocalDate): Flow<List<Climb>> =
        climbs.map { list -> list.filter { !it.date.isBefore(from) } }
    override fun observeClimbCount(): Flow<Int> = climbs.map { it.size }

    override fun observePersonalBests(): Flow<List<PersonalBest>> = climbs.map { list ->
        list.filter { it.style.isSend }
            .groupBy { Triple(it.discipline, it.gradeScale, it.style) }
            .values
            .map { group ->
                val best = group.maxBy { it.gradeIndex }
                PersonalBest(best.discipline, best.style, best.gradeScale, best.gradeIndex, best.name, best.place, best.date)
            }
    }

    override suspend fun logClimb(climb: NewClimb): Long {
        logged += climb
        val id = (climbs.value.maxOfOrNull { it.id } ?: 0) + 1
        climbs.value = climbs.value + climb.toClimb(id)
        return id
    }

    override suspend fun getClimb(id: Long): Climb? = climbs.value.find { it.id == id }

    override suspend fun updateClimb(id: Long, climb: NewClimb) {
        climbs.value = climbs.value.map { if (it.id == id) climb.toClimb(id) else it }
    }

    val images = mutableMapOf<Long, String>()
    val videos = mutableMapOf<Long, String>()

    override suspend fun setClimbMedia(climbId: Long, kind: com.hardtekpt.crux.data.local.MediaKind, path: String?): String? {
        val store = if (kind == com.hardtekpt.crux.data.local.MediaKind.IMAGE) images else videos
        val old = store[climbId]
        if (path == null) store.remove(climbId) else store[climbId] = path
        return old
    }

    override suspend fun deleteClimb(id: Long) {
        climbs.value = climbs.value.filterNot { it.id == id }
    }

    override fun observeClimbsForProblem(problemId: Long): Flow<List<Climb>> =
        observeClimbs().map { list -> list.filter { it.problemId == problemId } }

    override fun observeClimbsAtPlace(placeId: Long): Flow<List<Climb>> =
        observeClimbs().map { list -> list.filter { it.placeId == placeId } }

    private fun NewClimb.toClimb(id: Long) = Climb(
        id, discipline, gradeScale, gradeIndex, style, attempts, venue, date, name, place, notes,
        placeId, areaId, problemId, angle, effort, gradeLabel, gradeColour,
    )
}

/** Places, walls and problems held in memory; stats come from the fake climbs when given. */
class FakePlaceRepository(private val climbs: FakeClimbRepository? = null) : PlaceRepository {
    val places = MutableStateFlow<List<Place>>(emptyList())
    val areas = MutableStateFlow<List<Area>>(emptyList())
    val problems = MutableStateFlow<List<Problem>>(emptyList())
    private var nextId = 1L

    private fun stats(problemId: Long, all: List<Climb>): ProblemStats? {
        val goes = all.filter { it.problemId == problemId }
        if (goes.isEmpty()) return null
        return ProblemStats(
            sessions = goes.map { it.date }.distinct().size,
            attempts = goes.sumOf { it.attempts },
            firstSend = goes.filter { it.style.isSend }.minOfOrNull { it.date },
            lastGo = goes.maxOf { it.date },
        )
    }

    private val allClimbs: Flow<List<Climb>> = climbs?.climbs ?: MutableStateFlow(emptyList())

    override fun observePlaces(): Flow<List<PlaceSummary>> = combine(places, allClimbs) { list, climbs ->
        list.map { place ->
            val here = climbs.filter { it.placeId == place.id }
            PlaceSummary(place, here.size, here.maxOfOrNull { it.date })
        }
    }

    override fun observePlaceDetail(id: Long): Flow<PlaceDetail?> = combine(places, areas, problems, allClimbs) { p, a, pr, c ->
        p.find { it.id == id }?.let { place ->
            PlaceDetail(place, a.filter { it.placeId == id }, pr.filter { it.placeId == id }.map { ProblemWithStats(it, stats(it.id, c)) })
        }
    }

    override fun observeProblem(id: Long): Flow<ProblemWithStats?> = combine(problems, allClimbs) { pr, c ->
        pr.find { it.id == id }?.let { ProblemWithStats(it, stats(it.id, c)) }
    }

    override fun observeProjects(): Flow<List<Project>> = combine(places, areas, problems, allClimbs) { p, a, pr, c ->
        pr.filter { !it.retired }.mapNotNull { problem ->
            val stats = stats(problem.id, c)?.takeIf { !it.sent } ?: return@mapNotNull null
            Project(problem, p.find { it.id == problem.placeId }?.name.orEmpty(), a.find { it.id == problem.areaId }?.name, stats)
        }.sortedByDescending { it.stats.lastGo }
    }

    override suspend fun getPlace(id: Long): Place? = places.value.find { it.id == id }
    override suspend fun getProblem(id: Long): Problem? = problems.value.find { it.id == id }

    override suspend fun savePlace(input: PlaceInput): Long {
        val id = input.id.takeIf { it != 0L } ?: nextId++
        val place = Place(id, input.name, input.types.first(), input.location, input.boulderScale, input.routeScale, input.defaultAngle, input.notes, input.localScale, types = input.types)
        places.value = places.value.filterNot { it.id == id } + place
        return id
    }

    override suspend fun deletePlace(id: Long) {
        places.value = places.value.filterNot { it.id == id }
        areas.value = areas.value.filterNot { it.placeId == id }
        problems.value = problems.value.filterNot { it.placeId == id }
    }

    override suspend fun saveArea(placeId: Long, areaId: Long, name: String, angle: Int?, imagePath: String?, type: com.hardtekpt.crux.data.model.PlaceType?): Long {
        val id = areaId.takeIf { it != 0L } ?: nextId++
        areas.value = areas.value.filterNot { it.id == id } + Area(id, placeId, name, angle, null, imagePath, type)
        return id
    }

    override suspend fun deleteArea(id: Long) {
        areas.value = areas.value.filterNot { it.id == id }
        problems.value = problems.value.map { if (it.areaId == id) it.copy(areaId = null) else it }
    }

    override suspend fun resetArea(id: Long) {
        areas.value = areas.value.map { if (it.id == id) it.copy(resetDate = LocalDate.now(FIXED_CLOCK)) else it }
        problems.value = problems.value.map { if (it.areaId == id) it.copy(retired = true) else it }
    }

    override suspend fun saveProblem(input: ProblemInput): Long {
        val id = input.id.takeIf { it != 0L } ?: nextId++
        val old = problems.value.find { it.id == id }
        val problem = Problem(
            id, input.placeId, input.areaId, input.name, input.discipline, input.gradeScale, input.gradeIndex,
            input.tape, old?.setDate ?: LocalDate.now(FIXED_CLOCK), old?.retired ?: false, input.notes,
            input.gradeLabel, input.gradeColour,
        )
        problems.value = problems.value.filterNot { it.id == id } + problem
        return id
    }

    override suspend fun setRetired(problemId: Long, retired: Boolean) {
        problems.value = problems.value.map { if (it.id == problemId) it.copy(retired = retired) else it }
    }

    override suspend fun deleteProblem(id: Long) {
        problems.value = problems.value.filterNot { it.id == id }
    }
}

class FakeBodyRepository : BodyRepository {
    val weights = MutableStateFlow<List<Measurement>>(emptyList())

    override fun observeWeights(): Flow<List<Measurement>> = weights.map { list -> list.sortedByDescending { it.date } }
    val latest = MutableStateFlow<Map<MeasurementType, Measurement>>(emptyMap())
    override fun observeLatest(): Flow<Map<MeasurementType, Measurement>> = latest
    override suspend fun logWeight(kg: Double, date: LocalDate) {
        weights.value = weights.value + Measurement(weights.value.size + 1L, kg, date)
    }
    override suspend fun setMeasurement(type: MeasurementType, value: Double) {
        latest.value = latest.value + (type to Measurement(1, value, LocalDate.now(FIXED_CLOCK)))
    }
}

class FakeTemplateRepository : TemplateRepository {
    val templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
    override fun observeTemplates(): Flow<List<WorkoutTemplate>> = templates
    override fun observeTemplate(id: Long): Flow<WorkoutTemplate?> = templates.map { list -> list.find { it.id == id } }
    override suspend fun getTemplate(id: Long): WorkoutTemplate? = templates.value.find { it.id == id }
    override suspend fun saveTemplate(template: WorkoutTemplate): Long {
        val id = if (template.id == 0L) (templates.value.maxOfOrNull { it.id } ?: 0) + 1 else template.id
        templates.value = templates.value.filterNot { it.id == id } + template.copy(id = id)
        return id
    }
    override suspend fun deleteTemplate(id: Long) {
        templates.value = templates.value.filterNot { it.id == id }
    }
}

class FakeExerciseRepository : ExerciseRepository {
    val exercises = MutableStateFlow<List<Exercise>>(emptyList())
    override fun observeExercises(): Flow<List<Exercise>> = exercises
    override suspend fun getExercise(id: Long): Exercise? = exercises.value.find { it.id == id }
    override suspend fun saveExercise(input: ExerciseInput): Long {
        val id = if (input.id == 0L) (exercises.value.maxOfOrNull { it.id } ?: 0) + 1 else input.id
        exercises.value = exercises.value.filterNot { it.id == id } +
            Exercise(id, input.name.trim(), input.category, input.metric, input.notes?.takeIf { it.isNotBlank() })
        return id
    }
    override suspend fun planCount(id: Long): Int = 0
    override suspend fun deleteExercise(id: Long) {
        exercises.value = exercises.value.filterNot { it.id == id }
    }
}

/** Image files without Android storage: imports name the file after the uri, deletes are recorded. */
class FakeImageFiles : com.hardtekpt.crux.data.images.ImageFiles {
    val deleted = mutableListOf<String>()
    var next = 0

    override suspend fun importFrom(uri: android.net.Uri): String = "photo_${next++}.jpg"
    override suspend fun delete(name: String?) {
        if (name != null) deleted += name
    }
    override fun newCaptureUri(): android.net.Uri = throw UnsupportedOperationException()
    override suspend fun importVideo(uri: android.net.Uri): String = "video_${next++}.mp4"
    override fun newVideoCaptureUri(): android.net.Uri = throw UnsupportedOperationException()
}
