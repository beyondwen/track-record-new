package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceRollingAnalysisTest {

    @Test
    fun `first rolling analysis uses active phase anchor when no prior analysis exists`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).get()
        val anchorTimestamp = 1_000L
        setField(
            target = service,
            fieldName = "phaseAnchorPoint",
            value = TrackPoint(
                latitude = 39.9,
                longitude = 116.4,
                timestampMillis = anchorTimestamp,
            ),
        )
        setField(target = service, fieldName = "lastAnalysisAt", value = null)

        assertFalse(service.shouldTriggerRollingAnalysisForTest(anchorTimestamp + 8 * 60_000L - 1))
        assertTrue(service.shouldTriggerRollingAnalysisForTest(anchorTimestamp + 8 * 60_000L))
    }

    @Test
    fun `subsequent rolling analysis still uses last analysis timestamp`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).get()
        val anchorTimestamp = 1_000L
        val lastAnalysisAt = 20_000L
        setField(
            target = service,
            fieldName = "phaseAnchorPoint",
            value = TrackPoint(
                latitude = 39.9,
                longitude = 116.4,
                timestampMillis = anchorTimestamp,
            ),
        )
        setField(target = service, fieldName = "lastAnalysisAt", value = lastAnalysisAt)

        assertFalse(service.shouldTriggerRollingAnalysisForTest(lastAnalysisAt + 8 * 60_000L - 1))
        assertTrue(service.shouldTriggerRollingAnalysisForTest(lastAnalysisAt + 8 * 60_000L))
    }

    private fun BackgroundTrackingService.shouldTriggerRollingAnalysisForTest(timestampMillis: Long): Boolean {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod(
            "shouldTriggerRollingAnalysis",
            Long::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(this, timestampMillis) as Boolean
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = BackgroundTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
