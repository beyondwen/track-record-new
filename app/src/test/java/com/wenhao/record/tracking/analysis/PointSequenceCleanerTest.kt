package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointSequenceCleanerTest {

    @Test
    fun `clean removes time reversed duplicate and poor accuracy points`() {
        val cleaner = PointSequenceCleaner()

        val cleaned = cleaner.clean(
            listOf(
                analyzedPoint(
                    timestampMillis = 1_000L,
                    latitude = 30.0,
                    longitude = 120.0,
                    accuracyMeters = 8f,
                ),
                analyzedPoint(
                    timestampMillis = 900L,
                    latitude = 30.00001,
                    longitude = 120.00001,
                    accuracyMeters = 8f,
                ),
                analyzedPoint(
                    timestampMillis = 1_000L,
                    latitude = 30.0,
                    longitude = 120.0,
                    accuracyMeters = 8f,
                ),
                analyzedPoint(
                    timestampMillis = 2_000L,
                    latitude = 30.002,
                    longitude = 120.002,
                    accuracyMeters = 180f,
                ),
                analyzedPoint(
                    timestampMillis = 3_000L,
                    latitude = 30.00002,
                    longitude = 120.00001,
                    accuracyMeters = 10f,
                ),
            )
        )

        assertEquals(listOf(1_000L, 3_000L), cleaned.map { it.timestampMillis })
    }

    @Test
    fun `clean removes short jump that immediately returns to same area`() {
        val cleaner = PointSequenceCleaner()

        val cleaned = cleaner.clean(
            listOf(
                analyzedPoint(
                    timestampMillis = 0L,
                    latitude = 30.0,
                    longitude = 120.0,
                    accuracyMeters = 10f,
                ),
                analyzedPoint(
                    timestampMillis = 60_000L,
                    latitude = 30.0025,
                    longitude = 120.0025,
                    accuracyMeters = 65f,
                ),
                analyzedPoint(
                    timestampMillis = 120_000L,
                    latitude = 30.00002,
                    longitude = 120.00001,
                    accuracyMeters = 9f,
                ),
            )
        )

        assertEquals(2, cleaned.size)
        assertTrue(cleaned.none { it.timestampMillis == 60_000L })
    }
}
