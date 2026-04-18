package com.wenhao.record.tracking

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackingThreadDispatchTest {

    @Test
    fun `runs inline when already on tracking thread`() {
        val handlerThread = HandlerThread("tracking-dispatch-inline").apply { start() }
        try {
            val handler = Handler(handlerThread.looper)
            val events = CopyOnWriteArrayList<String>()
            val finished = CountDownLatch(1)

            handler.post {
                TrackingThreadDispatch.dispatch(handler) {
                    events += "dispatched"
                }
                events += "after"
                finished.countDown()
            }

            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("dispatched", "after"), events)
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `posts work when called off tracking thread`() {
        val handlerThread = HandlerThread("tracking-dispatch-post").apply { start() }
        try {
            val handler = Handler(handlerThread.looper)
            val events = CopyOnWriteArrayList<String>()
            val executed = CountDownLatch(1)

            TrackingThreadDispatch.dispatch(handler) {
                events += "dispatched"
                executed.countDown()
            }
            events += "after-call"

            assertTrue(executed.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("after-call", "dispatched"), events)
        } finally {
            handlerThread.quitSafely()
        }
    }
}
