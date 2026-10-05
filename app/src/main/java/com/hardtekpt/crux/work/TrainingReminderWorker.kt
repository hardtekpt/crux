package com.hardtekpt.crux.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Skeleton for scheduled training reminders. Notification posting is added with
 * the workouts feature; for now it only proves the Hilt + WorkManager wiring.
 */
@HiltWorker
class TrainingReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
