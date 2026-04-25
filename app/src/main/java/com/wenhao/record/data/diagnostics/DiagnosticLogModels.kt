package com.wenhao.record.data.diagnostics

enum class DiagnosticLogType {
    ERROR,
    PERF_WARN,
}

enum class DiagnosticLogSeverity {
    ERROR,
    WARN,
}

data class DiagnosticLogEntry(
    val logId: String,
    val occurredAt: Long,
    val type: DiagnosticLogType,
    val severity: DiagnosticLogSeverity,
    val source: String,
    val message: String,
    val fingerprint: String,
    val payloadJson: String? = null,
)

sealed interface DiagnosticLogUploadResult {
    data class Success(
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : DiagnosticLogUploadResult

    data class Failure(
        val message: String,
    ) : DiagnosticLogUploadResult
}
