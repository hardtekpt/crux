package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

interface BodyRepository {
    /** Newest first. */
    fun observeWeights(): Flow<List<Measurement>>
    fun observeHeight(): Flow<Measurement?>
    suspend fun logWeight(kg: Double, date: LocalDate)
    suspend fun setHeight(cm: Double)
}

class OfflineBodyRepository @Inject constructor(
    private val dbs: CruxDatabases,
    private val clock: Clock,
) : BodyRepository {
    override fun observeWeights(): Flow<List<Measurement>> =
        dbs.observe { it.bodyMeasurementDao().observe(MeasurementType.WEIGHT) }.map { rows -> rows.map { it.toModel() } }

    override fun observeHeight(): Flow<Measurement?> =
        dbs.observe { it.bodyMeasurementDao().observe(MeasurementType.HEIGHT) }.map { rows -> rows.firstOrNull()?.toModel() }

    override suspend fun logWeight(kg: Double, date: LocalDate) {
        insert(MeasurementType.WEIGHT, kg, date)
    }

    override suspend fun setHeight(cm: Double) {
        insert(MeasurementType.HEIGHT, cm, LocalDate.now(clock))
    }

    private suspend fun insert(type: MeasurementType, value: Double, date: LocalDate) {
        dbs.current().bodyMeasurementDao().insert(
            BodyMeasurementEntity(
                type = type,
                value = value,
                dateEpochDay = date.toEpochDay(),
                createdAtMillis = clock.millis(),
            ),
        )
    }
}

private fun BodyMeasurementEntity.toModel() =
    Measurement(id = id, value = value, date = LocalDate.ofEpochDay(dateEpochDay))
