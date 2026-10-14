package com.hardtekpt.crux.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hardtekpt.crux.data.backup.AutoBackups
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs one automatic backup; a failure is kept for Settings to show, and the schedule tries again next time. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, private val backups: AutoBackups) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (backups.backUp() == null) Result.success() else Result.failure()
}
