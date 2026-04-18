package com.wenhao.record.data.tracking

data class TrainingSampleBatchProgress(
    val batchIndex: Int,
    val totalBatches: Int,
    val batchSize: Int,
)

sealed interface TrainingSampleBatchUploadResult {
    data class Success(
        val acceptedEventIds: List<Long>,
        val insertedCount: Int,
        val dedupedCount: Int,
    ) : TrainingSampleBatchUploadResult

    data class Failure(
        val message: String,
        val acceptedEventIds: List<Long>,
        val insertedCount: Int,
        val dedupedCount: Int,
        val failedBatchIndex: Int,
        val totalBatches: Int,
    ) : TrainingSampleBatchUploadResult
}

class TrainingSampleBatchUploader(
    private val uploadBatch: (List<TrainingSampleRow>) -> TrainingSampleUploadResult,
) {
    fun upload(
        rows: List<TrainingSampleRow>,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        onBatchStart: ((TrainingSampleBatchProgress) -> Unit)? = null,
    ): TrainingSampleBatchUploadResult {
        require(batchSize > 0) { "batchSize must be positive" }
        if (rows.isEmpty()) {
            return TrainingSampleBatchUploadResult.Success(
                acceptedEventIds = emptyList(),
                insertedCount = 0,
                dedupedCount = 0,
            )
        }

        val batches = rows.chunked(batchSize)
        val acceptedEventIds = mutableListOf<Long>()
        var insertedCount = 0
        var dedupedCount = 0

        batches.forEachIndexed { index, batch ->
            onBatchStart?.invoke(
                TrainingSampleBatchProgress(
                    batchIndex = index,
                    totalBatches = batches.size,
                    batchSize = batch.size,
                )
            )

            when (val result = uploadBatch(batch)) {
                is TrainingSampleUploadResult.Success -> {
                    acceptedEventIds += result.acceptedEventIds
                    insertedCount += result.insertedCount
                    dedupedCount += result.dedupedCount
                }

                is TrainingSampleUploadResult.Failure -> {
                    return TrainingSampleBatchUploadResult.Failure(
                        message = result.message,
                        acceptedEventIds = acceptedEventIds.toList(),
                        insertedCount = insertedCount,
                        dedupedCount = dedupedCount,
                        failedBatchIndex = index,
                        totalBatches = batches.size,
                    )
                }
            }
        }

        return TrainingSampleBatchUploadResult.Success(
            acceptedEventIds = acceptedEventIds.toList(),
            insertedCount = insertedCount,
            dedupedCount = dedupedCount,
        )
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 200
    }
}
