package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        climbs.value = climbs.value + Climb(
            id, climb.discipline, climb.gradeScale, climb.gradeIndex, climb.style,
            climb.attempts, climb.venue, climb.date, climb.name, climb.place, climb.notes,
        )
        return id
    }
}

class FakeBodyRepository : BodyRepository {
    val weights = MutableStateFlow<List<Measurement>>(emptyList())
    val height = MutableStateFlow<Measurement?>(null)

    override fun observeWeights(): Flow<List<Measurement>> = weights.map { list -> list.sortedByDescending { it.date } }
    override fun observeHeight(): Flow<Measurement?> = height
    override suspend fun logWeight(kg: Double, date: LocalDate) {
        weights.value = weights.value + Measurement(weights.value.size + 1L, kg, date)
    }
    override suspend fun setHeight(cm: Double) {
        height.value = Measurement(1, cm, LocalDate.now(FIXED_CLOCK))
    }
}

class FakeTemplateRepository : TemplateRepository {
    val templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
    override fun observeTemplates(): Flow<List<WorkoutTemplate>> = templates
    override fun observeTemplate(id: Long): Flow<WorkoutTemplate?> = templates.map { list -> list.find { it.id == id } }
}
