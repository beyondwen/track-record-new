package com.wenhao.record.tracking

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundTrackingServiceSamplingConfigTest {

    @Test
    fun `active phase uses denser manual recording sampling`() {
        val config = samplingConfigFor(TrackingPhase.ACTIVE)

        assertEquals(2_000L, config.intervalMillis)
        assertEquals(2f, config.minDistanceMeters)
    }

    private fun samplingConfigFor(phase: TrackingPhase): SamplingConfigView {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod(
            "samplingConfigFor",
            TrackingPhase::class.java,
        )
        method.isAccessible = true
        val result = method.invoke(BackgroundTrackingService(), phase)
        val configClass = result.javaClass
        val intervalMillis = configClass.getDeclaredField("intervalMillis").apply {
            isAccessible = true
        }.getLong(result)
        val minDistanceMeters = configClass.getDeclaredField("minDistanceMeters").apply {
            isAccessible = true
        }.getFloat(result)
        return SamplingConfigView(
            intervalMillis = intervalMillis,
            minDistanceMeters = minDistanceMeters,
        )
    }

    private data class SamplingConfigView(
        val intervalMillis: Long,
        val minDistanceMeters: Float,
    )
}
