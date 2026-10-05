package com.hardtekpt.crux.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.local.ClimbDao
import com.hardtekpt.crux.data.local.CruxDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CruxDatabase =
        Room.databaseBuilder(context, CruxDatabase::class.java, CruxDatabase.NAME).build()

    @Provides
    fun provideClimbDao(db: CruxDatabase): ClimbDao = db.climbDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_prefs") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindClimbRepository(impl: OfflineClimbRepository): ClimbRepository
}
