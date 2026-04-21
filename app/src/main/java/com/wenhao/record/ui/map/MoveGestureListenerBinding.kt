package com.wenhao.record.ui.map

import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.gestures.OnMoveListener

internal interface MoveGestureListenerHost {
    fun addOnMoveListener(listener: OnMoveListener)
    fun removeOnMoveListener(listener: OnMoveListener)
}

internal class MoveGestureListenerBinding {
    private var host: MoveGestureListenerHost? = null
    private var listener: OnMoveListener? = null
    private var callback: (() -> Unit)? = null

    fun bind(
        host: MoveGestureListenerHost?,
        interactive: Boolean,
        onUserGestureMove: (() -> Unit)?,
    ) {
        callback = onUserGestureMove
        if (host == null || !interactive || onUserGestureMove == null) {
            clear()
            return
        }

        if (this.host === host && listener != null) {
            return
        }

        clear()
        val moveListener = object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                callback?.invoke()
            }

            override fun onMove(detector: MoveGestureDetector): Boolean = false

            override fun onMoveEnd(detector: MoveGestureDetector) = Unit
        }
        host.addOnMoveListener(moveListener)
        this.host = host
        listener = moveListener
    }

    fun clear() {
        val currentHost = host
        val currentListener = listener
        if (currentHost != null && currentListener != null) {
            currentHost.removeOnMoveListener(currentListener)
        }
        host = null
        listener = null
        callback = null
    }
}
