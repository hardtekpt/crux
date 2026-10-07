package com.hardtekpt.crux.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The climber's database is copied aside before a migration, and only then. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseSnapshotsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val db by lazy { File(tmp.root, "crux-user.db") }
    private val dir by lazy { File(tmp.root, DatabaseSnapshots.DIR) }

    private fun createDatabase(version: Int) {
        SQLiteDatabase.openOrCreateDatabase(db, null).use { it.version = version }
    }

    @Test
    fun `an older database is copied before it is migrated`() {
        createDatabase(version = 15)

        val copy = DatabaseSnapshots.snapshot(db, dir, targetVersion = 16)

        assertEquals("crux-user-v15.db", copy?.name)
        assertEquals(15, SQLiteDatabase.openDatabase(copy!!.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version })
    }

    @Test
    fun `a current database, or none at all, is left alone`() {
        assertNull(DatabaseSnapshots.snapshot(db, dir, targetVersion = 16))
        createDatabase(version = 16)
        assertNull(DatabaseSnapshots.snapshot(db, dir, targetVersion = 16))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `the first copy of a version is kept when the app relaunches mid-migration`() {
        createDatabase(version = 15)
        val first = DatabaseSnapshots.snapshot(db, dir, targetVersion = 16)!!
        first.setLastModified(1_000)

        DatabaseSnapshots.snapshot(db, dir, targetVersion = 16)

        assertEquals(1_000, first.lastModified())
    }

    @Test
    fun `opening through the factory snapshots once, before the upgrade runs`() {
        val events = mutableListOf<String>()
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                events += "upgrade"
            }
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getDatabasePath("snapshot-order.db").also { it.parentFile?.mkdirs() }.let { file ->
            SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = 1 }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name("snapshot-order.db").callback(callback).build()
        val helper = SnapshotBeforeOpen(FrameworkSQLiteOpenHelperFactory()) { events += "snapshot" }.create(config)

        helper.writableDatabase
        helper.readableDatabase
        helper.close()

        assertEquals(listOf("snapshot", "upgrade"), events)
    }

    @Test
    fun `only the newest copies are kept`() {
        (10..14).forEach { version ->
            createDatabase(version)
            DatabaseSnapshots.snapshot(db, dir, targetVersion = 16, keep = 3)
        }

        val kept = dir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".db") }.sorted()
        assertEquals(listOf("crux-user-v12.db", "crux-user-v13.db", "crux-user-v14.db"), kept)
    }
}
