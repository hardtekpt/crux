package com.hardtekpt.crux.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File

/**
 * Before Room migrates the climber's database to a newer schema, the old file is copied to
 * `files/db-backups/`, so a migration that goes wrong can still be rolled back by hand.
 * Only the newest few copies are kept.
 */
object DatabaseSnapshots {
    const val DIR = "db-backups"
    private const val KEEP = 3
    private val SIDE_FILES = listOf("-wal", "-shm")

    /** Copies database [name] aside if it is older than [targetVersion]; returns the copy, if any. */
    fun beforeMigration(context: Context, name: String, targetVersion: Int): File? =
        snapshot(context.getDatabasePath(name), File(context.filesDir, DIR), targetVersion)

    internal fun snapshot(db: File, dir: File, targetVersion: Int, keep: Int = KEEP): File? {
        if (!db.exists()) return null
        // Read-only, so the version also reflects anything still in the write-ahead log.
        val version = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        if (version == 0 || version >= targetVersion) return null

        dir.mkdirs()
        val base = db.name.removeSuffix(".db")
        val copy = File(dir, "$base-v$version.db")
        // One copy per old version: relaunching before the migration finishes keeps the first.
        if (!copy.exists()) {
            db.copyTo(copy)
            SIDE_FILES.forEach { suffix ->
                File(db.path + suffix).takeIf { it.exists() }?.copyTo(File(copy.path + suffix), overwrite = true)
            }
        }
        prune(dir, base, keep)
        return copy
    }

    private fun prune(dir: File, base: String, keep: Int) {
        val pattern = Regex("""${Regex.escape(base)}-v(\d+)\.db""")
        dir.listFiles().orEmpty()
            .mapNotNull { file -> pattern.matchEntire(file.name)?.let { it.groupValues[1].toInt() to file } }
            .sortedByDescending { it.first }
            .drop(keep)
            .forEach { (_, file) ->
                file.delete()
                SIDE_FILES.forEach { File(file.path + it).delete() }
            }
    }
}

/**
 * Takes the snapshot the first time Room opens the database: on Room's background thread rather
 * than the main one, and always before Room runs a migration.
 */
class SnapshotBeforeOpen(private val delegate: SupportSQLiteOpenHelper.Factory, private val snapshot: () -> Unit) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper = Helper(delegate.create(configuration))

    private inner class Helper(private val helper: SupportSQLiteOpenHelper) : SupportSQLiteOpenHelper by helper {
        private var snapshotTaken = false

        override val writableDatabase: SupportSQLiteDatabase
            get() = helper.also { snapshotOnce() }.writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = helper.also { snapshotOnce() }.readableDatabase

        private fun snapshotOnce() = synchronized(this) {
            if (snapshotTaken) return@synchronized
            snapshotTaken = true
            // A failed copy never stops the climber's data from opening.
            runCatching(snapshot).onFailure { Log.w("CruxDatabase", "Couldn't copy the database before migrating", it) }
        }
    }
}
