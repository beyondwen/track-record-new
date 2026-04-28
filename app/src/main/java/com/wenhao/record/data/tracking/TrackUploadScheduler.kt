package com.wenhao.record.data.tracking

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wenhao.record.data.diagnostics.DiagnosticLogUploadWorker
import com.wenhao.record.data.history.HistoryUploadWorker
import kotlin.reflect.KClass
import java.util.concurrent.TimeUnit

object TrackUploadPipelinePlan {
    val ONE_TIME_CHAIN: List<KClass<out androidx.work.ListenableWorker>> = listOf(
        RawPointUploadWorker::class,
        TodaySessionSyncWorker::class,
    )

    val LOCAL_RESULT_CHAIN: List<KClass<out androidx.work.ListenableWorker>> = emptyList()
}

object TrackUploadScheduler {
    private const val RAW_PERIODIC_WORK = "raw-point-upload-periodic"
    private const val LEGACY_HISTORY_PERIODIC_WORK = "history-upload-periodic"
    private const val LEGACY_PROCESSED_HISTORY_SYNC_PERIODIC_WORK = "processed-history-sync-periodic"
    private const val HISTORY_UPLOAD_PERIODIC_WORK = "history-upload-periodic-v2"
    private const val DIAGNOSTIC_LOG_PERIODIC_WORK = "diagnostic-log-upload-periodic"
    private const val RAW_ONE_TIME_WORK = "raw-point-upload-once"
    private const val LEGACY_ANALYSIS_ONE_TIME_WORK = "analysis-upload-once"
    private const val HISTORY_UPLOAD_ONE_TIME_WORK = "history-upload-once"
    private const val TODAY_SESSION_SYNC_ONE_TIME_WORK = "today-session-sync-once"
    private const val FULL_PIPELINE_ONE_TIME_WORK = "track-upload-pipeline-once"
    private const val LOCAL_RESULT_PIPELINE_ONE_TIME_WORK = "track-local-result-upload-pipeline-once"
    private const val DIAGNOSTIC_LOG_ONE_TIME_WORK = "diagnostic-log-upload-once"
    private const val LEGACY_MIRROR_RECOVERY_ONE_TIME_WORK = "track-mirror-recovery-once"

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val workManager = workManager(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        workManager.cancelUniqueWork(LEGACY_HISTORY_PERIODIC_WORK)
        workManager.cancelUniqueWork(LEGACY_PROCESSED_HISTORY_SYNC_PERIODIC_WORK)
        workManager.cancelUniqueWork(LEGACY_ANALYSIS_ONE_TIME_WORK)
        workManager.cancelUniqueWork(LOCAL_RESULT_PIPELINE_ONE_TIME_WORK)
        workManager.cancelUniqueWork(LEGACY_MIRROR_RECOVERY_ONE_TIME_WORK)

        workManager.enqueueUniquePeriodicWork(
            RAW_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RawPointUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            HISTORY_UPLOAD_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HistoryUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            DIAGNOSTIC_LOG_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DiagnosticLogUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
    }


    fun kickFullSyncPipeline(context: Context) {
        enqueueOneTimePipeline(
            context = context,
            uniqueWorkName = FULL_PIPELINE_ONE_TIME_WORK,
            requests = buildOneTimePipelineRequests(),
        )
    }

    fun buildOneTimePipelineRequests(): List<OneTimeWorkRequest> {
        return TrackUploadPipelinePlan.ONE_TIME_CHAIN.map { workerClass ->
            OneTimeWorkRequest.Builder(workerClass.java).build()
        }
    }

    private fun enqueueOneTimePipeline(
        context: Context,
        uniqueWorkName: String,
        requests: List<OneTimeWorkRequest>,
    ) {
        if (requests.isEmpty()) return

        val first = requests.first()
        val rest = requests.drop(1)
        val continuation = workManager(context.applicationContext).beginUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            first,
        )
        if (rest.isEmpty()) {
            continuation.enqueue()
        } else {
            continuation.then(rest).enqueue()
        }
    }

    fun kickRawPointSync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            RAW_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RawPointUploadWorker>().build(),
        )
    }

    fun kickHistorySync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            HISTORY_UPLOAD_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HistoryUploadWorker>().build(),
        )
    }

    fun kickTodaySessionSync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            TODAY_SESSION_SYNC_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TodaySessionSyncWorker>().build(),
        )
    }

    fun kickDiagnosticLogSync(context: Context) {
        workManager(context.applicationContext).enqueueUniqueWork(
            DIAGNOSTIC_LOG_ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DiagnosticLogUploadWorker>().build(),
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
