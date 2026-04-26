package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackFixQualityGateTest {

    @Test
    fun `requires three consecutive fresh accurate fixes before recording starts`() {
        val gate = TrackFixQualityGate(
            requiredConsecutiveGoodFixes = 3,
            maxAcceptedAccuracyMeters = 25f,
            maxFixAgeMillis = 8_000L,
            minMeaningfulDistanceMeters = 8f,
        )
        val now = 1_713_420_000_000L
        val first = TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = now - 4_000L, accuracyMeters = 12f)
        val second = TrackPoint(latitude = 30.00012, longitude = 120.00012, timestampMillis = now - 2_000L, accuracyMeters = 10f)
        val third = TrackPoint(latitude = 30.00026, longitude = 120.00026, timestampMillis = now - 500L, accuracyMeters = 9f)

        assertFalse(gate.noteFix(first, nowMillis = now, previousCandidate = null).isReadyToRecord)
        assertFalse(gate.noteFix(second, nowMillis = now, previousCandidate = first).isReadyToRecord)
        assertTrue(gate.noteFix(third, nowMillis = now, previousCandidate = second).isReadyToRecord)
    }

    @Test
    fun `bad indoor fix resets consecutive good fixes`() {
        val gate = TrackFixQualityGate(
            requiredConsecutiveGoodFixes = 2,
            maxAcceptedAccuracyMeters = 25f,
            maxFixAgeMillis = 8_000L,
            minMeaningfulDistanceMeters = 8f,
        )
        val now = 1_713_420_000_000L
        val first = TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = now - 2_000L, accuracyMeters = 12f)
        val badIndoor = TrackPoint(latitude = 30.00001, longitude = 120.00001, timestampMillis = now - 1_000L, accuracyMeters = 85f)
        val recovery = TrackPoint(latitude = 30.0002, longitude = 120.0002, timestampMillis = now, accuracyMeters = 9f)

        assertFalse(gate.noteFix(first, nowMillis = now, previousCandidate = null).isReadyToRecord)
        assertFalse(gate.noteFix(badIndoor, nowMillis = now, previousCandidate = first).isReadyToRecord)
        assertFalse(gate.noteFix(recovery, nowMillis = now, previousCandidate = badIndoor).isReadyToRecord)
    }
}
