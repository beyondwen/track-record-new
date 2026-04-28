package com.wenhao.record.data.tracking

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackUploadSchedulerTest {

    @Test
    fun `one time upload pipeline keeps raw today session order`() {
        assertEquals(
            listOf(
                "RawPointUploadWorker",
                "TodaySessionSyncWorker",
            ),
            TrackUploadPipelinePlan.ONE_TIME_CHAIN.map { it.simpleName },
        )
    }

    @Test
    fun `local result pipeline stays empty after local history refactor`() {
        assertEquals(
            emptyList(),
            TrackUploadPipelinePlan.LOCAL_RESULT_CHAIN.map { it.simpleName },
        )
    }
}
