package com.hardtekpt.crux.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.DatabaseFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.UUID
import javax.inject.Singleton

/** Gives every instrumented test fresh in-memory databases and its own DataStore file. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabaseFactory(@ApplicationContext context: Context): DatabaseFactory = DatabaseFactory {
        Room.inMemoryDatabaseBuilder(context, CruxDatabase::class.java).build()
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Tests start in the app, past first-run setup.
        migrations = listOf(SetupDone),
    ) {
        context.preferencesDataStoreFile("test_prefs_${UUID.randomUUID()}")
    }

    private object SetupDone : androidx.datastore.core.DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences) = currentData[KEY] == null
        override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply { this[KEY] = true }
        override suspend fun cleanUp() = Unit
        private val KEY = androidx.datastore.preferences.core.booleanPreferencesKey("setup_done")
    }
}
