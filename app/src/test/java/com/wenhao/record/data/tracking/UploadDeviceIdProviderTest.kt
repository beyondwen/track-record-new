package com.wenhao.record.data.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadDeviceIdProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences
        get() = context.applicationContext.getSharedPreferences(
            UPLOAD_DEVICE_ID_PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Before
    fun setUp() {
        prefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().apply()
    }

    @Test
    fun `returns stable installation id once generated`() {
        val first = uploadDeviceId(context)
        val second = uploadDeviceId(context)

        assertFalse(first.isBlank())
        assertEquals(first, second)
        assertEquals(first, prefs.getString(KEY_INSTALLATION_ID, null))
    }
}
