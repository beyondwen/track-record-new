package com.wenhao.record.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (TrackingRuntimeSnapshotStorage.peek(context).isEnabled) {
            BackgroundTrackingService.start(context)
        }
    }
}
