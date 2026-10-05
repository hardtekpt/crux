package com.hardtekpt.crux.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.hardtekpt.crux.data.local.ClimbDao
import com.hardtekpt.crux.data.local.CruxDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.UUID
import javax.inject.Singleton

/** Gives every instrumented test a fresh in-memory database and its own DataStore file. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CruxDatabase =
        Room.inMemoryDatabaseBuilder(context, CruxDatabase::class.java).build()

    @Provides
    fun provideClimbDao(db: CruxDatabase): ClimbDao = db.climbDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("test_prefs_${UUID.randomUUID()}")
        }
}
