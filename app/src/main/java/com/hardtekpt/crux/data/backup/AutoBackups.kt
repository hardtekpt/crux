package com.hardtekpt.crux.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.prefs.AutoBackupFrequency
import com.hardtekpt.crux.data.prefs.AutoBackupPrefs
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.work.AutoBackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Backups that write themselves: a full backup file (everything, with photos and videos) into a
 * folder the climber picked, daily or weekly, keeping the newest few. Only your own data, never
 * the demo set, and only into that folder: Crux holds no other storage access and sends nothing.
 */
@Singleton
class AutoBackups @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbs: CruxDatabases,
    private val clock: Clock,
    private val images: AreaImageStore,
    private val settings: BackupSettings,
    private val preferences: UserPreferencesRepository,
) {
    val state: Flow<AutoBackupPrefs> = preferences.autoBackup

    /**
     * Uses [uri], a folder from the system picker, keeping access to it across restarts; null
     * stops and gives the access back.
     */
    suspend fun setFolder(uri: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        preferences.autoBackup.first().folder?.let { old ->
            if (old != uri?.toString()) runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        if (uri != null) resolver.takePersistableUriPermission(uri, flags)
        preferences.setAutoBackupFolder(uri?.toString())
        schedule()
    }

    suspend fun setFrequency(frequency: AutoBackupFrequency) {
        preferences.setAutoBackupFrequency(frequency)
        schedule()
    }

    suspend fun setKeep(keep: Int) = preferences.setAutoBackupKeep(keep)

    /** Backs up now, in the background, whatever the schedule. */
    fun runNow() {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    /** Puts the schedule in step with the settings: a periodic job, or none. */
    suspend fun schedule() {
        val prefs = preferences.autoBackup.first()
        val work = WorkManager.getInstance(context)
        if (prefs.folder == null || prefs.frequency == AutoBackupFrequency.OFF) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(prefs.frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Writes one backup into the folder and drops the oldest beyond what's kept. Returns the error, if any. */
    suspend fun backUp(): String? = withContext(Dispatchers.IO) {
        val prefs = preferences.autoBackup.first()
        val tree = prefs.folder?.let(Uri::parse) ?: return@withContext "No folder picked"
        val error = runCatching {
            val resolver = context.contentResolver
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = PREFIX + STAMP.format(Instant.now(clock).atZone(ZoneId.systemDefault())) + ".zip"
            val file = DocumentsContract.createDocument(resolver, folder, "application/zip", name) ?: error("The folder isn't writable")
            // Your own data only: the demo set is never backed up.
            val repository = BackupRepository({ dbs.real }, clock, images, settings)
            resolver.openOutputStream(file, "w")?.use { out -> repository.export(BackupSection.entries.toSet(), out, BackupMedia()) }
                ?: error("The folder isn't writable")
            prune(tree, prefs.keep)
        }.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        preferences.recordAutoBackup(if (error == null) clock.millis() else null, error)
        error
    }

    /** Deletes Crux's automatic backups in the folder beyond the newest [keep]; nothing else is touched. */
    private fun prune(tree: Uri, keep: Int) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ours = mutableListOf<Pair<String, String>>()
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX) && name.endsWith(".zip")) ours += c.getString(0) to name
            }
        }
        // Names carry the time, so they sort oldest first.
        ours.sortedBy { it.second }.dropLast(keep).forEach { (id, _) ->
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        }
    }

    companion object {
        const val WORK = "auto_backup"
        const val PREFIX = "crux-auto-"
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
