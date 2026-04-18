package com.wenhao.record.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapboxTokenStorageTest {

    @Test
    fun `save trims token before persisting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MapboxTokenStorage.clear(context)

        MapboxTokenStorage.save(context, "  pk.runtime-token  ")

        assertEquals("pk.runtime-token", MapboxTokenStorage.load(context))
    }

    @Test
    fun `save clears persisted token when input is blank`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MapboxTokenStorage.save(context, "pk.runtime-token")

        MapboxTokenStorage.save(context, "   ")

        assertEquals("", MapboxTokenStorage.load(context))
    }
}
