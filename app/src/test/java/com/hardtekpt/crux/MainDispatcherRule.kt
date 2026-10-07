package com.hardtekpt.crux

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/** Swaps Dispatchers.Main for a test dispatcher so viewModelScope works on the JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()

    /**
     * A DataStore that runs on the test dispatcher, so reads and writes finish before the call
     * returns instead of racing a wall-clock timeout on a slow CI runner.
     */
    fun preferencesDataStore(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(testDispatcher + SupervisorJob())) { file }
}
