package com.wenhao.record.data.history

data class HistoryBatchProgress(
    val batchIndex: Int,
    val totalBatches: Int,
    val batchSize: Int,
)

sealed interface HistoryBatchUploadResult {
    data class Success(
        val acceptedHistoryIds: List<Long>,
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : HistoryBatchUploadResult

    data class Failure(
        val message: String,
        val acceptedHistoryIds: List<Long>,
        val insertedCount: Int,
        val dedupedCount: Int,
        val failedBatchIndex: Int,
        val totalBatches: Int,
    ) : HistoryBatchUploadResult
}

class HistoryBatchUploader(
    private val uploadBatch: (List<HistoryUploadRow>) -> HistoryUploadResult,
) {
    fun upload(
        rows: List<HistoryUploadRow>,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        onBatchStart: ((HistoryBatchProgress) -> Unit)? = null,
    ): HistoryBatchUploadResult {
        require(batchSize > 0) { "batchSize must be positive" }
        if (rows.isEmpty()) {
            return HistoryBatchUploadResult.Success(
                acceptedHistoryIds = emptyList(),
                insertedCount = 0,
                dedupedCount = 0,
            )
        }

        val batches = rows.chunked(batchSize)
        val acceptedHistoryIds = mutableListOf<Long>()
        var insertedCount = 0
        var dedupedCount = 0

        batches.forEachIndexed { index, batch ->
            onBatchStart?.invoke(
                HistoryBatchProgress(
                    batchIndex = index,
                    totalBatches = batches.size,
                    batchSize = batch.size,
                )
            )

            when (val result = uploadBatch(batch)) {
                is HistoryUploadResult.Success -> {
                    acceptedHistoryIds += result.acceptedHistoryIds
                    insertedCount += result.insertedCount
                    dedupedCount += result.dedupedCount
                }

                is HistoryUploadResult.Failure -> {
                    return HistoryBatchUploadResult.Failure(
                        message = result.message,
                        acceptedHistoryIds = acceptedHistoryIds.toList(),
                        insertedCount = insertedCount,
                        dedupedCount = dedupedCount,
                        failedBatchIndex = index,
                        totalBatches = batches.size,
                    )
                }
            }
        }

        return HistoryBatchUploadResult.Success(
            acceptedHistoryIds = acceptedHistoryIds.toList(),
            insertedCount = insertedCount,
            dedupedCount = dedupedCount,
        )
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
    }
}
