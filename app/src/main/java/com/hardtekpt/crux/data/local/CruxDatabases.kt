package com.hardtekpt.crux.data.local

import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Which data set the app is showing. */
enum class DataMode { REAL, DEMO }

/** Builds the database for a mode; the app uses files, tests use memory. */
fun interface DatabaseFactory {
    fun create(mode: DataMode): CruxDatabase
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The climber's own data and the demo data live in two separate databases. Demo mode only
 * changes which one the app reads and writes, so switching never touches the other.
 */
@Singleton
class CruxDatabases @Inject constructor(
    preferences: UserPreferencesRepository,
    private val factory: DatabaseFactory,
    @ApplicationScope scope: CoroutineScope,
) {
    val real: CruxDatabase by lazy { factory.create(DataMode.REAL) }
    val demo: CruxDatabase by lazy { factory.create(DataMode.DEMO) }

    fun forMode(mode: DataMode): CruxDatabase = if (mode == DataMode.DEMO) demo else real

    val mode: StateFlow<DataMode?> = preferences.demoMode
        .map { if (it) DataMode.DEMO else DataMode.REAL }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val active: Flow<CruxDatabase> = mode.filterNotNull().map(::forMode)

    /** Follows the active database: switching modes re-subscribes to the other one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(query: (CruxDatabase) -> Flow<T>): Flow<T> = active.flatMapLatest(query)

    /** The database to write to right now. */
    suspend fun current(): CruxDatabase = forMode(mode.filterNotNull().first())
}
