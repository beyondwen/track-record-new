package com.wenhao.record.data.tracking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawPointRetentionPolicyTest {

    @Test
    fun `raw points are deletable only after accepted by remote upload cursor`() {
        assertFalse(RawPointRetentionPolicy.canDeleteAnalyzedRawPoints(rawUploadedUpToPointId = 99L, analyzedUpToPointId = 100L))
        assertTrue(RawPointRetentionPolicy.canDeleteAnalyzedRawPoints(rawUploadedUpToPointId = 100L, analyzedUpToPointId = 100L))
        assertTrue(RawPointRetentionPolicy.canDeleteAnalyzedRawPoints(rawUploadedUpToPointId = 120L, analyzedUpToPointId = 100L))
    }
}
