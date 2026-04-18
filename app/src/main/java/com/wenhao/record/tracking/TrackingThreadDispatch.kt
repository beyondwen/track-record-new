package com.wenhao.record.tracking

import android.os.Handler
import android.os.Looper

internal object TrackingThreadDispatch {

    fun dispatch(
        handler: Handler,
        currentLooper: Looper? = Looper.myLooper(),
        block: () -> Unit,
    ) {
        if (handler.looper == currentLooper) {
            block()
        } else {
            handler.post(block)
        }
    }
}
