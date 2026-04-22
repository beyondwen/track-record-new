package com.wenhao.record.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

object AppTaskExecutor {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun runOnIo(task: () -> Unit): Future<*>? {
        return ioExecutor.submit(task)
    }

    fun runOnMain(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            mainHandler.post(task)
        }
    }

    fun cancelAll() {
        ioExecutor.shutdownNow()
    }
}
