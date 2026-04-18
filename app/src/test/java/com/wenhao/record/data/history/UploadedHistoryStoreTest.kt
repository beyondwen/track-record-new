package com.wenhao.record.data.history

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadedHistoryStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences
        get() = context.applicationContext.getSharedPreferences(
            UploadedHistoryStore.PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Before
    fun setUp() {
        UploadedHistoryStore.clear(context)
    }

    @After
    fun tearDown() {
        UploadedHistoryStore.clear(context)
    }

    @Test
    fun `mark uploaded persists unique history ids`() {
        UploadedHistoryStore.markUploaded(context, listOf(3L, 3L, 7L, 7L, 11L))

        assertEquals(setOf(3L, 7L, 11L), UploadedHistoryStore.load(context))
    }

    @Test
    fun `load cleans dirty values from preferences`() {
        prefs.edit()
            .putStringSet(
                UploadedHistoryStore.KEY_UPLOADED_HISTORY_IDS,
                mutableSetOf("12", "bad-value", "15", "oops")
            )
            .apply()

        assertEquals(setOf(12L, 15L), UploadedHistoryStore.load(context))
        assertEquals(
            setOf("12", "15"),
            prefs.getStringSet(UploadedHistoryStore.KEY_UPLOADED_HISTORY_IDS, emptySet())
        )
        assertFalse(
            prefs.getStringSet(UploadedHistoryStore.KEY_UPLOADED_HISTORY_IDS, emptySet())!!
                .contains("bad-value")
        )
    }
}
