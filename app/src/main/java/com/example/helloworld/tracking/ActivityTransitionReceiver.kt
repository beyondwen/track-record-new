package com.example.helloworld.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityTransitionResult

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            val serviceIntent = Intent(context, BackgroundTrackingService::class.java).apply {
                action = BackgroundTrackingService.ACTION_ACTIVITY_TRANSITION
                putExtra(BackgroundTrackingService.EXTRA_ACTIVITY_TYPE, event.activityType)
                putExtra(BackgroundTrackingService.EXTRA_TRANSITION_TYPE, event.transitionType)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
