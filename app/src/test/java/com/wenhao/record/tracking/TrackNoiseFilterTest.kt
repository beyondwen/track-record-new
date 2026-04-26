package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackNoiseFilterTest {

    @Test
    fun `drops stale first point with poor quality`() {
        val result = TrackNoiseFilter.evaluate(
            previousPoint = null,
            lastPoint = null,
            sample = TrackNoiseSample(
                point = TrackPoint(
                    latitude = 30.0,
                    longitude = 120.0,
                    timestampMillis = 1_000L,
                    accuracyMeters = 130f
                ),
                speedMetersPerSecond = 0.3f,
                locationAgeMs = 25_000L
            )
        )

        assertEquals(TrackNoiseAction.DROP_DRIFT, result.action)
    }

    @Test
    fun `merges tiny stationary jitter`() {
        val result = TrackNoiseFilter.evaluate(
            previousPoint = null,
            lastPoint = TrackPoint(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = 10_000L,
                accuracyMeters = 6f
            ),
            sample = TrackNoiseSample(
                point = TrackPoint(
                    latitude = 30.00001,
                    longitude = 120.00001,
                    timestampMillis = 12_000L,
                    accuracyMeters = 6f
                ),
                speedMetersPerSecond = 0.5f,
                locationAgeMs = 100L
            )
        )

        assertEquals(TrackNoiseAction.MERGE_STILL, result.action)
    }

    @Test
    fun `drops impossible jump`() {
        val result = TrackNoiseFilter.evaluate(
            previousPoint = null,
            lastPoint = TrackPoint(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = 1_000L,
                accuracyMeters = 5f
            ),
            sample = TrackNoiseSample(
                point = TrackPoint(
                    latitude = 31.0,
                    longitude = 121.0,
                    timestampMillis = 6_000L,
                    accuracyMeters = 5f
                ),
                speedMetersPerSecond = 3f,
                locationAgeMs = 100L
            )
        )

        assertEquals(TrackNoiseAction.DROP_JUMP, result.action)
    }

    @Test
    fun `drops poor accuracy active point when displacement is still drift-like`() {
        val result = TrackNoiseFilter.evaluate(
            previousPoint = null,
            lastPoint = TrackPoint(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = 1_000L,
                accuracyMeters = 6f
            ),
            sample = TrackNoiseSample(
                point = TrackPoint(
                    latitude = 30.0001,
                    longitude = 120.0001,
                    timestampMillis = 6_000L,
                    accuracyMeters = 41f
                ),
                speedMetersPerSecond = 1.2f,
                locationAgeMs = 100L
            )
        )

        assertEquals(TrackNoiseAction.DROP_DRIFT, result.action)
    }

    @Test
    fun `keeps moderate active fix for later path sanitizing`() {
        val result = TrackNoiseFilter.evaluate(
            previousPoint = null,
            lastPoint = TrackPoint(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = 1_000L,
                accuracyMeters = 8f,
            ),
            sample = TrackNoiseSample(
                point = TrackPoint(
                    latitude = 30.00018,
                    longitude = 120.00018,
                    timestampMillis = 6_000L,
                    accuracyMeters = 32f,
                ),
                speedMetersPerSecond = 1.0f,
                locationAgeMs = 100L,
            ),
        )

        assertEquals(TrackNoiseAction.ACCEPT, result.action)
    }

    @Test
    fun `drops active point with abnormal reported speed`() {
        val result = TrackNoiseFilter.evaluate(
            previousPoint = null,
            lastPoint = TrackPoint(
                latitude = 30.0,
                longitude = 120.0,
                timestampMillis = 1_000L,
                accuracyMeters = 6f
            ),
            sample = TrackNoiseSample(
                point = TrackPoint(
                    latitude = 30.0003,
                    longitude = 120.0003,
                    timestampMillis = 6_000L,
                    accuracyMeters = 6f
                ),
                speedMetersPerSecond = 46f,
                locationAgeMs = 100L
            )
        )

        assertEquals(TrackNoiseAction.DROP_JUMP, result.action)
    }
}
