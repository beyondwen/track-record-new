package com.wenhao.record.data.tracking

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.CopyOnWriteArraySet

object TrackDataChangeNotifier {

    interface Listener {
        fun onDashboardDataChanged() = Unit

        fun onHistoryDataChanged() = Unit

        fun onDiagnosticsChanged() = Unit
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun addLifecycleListener(lifecycleOwner: LifecycleOwner, listener: Listener) {
        addListener(listener)
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    removeListener(listener)
                    lifecycleOwner.lifecycle.removeObserver(this)
                }
            }
        })
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

    fun notifyDiagnosticsChanged() {
        mainHandler.post {
            listeners.forEach { it.onDiagnosticsChanged() }
        }
    }
}
