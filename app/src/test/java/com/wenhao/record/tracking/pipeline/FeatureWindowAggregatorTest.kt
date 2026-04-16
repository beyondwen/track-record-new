package com.wenhao.record.tracking.pipeline

import com.wenhao.record.tracking.TrackingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FeatureWindowAggregatorTest {

    @Test
    fun `build vector from 30s 60s 180s windows`() {
        val aggregator = FeatureWindowAggregator(
            clock = { 180_000L }
        )

        aggregator.append(
            SignalSnapshot(
                timestampMillis = 30_000L,
                phase = TrackingPhase.IDLE,
                isRecording = false,
                latitude = 39.90,
                longitude = 116.40,
                accuracyMeters = 12f,
                speedMetersPerSecond = 0.4f,
                stepDelta = 3,
                accelerationMagnitude = 0.9f,
                wifiChanged = false,
                insideFrequentPlace = true,
                candidateStateDurationMillis = 30_000L,
                protectionRemainingMillis = 0L,
            )
        )

        val vector = aggregator.buildVector()

        assertNotNull(vector)
        assertEquals(3.0, vector!!.features.getValue("steps_30s"), 0.0001)
        assertEquals(12.0, vector.features.getValue("accuracy_avg_30s"), 0.0001)
        assertEquals(1.0, vector.features.getValue("gps_sample_count_30s"), 0.0001)
        assertEquals(1.0, vector.features.getValue("motion_evidence_30s"), 0.0001)
        assertEquals(1.0, vector.features.getValue("inside_frequent_place_current"), 0.0001)
        assertEquals(1.0, vector.features.getValue("inside_frequent_place_180s_ratio"), 0.0001)
    }
}
