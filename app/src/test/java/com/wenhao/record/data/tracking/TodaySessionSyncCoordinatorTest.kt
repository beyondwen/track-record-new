package com.wenhao.record.data.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodaySessionSyncCoordinatorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("today-session-sync-coordinator-test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().apply()
    }

    @Test
    fun `shouldSync returns true when pending points reach threshold`() {
        val coordinator = TodaySessionSyncCoordinator(prefs)

        assertTrue(coordinator.shouldSync(nowMillis = 31_000L, pendingPointCount = 20, force = false))
    }

    @Test
    fun `shouldSync returns true when sync interval is exceeded`() {
        val coordinator = TodaySessionSyncCoordinator(prefs)
        coordinator.markTriggered(nowMillis = 1_000L)

        assertTrue(coordinator.shouldSync(nowMillis = 31_001L, pendingPointCount = 3, force = false))
    }

    @Test
    fun `shouldSync returns false before threshold and interval`() {
        val coordinator = TodaySessionSyncCoordinator(prefs)
        coordinator.markTriggered(nowMillis = 5_000L)

        assertFalse(coordinator.shouldSync(nowMillis = 20_000L, pendingPointCount = 5, force = false))
    }

    @Test
    fun `shouldSync returns true when forced`() {
        val coordinator = TodaySessionSyncCoordinator(prefs)
        coordinator.markTriggered(nowMillis = 20_000L)

        assertTrue(coordinator.shouldSync(nowMillis = 20_001L, pendingPointCount = 0, force = true))
    }
}
