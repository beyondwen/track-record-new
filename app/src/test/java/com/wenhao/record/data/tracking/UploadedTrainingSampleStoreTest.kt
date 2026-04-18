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
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadedTrainingSampleStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences
        get() = context.applicationContext.getSharedPreferences(
            UploadedTrainingSampleStore.PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Before
    fun setUp() {
        UploadedTrainingSampleStore.clear(context)
    }

    @After
    fun tearDown() {
        UploadedTrainingSampleStore.clear(context)
    }

    @Test
    fun `mark uploaded persists unique event ids`() {
        UploadedTrainingSampleStore.markUploaded(context, listOf(3L, 3L, 7L, 7L, 11L))

        assertEquals(setOf(3L, 7L, 11L), UploadedTrainingSampleStore.load(context))
    }

    @Test
    fun `multiple mark uploaded calls merge sets`() {
        UploadedTrainingSampleStore.markUploaded(context, listOf(1L, 2L))
        UploadedTrainingSampleStore.markUploaded(context, listOf(2L, 3L))

        assertEquals(setOf(1L, 2L, 3L), UploadedTrainingSampleStore.load(context))
    }

    @Test
    fun `clear removes uploaded event ids`() {
        UploadedTrainingSampleStore.markUploaded(context, listOf(9L, 10L))

        UploadedTrainingSampleStore.clear(context)

        assertEquals(emptySet(), UploadedTrainingSampleStore.load(context))
    }

    @Test
    fun `mark uploaded with empty list keeps existing ids`() {
        UploadedTrainingSampleStore.markUploaded(context, listOf(4L, 5L))

        UploadedTrainingSampleStore.markUploaded(context, emptyList())

        assertEquals(setOf(4L, 5L), UploadedTrainingSampleStore.load(context))
    }

    @Test
    fun `load returns empty set when nothing is stored`() {
        assertEquals(emptySet(), UploadedTrainingSampleStore.load(context))
    }

    @Test
    fun `load cleans dirty values from preferences`() {
        prefs.edit()
            .putStringSet(
                UploadedTrainingSampleStore.KEY_UPLOADED_EVENT_IDS,
                mutableSetOf("12", "bad-value", "15", "oops")
            )
            .apply()

        assertEquals(setOf(12L, 15L), UploadedTrainingSampleStore.load(context))
        assertEquals(setOf("12", "15"), prefs.getStringSet(
            UploadedTrainingSampleStore.KEY_UPLOADED_EVENT_IDS,
            emptySet()
        ))
        assertFalse(
            prefs.getStringSet(
                UploadedTrainingSampleStore.KEY_UPLOADED_EVENT_IDS,
                emptySet()
            )!!.contains("bad-value")
        )
    }
}
