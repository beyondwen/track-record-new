package com.wenhao.record.data.tracking

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackUploadSchedulerTest {

    @Test
    fun `one time upload pipeline keeps raw today session processed analysis order`() {
        assertEquals(
            listOf(
                "RawPointUploadWorker",
                "TodaySessionSyncWorker",
                "ProcessedHistorySyncWorker",
                "AnalysisUploadWorker",
            ),
            TrackUploadPipelinePlan.ONE_TIME_CHAIN.map { it.simpleName },
        )
    }

    @Test
    fun `local result pipeline uploads analysis`() {
        assertEquals(
            listOf(
                "AnalysisUploadWorker",
            ),
            TrackUploadPipelinePlan.LOCAL_RESULT_CHAIN.map { it.simpleName },
        )
    }
}
