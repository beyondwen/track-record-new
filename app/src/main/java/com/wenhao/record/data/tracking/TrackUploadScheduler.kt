package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wenhao.record.data.history.HistoryUploadWorker
import java.util.concurrent.TimeUnit

object TrackUploadScheduler {
    private const val RAW_PERIODIC_WORK = "raw-point-upload-periodic"
    private const val ANALYSIS_PERIODIC_WORK = "analysis-upload-periodic"
    private const val HISTORY_PERIODIC_WORK = "history-upload-periodic"
    private const val RAW_ONE_TIME_WORK = "raw-point-upload-once"
    private const val ANALYSIS_ONE_TIME_WORK = "analysis-upload-once"
    private const val HISTORY_ONE_TIME_WORK = "history-upload-once"

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val workManager = workManager(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        workManager.enqueueUniquePeriodicWork(
            RAW_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RawPointUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            ANALYSIS_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AnalysisUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            HISTORY_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HistoryUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
    }

    fun kickRawPointSync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            RAW_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RawPointUploadWorker>().build(),
        )
    }

    fun kickAnalysisSync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            ANALYSIS_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AnalysisUploadWorker>().build(),
        )
    }

    fun kickHistorySync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            HISTORY_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HistoryUploadWorker>().build(),
        )
    }

    private fun workManager(appContext: Context): WorkManager {
        return try {
            WorkManager.getInstance(appContext)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(
                appContext,
                Configuration.Builder().build(),
            )
            WorkManager.getInstance(appContext)
        }
    }
}
