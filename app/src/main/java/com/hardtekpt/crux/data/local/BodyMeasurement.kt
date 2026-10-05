package com.hardtekpt.crux.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.hardtekpt.crux.data.model.MeasurementType
import kotlinx.coroutines.flow.Flow

/** A body stat on a given day: weight in kg, height in cm. */
@Entity(tableName = "body_measurements", indices = [Index("type", "dateEpochDay")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MeasurementType,
    val value: Double,
    val dateEpochDay: Long,
    val createdAtMillis: Long,
)

@Dao
interface BodyMeasurementDao {
    @Query(
        "SELECT * FROM body_measurements WHERE type = :type " +
            "ORDER BY dateEpochDay DESC, createdAtMillis DESC",
    )
    fun observe(type: MeasurementType): Flow<List<BodyMeasurementEntity>>

    @Insert
    suspend fun insert(measurement: BodyMeasurementEntity): Long

    @Insert
    suspend fun insertAll(measurements: List<BodyMeasurementEntity>)
}
