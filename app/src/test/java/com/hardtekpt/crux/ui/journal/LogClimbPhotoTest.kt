package com.hardtekpt.crux.ui.journal

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeClimbRepository
import com.hardtekpt.crux.data.FakeImageFiles
import com.hardtekpt.crux.data.FakePlaceRepository
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Photos on a logged climb: attached on save, and files that are swapped out get removed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LogClimbPhotoTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val climbs = FakeClimbRepository()
    private val files = FakeImageFiles()
    private val viewModel by lazy {
        LogClimbViewModel(
            SavedStateHandle(),
            climbs,
            FakePlaceRepository(climbs),
            FIXED_CLOCK,
            UserPreferencesRepository(PreferenceDataStoreFactory.create { java.io.File(tmp.root, "prefs.preferences_pb") }),
            files,
        )
    }

    @Test
    fun `the last photo picked is attached and the one it replaced is deleted`() = runBlocking {
        viewModel.attachImage(Uri.parse("content://photos/1"))
        withTimeout(5_000) { viewModel.draft.first { it.imagePath == "photo_0.jpg" } }
        viewModel.attachImage(Uri.parse("content://photos/2"))
        withTimeout(5_000) { viewModel.draft.first { it.imagePath == "photo_1.jpg" } }
        assertEquals(listOf("photo_0.jpg"), files.deleted)

        viewModel.save()
        withTimeout(5_000) { viewModel.draft.first { it.saved } }

        val id = climbs.climbs.value.single().id
        assertEquals("photo_1.jpg", climbs.images[id])
    }

    @Test
    fun `a video is attached alongside the photo and removing it before save leaves none`() = runBlocking {
        viewModel.attachImage(Uri.parse("content://photos/1"))
        viewModel.attachVideo(Uri.parse("content://videos/1"))
        withTimeout(5_000) { viewModel.draft.first { it.imagePath != null && it.videoPath != null } }
        viewModel.save()
        withTimeout(5_000) { viewModel.draft.first { it.saved } }
        val id = climbs.climbs.value.single().id
        assertEquals(true, climbs.videos[id]!!.endsWith(".mp4"))
        assertEquals(true, climbs.images[id]!!.endsWith(".jpg"))
    }
}
