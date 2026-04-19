package com.wenhao.record.data.tracking

enum class UploadCursorType {
    RAW_POINT,
    ANALYSIS_SEGMENT,
}

data class UploadCursor(
    val type: UploadCursorType,
    val lastUploadedId: Long,
    val updatedAt: Long,
)
