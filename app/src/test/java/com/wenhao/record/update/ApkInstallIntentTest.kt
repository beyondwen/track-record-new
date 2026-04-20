package com.wenhao.record.update

import android.content.Intent
import android.net.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApkInstallIntentTest {
    @Test
    @Suppress("DEPRECATION")
    fun `build install intent uses package installer action and grants read access`() {
        val contentUri = Uri.parse("content://com.wenhao.record.fileprovider/update/app-release.apk")

        val intent = buildInstallIntent(contentUri)

        assertEquals(Intent.ACTION_INSTALL_PACKAGE, intent.action)
        assertEquals(contentUri, intent.data)
        assertEquals(APK_MIME_TYPE, intent.type)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
    }

    @Test
    fun `build fallback install intent uses view action and apk mime type`() {
        val contentUri = Uri.parse("content://com.wenhao.record.fileprovider/update/app-release.apk")

        val intent = buildInstallFallbackIntent(contentUri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(contentUri, intent.data)
        assertEquals(APK_MIME_TYPE, intent.type)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
    }
}
