package com.wenhao.record.ui.main

import android.location.Location
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreshLocationSelectionPolicyTest {

    @Test
    fun `more accurate fix replaces previous best`() {
        val current = location("network", accuracy = 68f, time = 1_000L)
        val candidate = location("gps", accuracy = 12f, time = 1_500L)

        assertTrue(FreshLocationSelectionPolicy.shouldReplaceBest(current, candidate))
    }

    @Test
    fun `slightly newer but much worse fix does not replace current best`() {
        val current = location("gps", accuracy = 14f, time = 1_000L)
        val candidate = location("network", accuracy = 55f, time = 3_000L)

        assertFalse(FreshLocationSelectionPolicy.shouldReplaceBest(current, candidate))
    }

    @Test
    fun `request finishes immediately when best fix is already accurate enough`() {
        val best = location("gps", accuracy = 10f, time = 2_000L)

        assertTrue(
            FreshLocationSelectionPolicy.shouldFinish(
                bestLocation = best,
                requestStartedAt = 1_000L,
                now = 1_200L,
            )
        )
    }

    @Test
    fun `request finishes after max wait with fallback fix`() {
        val best = location("network", accuracy = 60f, time = 2_000L)

        assertTrue(
            FreshLocationSelectionPolicy.shouldFinish(
                bestLocation = best,
                requestStartedAt = 1_000L,
                now = 4_000L,
            )
        )
    }

    private fun location(provider: String, accuracy: Float, time: Long): Location {
        return Location(provider).apply {
            latitude = 30.0
            longitude = 104.0
            this.accuracy = accuracy
            this.time = time
        }
    }
}
