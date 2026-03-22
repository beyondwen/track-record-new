package com.wenhao.record.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.permissions.TrackingPermissionGate

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!AutoTrackStorage.isAutoTrackingEnabled(context)) return
        if (!TrackingPermissionGate.canRunBackgroundTracking(context)) {
            AutoTrackDiagnosticsStorage.markServiceStatus(
                context,
                context.getString(com.wenhao.record.R.string.diagnostics_status_waiting_permissions),
                context.getString(com.wenhao.record.R.string.diagnostics_event_boot_permissions_missing)
            )
            return
        }
        BackgroundTrackingService.start(context)
    }
}
