package com.wenhao.record.tracking

import android.location.Location
import android.location.LocationManager
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TodaySessionStorage
import com.wenhao.record.data.tracking.TodayTrackDisplayCache
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceTodaySessionTest {

    @Test
    fun `accepted raw point is mirrored into today session storage`() {
        clearDatabase()
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).create().get()
        setField(service, "enabled", true)
        setField(service, "currentPhase", TrackingPhase.ACTIVE)
        setField(service, "signalLost", false)
        val nowMillis = System.currentTimeMillis()

        invokeHandleLocationUpdate(
            service,
            goodLocation(timestampMillis = nowMillis),
        )

        val storage = TodaySessionStorage(
            TrackDatabase.getInstance(service).todaySessionDao(),
        )
        val dayStartMillis = dayStartMillis(nowMillis)

        assertEventually {
            assertTrue(runBlocking { storage.hasOpenSession(dayStartMillis) })
            assertEquals(
                1,
                runBlocking { TodayTrackDisplayCache.loadToday(service, nowMillis = nowMillis + 1_000L).size }
            )
        }
    }

    private fun goodLocation(timestampMillis: Long): Location {
        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 30.0
            longitude = 120.0
            time = timestampMillis
            accuracy = 8f
            speed = 1.2f
        }
    }

    private fun invokeHandleLocationUpdate(service: BackgroundTrackingService, location: Location) {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod("handleLocationUpdate", Location::class.java)
        method.isAccessible = true
        method.invoke(service, location)
    }

    private fun assertEventually(block: () -> Unit) {
        val deadline = System.currentTimeMillis() + 1_000L
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                block()
                return
            } catch (error: Throwable) {
                lastError = error
                Thread.sleep(20L)
            }
        }
        throw lastError ?: AssertionError("assertEventually timed out")
    }

    private fun dayStartMillis(timestampMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun clearDatabase() {
        TrackingRuntimeSnapshotStorage.save(
            RuntimeEnvironment.getApplication(),
            TrackingRuntimeSnapshot(
                isEnabled = false,
                phase = TrackingPhase.IDLE,
                samplingTier = SamplingTier.IDLE,
                latestPoint = null,
                lastAnalysisAt = null,
                sessionId = null,
                dayStartMillis = null,
            ),
        )
        TrackDatabase.closeInstance()
        RuntimeEnvironment.getApplication().deleteDatabase("track_record.db")
        TrackDatabase.closeInstance()
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = BackgroundTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
