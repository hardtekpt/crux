package com.hardtekpt.crux.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.OfflineExerciseRepository
import com.hardtekpt.crux.data.OfflineBodyRepository
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.OfflineTemplateRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.local.ApplicationScope
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.DataMode
import com.hardtekpt.crux.data.local.DatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

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
        val name = if (mode == DataMode.DEMO) CruxDatabase.DEMO_NAME else CruxDatabase.NAME
        Room.databaseBuilder(context, CruxDatabase::class.java, name)
            // Until real migrations land, schema changes wipe local data.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
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
    abstract fun bindBodyRepository(impl: OfflineBodyRepository): BodyRepository

    @Binds
    abstract fun bindTemplateRepository(impl: OfflineTemplateRepository): TemplateRepository

    @Binds
    abstract fun bindExerciseRepository(impl: OfflineExerciseRepository): ExerciseRepository
}
