package com.hardtekpt.crux.data

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ScheduleKind
import com.hardtekpt.crux.data.local.ScheduleSlotEntity
import com.hardtekpt.crux.data.local.ScheduleSlotRow
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One thing planned on a day of the week: a session plan, or climbing (at a place, maybe). */
data class ScheduleSlot(
    val id: Long,
    val day: DayOfWeek,
    val kind: ScheduleKind,
    val planId: Long? = null,
    val planName: String? = null,
    val placeId: Long? = null,
    val placeName: String? = null,
)

/** What a day of the week holds when the climber sets it. No plans and no climbing is rest. */
data class DayPlan(val planIds: List<Long> = emptyList(), val climbing: Boolean = false, val placeId: Long? = null)

/** The weekly schedule: the same week, repeating. */
interface ScheduleRepository {
    fun observeWeek(): Flow<List<ScheduleSlot>>

    /** Replaces what [day] holds. */
    suspend fun setDay(day: DayOfWeek, plan: DayPlan)

    /** How many days of the week a plan is on. */
    suspend fun daysWithPlan(planId: Long): Int
}

class OfflineScheduleRepository @Inject constructor(private val dbs: CruxDatabases) : ScheduleRepository {
    override fun observeWeek(): Flow<List<ScheduleSlot>> = dbs.observe { it.scheduleDao().observeAll() }.map { rows -> rows.map(ScheduleSlotRow::toModel) }

    override suspend fun setDay(day: DayOfWeek, plan: DayPlan) {
        val db = dbs.current()
        db.withTransaction {
            val dao = db.scheduleDao()
            dao.clearDay(day.value)
            val slots = plan.planIds.distinct().map { ScheduleSlotEntity(dayOfWeek = day.value, position = 0, kind = ScheduleKind.PLAN, templateId = it) } +
                listOfNotNull(
                    ScheduleSlotEntity(dayOfWeek = day.value, position = 0, kind = ScheduleKind.CLIMB, placeId = plan.placeId).takeIf {
                        plan.climbing
                    },
                )
            dao.insert(slots.mapIndexed { index, slot -> slot.copy(position = index) })
        }
    }

    override suspend fun daysWithPlan(planId: Long): Int = dbs.current().scheduleDao().countForPlan(planId)
}

internal fun ScheduleSlotRow.toModel() = ScheduleSlot(
    id = id,
    day = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
    kind = kind,
    planId = templateId,
    planName = planName,
    placeId = placeId,
    placeName = placeName,
)
