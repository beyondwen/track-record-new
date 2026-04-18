package com.wenhao.record.data.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrainingSampleUploadConfigStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences
        get() = context.applicationContext.getSharedPreferences(
            TrainingSampleUploadConfigStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Before
    fun setUp() {
        TrainingSampleUploadConfigStorage.clear(context)
    }

    @After
    fun tearDown() {
        TrainingSampleUploadConfigStorage.clear(context)
    }

    @Test
    fun `save returns sanitized config on load`() {
        TrainingSampleUploadConfigStorage.save(
            context,
            TrainingSampleUploadConfig(
                workerBaseUrl = "  https://worker.example.com/api/  ",
                uploadToken = "  token-123  "
            )
        )

        assertEquals(
            TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com/api",
                uploadToken = "token-123"
            ),
            TrainingSampleUploadConfigStorage.load(context)
        )
    }

    @Test
    fun `clear restores default empty config`() {
        TrainingSampleUploadConfigStorage.save(
            context,
            TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com/api",
                uploadToken = "token-123"
            )
        )

        TrainingSampleUploadConfigStorage.clear(context)

        assertEquals(
            TrainingSampleUploadConfig(),
            TrainingSampleUploadConfigStorage.load(context)
        )
    }

    @Test
    fun `blank values clear persisted config`() {
        TrainingSampleUploadConfigStorage.save(
            context,
            TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com/api",
                uploadToken = "token-123"
            )
        )

        TrainingSampleUploadConfigStorage.save(
            context,
            TrainingSampleUploadConfig(
                workerBaseUrl = "   ",
                uploadToken = "\n\t "
            )
        )

        assertEquals(
            TrainingSampleUploadConfig(),
            TrainingSampleUploadConfigStorage.load(context)
        )
    }

    @Test
    fun `load sanitizes dirty data from preferences`() {
        prefs.edit()
            .putString(TrainingSampleUploadConfigStorage.KEY_WORKER_BASE_URL, "  https://worker.example.com/api/  ")
            .putString(TrainingSampleUploadConfigStorage.KEY_UPLOAD_TOKEN, "  token-123  ")
            .apply()

        assertEquals(
            TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com/api",
                uploadToken = "token-123"
            ),
            TrainingSampleUploadConfigStorage.load(context)
        )
    }

    @Test
    fun `blank save removes persisted keys`() {
        TrainingSampleUploadConfigStorage.save(
            context,
            TrainingSampleUploadConfig(
                workerBaseUrl = "https://worker.example.com/api",
                uploadToken = "token-123"
            )
        )

        TrainingSampleUploadConfigStorage.save(
            context,
            TrainingSampleUploadConfig(
                workerBaseUrl = "   ",
                uploadToken = "\n\t "
            )
        )

        assertFalse(prefs.contains(TrainingSampleUploadConfigStorage.KEY_WORKER_BASE_URL))
        assertFalse(prefs.contains(TrainingSampleUploadConfigStorage.KEY_UPLOAD_TOKEN))
        assertEquals(
            TrainingSampleUploadConfig(),
            TrainingSampleUploadConfigStorage.load(context)
        )
    }
}
