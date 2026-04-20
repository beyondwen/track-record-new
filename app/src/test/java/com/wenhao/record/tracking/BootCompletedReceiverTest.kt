package com.wenhao.record.tracking

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootCompletedReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        TrackingRuntimeSnapshotStorage.setEnabled(context, false)
    }

    @After
    fun tearDown() {
        TrackingRuntimeSnapshotStorage.setEnabled(context, false)
    }

    @Test
    fun `does not restore background tracking on boot when permissions are incomplete`() {
        TrackingRuntimeSnapshotStorage.setEnabled(context, true)

        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNull(shadowOf(application).nextStartedService)
    }
}
