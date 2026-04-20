package com.wenhao.record.tracking

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundTrackingDiagnosticsTest {

    @Test
    fun `idle phase reports ignored location when accuracy exceeds standby threshold`() {
        assertEquals(
            "已忽略：低频待命精度 96m > 80m",
            ignoredLocationDecision(
                phase = TrackingPhase.IDLE,
                accuracyMeters = 96f,
                speedMetersPerSecond = null,
            )
        )
    }

    @Test
    fun `active phase reports ignored location when accuracy exceeds active threshold`() {
        assertEquals(
            "已忽略：活跃采样精度 40m > 35m",
            ignoredLocationDecision(
                phase = TrackingPhase.ACTIVE,
                accuracyMeters = 40f,
                speedMetersPerSecond = null,
            )
        )
    }

    @Test
    fun `accepted location summary includes phase label and point count`() {
        assertEquals(
            "已接收：低频待命，第 3 个点，精度 18m",
            acceptedLocationDecision(
                phase = TrackingPhase.IDLE,
                acceptedPointCount = 3,
                accuracyMeters = 18f,
            )
        )
    }
}
