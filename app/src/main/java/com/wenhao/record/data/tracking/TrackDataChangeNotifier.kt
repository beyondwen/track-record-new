package com.wenhao.record.data.tracking

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

object TrackDataChangeNotifier {

    interface Listener {
        fun onDashboardDataChanged() = Unit

        fun onHistoryDataChanged() = Unit
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun notifyDashboardChanged() {
        mainHandler.post {
            listeners.forEach { it.onDashboardDataChanged() }
        }
    }

    fun notifyHistoryChanged() {
        mainHandler.post {
            listeners.forEach { it.onHistoryDataChanged() }
        }
    }
}
