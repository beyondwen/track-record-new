package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TrackUploadScheduler {
    private const val RAW_PERIODIC_WORK = "raw-point-upload-periodic"
    private const val ANALYSIS_PERIODIC_WORK = "analysis-upload-periodic"
    private const val RAW_ONE_TIME_WORK = "raw-point-upload-once"
    private const val ANALYSIS_ONE_TIME_WORK = "analysis-upload-once"

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            RAW_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RawPointUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            ANALYSIS_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AnalysisUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
    }

    fun kickRawPointSync(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RAW_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RawPointUploadWorker>().build(),
        )
    }

    fun kickAnalysisSync(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ANALYSIS_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AnalysisUploadWorker>().build(),
        )
    }
}
