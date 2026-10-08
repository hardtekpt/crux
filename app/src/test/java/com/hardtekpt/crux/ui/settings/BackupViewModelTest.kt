package com.hardtekpt.crux.ui.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.NoteEntity
import com.hardtekpt.crux.data.backup.BackupRepository
import com.hardtekpt.crux.data.backup.BackupSection
import com.hardtekpt.crux.data.backup.Resolution
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The import flow: pick a file, choose sections, then answer each record that is already here. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CruxDatabase::class.java).allowMainThreadQueries().build()
    private val repository = BackupRepository({ db }, FIXED_CLOCK, AreaImageStore(context))

    @After
    fun close() = db.close()

    private fun viewModel() = BackupViewModel(
        repository,
        UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb"))),
        context.contentResolver,
        FIXED_CLOCK,
    )

    /** Backup steps run on the IO dispatcher; wait for the one in flight to finish. */
    private suspend fun BackupViewModel.settle() = withContext(Dispatchers.Default) {
        withTimeout(10_000) { state.first { !it.busy } }
    }

    /** A backup of everything, picked for import into the same data, with only [sections] switched on. */
    private suspend fun picked(vararg sections: BackupSection): BackupViewModel {
        StarterDataSeeder(FIXED_CLOCK).seed(db, includeSampleData = true)
        db.noteDao().insert(NoteEntity(text = "Beta", createdAtMillis = 1, updatedAtMillis = 1))
        val file = tmp.newFile("backup.zip")
        file.outputStream().use { repository.export(BackupSection.entries.toSet(), it) }
        return viewModel().apply {
            pickedImport(Uri.fromFile(file))
            settle()
            assertNotNull(state.value.message, state.value.pendingImport)
            // Settings replace the climber's own, so they start switched off.
            assertFalse(BackupSection.SETTINGS in state.value.importSections)
            (state.value.importSections - sections.toSet()).forEach(::toggleImport)
        }
    }

    @Test
    fun `each record already here is asked about, and the answer can cover the rest of its section`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = picked(BackupSection.JOURNAL, BackupSection.NOTES)
        val climbs = db.climbDao().count()
        val notes = db.noteDao().getAll().size

        viewModel.confirmImport()
        viewModel.settle()

        val first = viewModel.state.value.conflict!!
        assertEquals(BackupSection.JOURNAL, first.section)
        assertEquals(climbs + notes, viewModel.state.value.conflicts.size)
        assertEquals(climbs - 1, viewModel.state.value.othersInSection)

        // One answer for this climb only, then one for every other climb.
        viewModel.resolve(Resolution.SKIP, forOthers = false)
        assertEquals(climbs - 2, viewModel.state.value.othersInSection)
        viewModel.resolve(Resolution.KEEP_BOTH, forOthers = true)
        assertEquals(BackupSection.NOTES, viewModel.state.value.conflict!!.section)
        assertEquals(climbs, db.climbDao().count())

        // The last answer runs the import.
        viewModel.resolve(Resolution.SKIP, forOthers = true)
        viewModel.settle()

        assertNull(viewModel.state.value.conflict)
        assertEquals(climbs + climbs - 1, db.climbDao().count())
        assertEquals(notes, db.noteDao().getAll().size)
        assertEquals("Nothing new imported. Kept ${climbs - 1} as copies, skipped ${notes + 1} already here.", viewModel.state.value.message)
    }

    @Test
    fun `cancelling while answering imports nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = picked(BackupSection.NOTES)
        val notes = db.noteDao().getAll().size
        viewModel.confirmImport()
        viewModel.settle()
        assertNotNull(viewModel.state.value.conflict)

        viewModel.cancelImport()

        assertNull(viewModel.state.value.conflict)
        assertTrue(viewModel.state.value.decisions.isEmpty())
        assertEquals(notes, db.noteDao().getAll().size)
    }
}
