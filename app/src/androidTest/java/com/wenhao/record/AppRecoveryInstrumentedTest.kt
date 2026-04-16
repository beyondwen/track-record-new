package com.wenhao.record

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.permissions.TrackingPermissionGate
import com.wenhao.record.tracking.BootCompletedReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRecoveryInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun flushPersistsThrottledAutoTrackSessionUpdates() {
        val dao = TrackDatabase.getInstance(context).autoTrackDao()
        val baseSession = buildSession(pointCount = 2)
        AutoTrackStorage.saveSession(context, baseSession)
        waitUntil { dao.getSessionPoints().size == 2 }

        val updatedSession = buildSession(pointCount = 3)
        AutoTrackStorage.saveSession(context, updatedSession)
        Thread.sleep(150L)
        assertEquals(2, dao.getSessionPoints().size)

        AutoTrackStorage.flush(context)
        waitUntil { dao.getSessionPoints().size == 3 }
        assertEquals(3, dao.getSessionPoints().size)
    }

    @Test
    fun historySaveSupportsDayLookup() {
        val item = HistoryItem(
            id = 9L,
            timestamp = 1_725_000_000_000L,
            distanceKm = 6.4,
            durationSeconds = 1200,
            averageSpeedKmh = 19.2,
            points = listOf(
                TrackPoint(30.1, 120.1, timestampMillis = 1_000L),
                TrackPoint(30.2, 120.2, timestampMillis = 2_000L)
            )
        )

        HistoryStorage.save(context, listOf(item))
        waitUntil { HistoryStorage.peek(context).size == 1 }

        val dayStart = com.wenhao.record.data.history.HistoryDayAggregator.startOfDay(item.timestamp)
        val loaded = requireNotNull(HistoryStorage.peekDailyByStart(context, dayStart))

        assertEquals(1, loaded.sourceIds.size)
        assertEquals(item.id, loaded.sourceIds.first())
    }

    @Test
    fun bootReceiverRecordsWaitingPermissionsDiagnosticsWhenPermissionsAreIncomplete() {
        assumeTrue(!TrackingPermissionGate.canRunBackgroundTracking(context))

        AutoTrackStorage.setAutoTrackingEnabled(context, true)
        AutoTrackDiagnosticsStorage.markServiceStatus(context, "初始化", "初始化")

        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        AutoTrackDiagnosticsStorage.flush(context)
        val diagnostics = AutoTrackDiagnosticsStorage.load(context)

        assertEquals(
            context.getString(R.string.diagnostics_status_waiting_permissions),
            diagnostics.serviceStatus
        )
        assertEquals(
            context.getString(R.string.diagnostics_event_boot_permissions_missing),
            diagnostics.lastEvent
        )
    }

    private fun clearState() {
        AutoTrackStorage.clearSession(context)
        AutoTrackStorage.flush(context)
        HistoryStorage.save(context, emptyList())
        TrackDatabase.getInstance(context).historyDao().replaceAll(emptyList(), emptyList())
        TrackDatabase.getInstance(context).autoTrackDao().deleteSessionPoints()
        TrackDatabase.getInstance(context).autoTrackDao().deleteSession()
        context.getSharedPreferences("track_record_auto", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("track_record_history", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("track_record_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteFile("history_snapshot.json")
    }

    private fun buildSession(pointCount: Int): AutoTrackSession {
        return AutoTrackSession(
            startTimestamp = 10_000L,
            lastMotionTimestamp = 20_000L,
            totalDistanceKm = pointCount.toDouble(),
            points = List(pointCount) { index ->
                TrackPoint(
                    latitude = 30.0 + (index * 0.001),
                    longitude = 120.0 + (index * 0.001),
                    timestampMillis = 10_000L + index
                )
            }
        )
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition was not met within ${timeoutMs}ms")
            }
            Thread.sleep(50L)
        }
    }
}
