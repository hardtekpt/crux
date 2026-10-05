package com.hardtekpt.crux.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.OfflineBodyRepository
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.OfflineTemplateRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.local.BodyMeasurementDao
import com.hardtekpt.crux.data.local.ClimbDao
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.TemplateDao
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CruxDatabase =
        Room.databaseBuilder(context, CruxDatabase::class.java, CruxDatabase.NAME)
            // MVP only: schema changes wipe local data. Real migrations start in phase 2;
            // exported schemas in app/schemas make that possible.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideClimbDao(db: CruxDatabase): ClimbDao = db.climbDao()

    @Provides
    fun provideBodyMeasurementDao(db: CruxDatabase): BodyMeasurementDao = db.bodyMeasurementDao()

    @Provides
    fun provideTemplateDao(db: CruxDatabase): TemplateDao = db.templateDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_prefs") }
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
}
