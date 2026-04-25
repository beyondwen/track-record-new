package com.wenhao.record.data.diagnostics

import android.content.Context
import com.wenhao.record.data.tracking.TrackUploadScheduler
import java.util.UUID

object DiagnosticLogger {
    fun error(
        context: Context,
        source: String,
        message: String,
        fingerprint: String,
        payloadJson: String? = null,
    ) {
        enqueue(
            context = context,
            entry = DiagnosticLogEntry(
                logId = UUID.randomUUID().toString(),
                occurredAt = System.currentTimeMillis(),
                type = DiagnosticLogType.ERROR,
                severity = DiagnosticLogSeverity.ERROR,
                source = source,
                message = message,
                fingerprint = fingerprint,
                payloadJson = payloadJson,
            ),
        )
    }

    fun perfWarn(
        context: Context,
        source: String,
        message: String,
        fingerprint: String,
        payloadJson: String? = null,
    ) {
        enqueue(
            context = context,
            entry = DiagnosticLogEntry(
                logId = UUID.randomUUID().toString(),
                occurredAt = System.currentTimeMillis(),
                type = DiagnosticLogType.PERF_WARN,
                severity = DiagnosticLogSeverity.WARN,
                source = source,
                message = message,
                fingerprint = fingerprint,
                payloadJson = payloadJson,
            ),
        )
    }

    private fun enqueue(context: Context, entry: DiagnosticLogEntry) {
        runCatching {
            val appContext = context.applicationContext
            DiagnosticLogStore(appContext).enqueue(entry)
            TrackUploadScheduler.kickDiagnosticLogSync(appContext)
        }
    }
}
