package com.wenhao.record.data.tracking

object RawPointRetentionPolicy {
    fun canDeleteAnalyzedRawPoints(
        rawUploadedUpToPointId: Long,
        analyzedUpToPointId: Long,
    ): Boolean {
        return analyzedUpToPointId > 0L && rawUploadedUpToPointId >= analyzedUpToPointId
    }
}
