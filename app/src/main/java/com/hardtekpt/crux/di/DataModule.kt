package com.hardtekpt.crux.di

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.hardtekpt.crux.BuildConfig
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.OfflineBodyRepository
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.OfflineExerciseRepository
import com.hardtekpt.crux.data.OfflinePlaceRepository
import com.hardtekpt.crux.data.OfflineTemplateRepository
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.diagnostics.CrashReports
import com.hardtekpt.crux.data.local.ApplicationScope
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.DataMode
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.local.DatabaseSnapshots
import com.hardtekpt.crux.data.local.SnapshotBeforeOpen
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * The climber's own data and the demo data are separate files. The demo set keeps the
     * old `crux.db`, which already held the starter and sample data, so nothing is lost.
     */
    @Provides
    @Singleton
    fun provideDatabaseFactory(@ApplicationContext context: Context): DatabaseFactory = DatabaseFactory { mode ->
        val builder = when (mode) {
            // Demo data can be seeded again, so a schema it can't migrate is simply rebuilt.
            DataMode.DEMO -> Room.databaseBuilder(context, CruxDatabase::class.java, CruxDatabase.DEMO_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)

            // The climber's own data is never wiped: a missing migration fails loudly instead,
            // and the old file is copied aside before any migration runs.
            DataMode.REAL -> Room.databaseBuilder(context, CruxDatabase::class.java, CruxDatabase.NAME)
                .openHelperFactory(
                    SnapshotBeforeOpen(FrameworkSQLiteOpenHelperFactory()) {
                        DatabaseSnapshots.beforeMigration(context, CruxDatabase.NAME, CruxDatabase.VERSION)
                    },
                )
        }
        builder.build()
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_prefs") }
}

@Module
@InstallIn(SingletonComponent::class)
object AndroidModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideCrashReports(@ApplicationContext context: Context, clock: Clock): CrashReports = CrashReports(
        dir = File(context.filesDir, CrashReports.DIR),
        clock = clock,
        about = "App: Crux ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
    )
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindClimbRepository(impl: OfflineClimbRepository): ClimbRepository

    @Binds
    abstract fun bindNoteRepository(impl: com.hardtekpt.crux.data.OfflineNoteRepository): com.hardtekpt.crux.data.NoteRepository

    @Binds
    abstract fun bindRecordRepository(impl: com.hardtekpt.crux.data.OfflineRecordRepository): com.hardtekpt.crux.data.RecordRepository

    @Binds
    abstract fun bindImageFiles(impl: com.hardtekpt.crux.data.images.AreaImageStore): com.hardtekpt.crux.data.images.ImageFiles

    @Binds
    abstract fun bindBodyRepository(impl: OfflineBodyRepository): BodyRepository

    @Binds
    abstract fun bindTemplateRepository(impl: OfflineTemplateRepository): TemplateRepository

    @Binds
    abstract fun bindExerciseRepository(impl: OfflineExerciseRepository): ExerciseRepository

    @Binds
    abstract fun bindSessionRepository(impl: com.hardtekpt.crux.data.OfflineSessionRepository): com.hardtekpt.crux.data.SessionRepository

    @Binds
    abstract fun bindPlaceRepository(impl: OfflinePlaceRepository): PlaceRepository
}
