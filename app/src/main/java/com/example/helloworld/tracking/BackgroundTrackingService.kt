package com.example.helloworld.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.helloworld.R
import com.example.helloworld.data.history.HistoryItem
import com.example.helloworld.data.history.HistoryStorage
import com.example.helloworld.data.tracking.AutoTrackSession
import com.example.helloworld.data.tracking.AutoTrackStorage
import com.example.helloworld.data.tracking.AutoTrackUiState
import com.example.helloworld.data.tracking.TrackPoint
import com.example.helloworld.ui.main.MainActivity
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.max

class BackgroundTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.example.helloworld.action.START_BACKGROUND_TRACKING"
        const val ACTION_STOP = "com.example.helloworld.action.STOP_BACKGROUND_TRACKING"
        const val ACTION_ACTIVITY_TRANSITION = "com.example.helloworld.action.ACTIVITY_TRANSITION"
        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_TRANSITION_TYPE = "extra_transition_type"

        private const val CHANNEL_ID = "smart_tracking"
        private const val NOTIFICATION_ID = 1107
        private const val STILL_STOP_DELAY_MS = 2 * 60 * 1000L
        private const val MIN_USEFUL_DURATION_SECONDS = 45
        private const val MIN_USEFUL_DISTANCE_KM = 0.08

        fun start(context: Context) {
            val intent = Intent(context, BackgroundTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleLocationUpdate)
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var currentSession: AutoTrackSession? = null
    private var activityTransitionsRegistered = false
    private var isLocationUpdatesActive = false
    private var requestedPriority = -1
    private var requestedIntervalMs = -1L
    private var requestedMinDistanceMeters = -1f

    private val finalizeRunnable = Runnable {
        finalizeCurrentSession()
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        currentSession = AutoTrackStorage.loadSession(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AutoTrackStorage.setAutoTrackingEnabled(this, false)
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.DISABLED)
                stopAllTracking(clearSession = false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ACTIVITY_TRANSITION -> {
                ensureForeground()
                val activityType = intent.getIntExtra(EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN)
                val transitionType = intent.getIntExtra(
                    EXTRA_TRANSITION_TYPE,
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER
                )
                handleActivityTransition(activityType, transitionType)
            }

            else -> {
                ensureForeground()
                AutoTrackStorage.setAutoTrackingEnabled(this, true)
                registerActivityTransitionsIfPossible()
                if (currentSession != null) {
                    AutoTrackStorage.saveUiState(this, AutoTrackUiState.TRACKING)
                    requestLocationUpdates(
                        priority = Priority.PRIORITY_HIGH_ACCURACY,
                        intervalMs = 5_000L,
                        minDistanceMeters = 6f
                    )
                } else {
                    AutoTrackStorage.saveUiState(this, AutoTrackUiState.IDLE)
                }
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAllTracking(clearSession = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun handleActivityTransition(activityType: Int, transitionType: Int) {
        when {
            activityType == DetectedActivity.STILL &&
                transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                stopLocationUpdates()
                if (currentSession != null) {
                    AutoTrackStorage.saveUiState(this, AutoTrackUiState.PAUSED_STILL)
                    handler.removeCallbacks(finalizeRunnable)
                    handler.postDelayed(finalizeRunnable, STILL_STOP_DELAY_MS)
                    updateNotification(
                        title = "\u6b63\u5728\u5224\u65ad\u672c\u6b21\u884c\u7a0b\u662f\u5426\u7ed3\u675f",
                        content = "\u68c0\u6d4b\u5230\u4f60\u5df2\u9759\u6b62\uff0c2 \u5206\u949f\u540e\u4ecd\u9759\u6b62\u5c31\u4f1a\u81ea\u52a8\u4fdd\u5b58\u884c\u7a0b\u3002"
                    )
                }
            }

            isMovingActivity(activityType) &&
                transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                handler.removeCallbacks(finalizeRunnable)
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.PREPARING)
                startOrResumeSession()
                requestLocationUpdates(
                    priority = Priority.PRIORITY_HIGH_ACCURACY,
                    intervalMs = 5_000L,
                    minDistanceMeters = 6f
                )
                updateNotification()
            }

            activityType == DetectedActivity.STILL &&
                transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                handler.removeCallbacks(finalizeRunnable)
                if (currentSession != null) {
                    AutoTrackStorage.saveUiState(this, AutoTrackUiState.PREPARING)
                    requestLocationUpdates(
                        priority = Priority.PRIORITY_HIGH_ACCURACY,
                        intervalMs = 5_000L,
                        minDistanceMeters = 6f
                    )
                    updateNotification()
                }
            }
        }
    }

    private fun startOrResumeSession() {
        val now = System.currentTimeMillis()
        val updatedSession = (currentSession ?: AutoTrackSession(
            startTimestamp = now,
            lastMotionTimestamp = now,
            totalDistanceKm = 0.0
        )).copy(lastMotionTimestamp = now)
        currentSession = updatedSession
        AutoTrackStorage.saveSession(this, updatedSession)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(priority: Int, intervalMs: Long, minDistanceMeters: Float) {
        if (!hasLocationPermission()) return
        if (isLocationUpdatesActive &&
            requestedPriority == priority &&
            requestedIntervalMs == intervalMs &&
            requestedMinDistanceMeters == minDistanceMeters
        ) {
            return
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(max(1_500L, intervalMs / 2))
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setMaxUpdateDelayMillis(intervalMs * 3)
            .build()

        fusedLocationClient.removeLocationUpdates(locationCallback)
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isLocationUpdatesActive = true
        requestedPriority = priority
        requestedIntervalMs = intervalMs
        requestedMinDistanceMeters = minDistanceMeters
    }

    private fun stopLocationUpdates() {
        if (!isLocationUpdatesActive) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
        requestedPriority = -1
        requestedIntervalMs = -1L
        requestedMinDistanceMeters = -1f
    }

    private fun finalizeCurrentSession() {
        val session = currentSession ?: return
        currentSession = null
        AutoTrackStorage.clearSession(this)
        stopLocationUpdates()

        val durationSeconds = ((System.currentTimeMillis() - session.startTimestamp) / 1000L).toInt()
        if (durationSeconds < MIN_USEFUL_DURATION_SECONDS && session.totalDistanceKm < MIN_USEFUL_DISTANCE_KM) {
            AutoTrackStorage.saveUiState(this, AutoTrackUiState.IDLE)
            updateNotification(
                title = "\u667a\u80fd\u8f68\u8ff9\u5df2\u5f00\u542f",
                content = "\u5f53\u524d\u5904\u4e8e\u5f85\u547d\u72b6\u6001\uff0c\u68c0\u6d4b\u5230\u51fa\u884c\u540e\u4f1a\u81ea\u52a8\u8bb0\u5f55\u3002"
            )
            return
        }

        val averageSpeed = if (durationSeconds > 0) {
            session.totalDistanceKm / (durationSeconds / 3600.0)
        } else {
            0.0
        }
        val historyItems = HistoryStorage.load(this)
        val nextId = (historyItems.maxOfOrNull { it.id } ?: 0L) + 1L
        historyItems.add(
            0,
            HistoryItem(
                id = nextId,
                timestamp = session.startTimestamp,
                distanceKm = session.totalDistanceKm,
                durationSeconds = durationSeconds,
                averageSpeedKmh = averageSpeed,
                points = session.points
            )
        )
        HistoryStorage.save(this, historyItems)
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.SAVED_RECENTLY)
        updateNotification(
            title = "\u884c\u7a0b\u5df2\u81ea\u52a8\u4fdd\u5b58",
            content = "\u5df2\u4e3a\u4f60\u4fdd\u5b58\u6700\u65b0\u4e00\u6bb5\u51fa\u884c\uff0c\u5f53\u524d\u7ee7\u7eed\u5728\u540e\u53f0\u5b88\u5019\u3002"
        )
    }

    private fun handleLocationUpdate(location: Location) {
        startOrResumeSession()
        val previousSession = currentSession ?: return

        var totalDistance = previousSession.totalDistanceKm
        val previousLat = previousSession.lastLatitude
        val previousLng = previousSession.lastLongitude
        if (previousLat != null && previousLng != null) {
            val result = FloatArray(1)
            Location.distanceBetween(previousLat, previousLng, location.latitude, location.longitude, result)
            if (result[0] >= 3f) {
                totalDistance += result[0] / 1000.0
            }
        }

        val trackPoint = convertToGcj02TrackPoint(location)
        val points = if (shouldAppendTrackPoint(previousSession.points.lastOrNull(), trackPoint)) {
            previousSession.points + trackPoint
        } else {
            previousSession.points
        }

        val updatedSession = previousSession.copy(
            lastMotionTimestamp = System.currentTimeMillis(),
            totalDistanceKm = totalDistance,
            lastLatitude = location.latitude,
            lastLongitude = location.longitude,
            points = points
        )
        currentSession = updatedSession
        AutoTrackStorage.saveSession(this, updatedSession)
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.TRACKING)
        adaptLocationRequestForSpeed(location.speed)
        updateNotification()
    }

    private fun adaptLocationRequestForSpeed(speedMetersPerSecond: Float) {
        if (!isLocationUpdatesActive) return
        when {
            speedMetersPerSecond >= 10f -> requestLocationUpdates(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                intervalMs = 2_500L,
                minDistanceMeters = 12f
            )

            speedMetersPerSecond >= 3f -> requestLocationUpdates(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                intervalMs = 4_000L,
                minDistanceMeters = 8f
            )

            else -> requestLocationUpdates(
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                intervalMs = 8_000L,
                minDistanceMeters = 5f
            )
        }
    }

    private fun shouldAppendTrackPoint(lastPoint: TrackPoint?, newPoint: TrackPoint): Boolean {
        if (lastPoint == null) return true
        val result = FloatArray(1)
        Location.distanceBetween(
            lastPoint.latitude,
            lastPoint.longitude,
            newPoint.latitude,
            newPoint.longitude,
            result
        )
        return result[0] >= 5f
    }

    private fun convertToGcj02TrackPoint(location: Location): TrackPoint {
        val converter = CoordinateConverter(this)
        converter.from(CoordinateConverter.CoordType.GPS)
        converter.coord(LatLng(location.latitude, location.longitude))
        val latLng = converter.convert()
        return TrackPoint(latLng.latitude, latLng.longitude)
    }

    @SuppressLint("MissingPermission")
    private fun registerActivityTransitionsIfPossible() {
        if (activityTransitionsRegistered || !hasLocationPermission() || !hasActivityRecognitionPermission()) {
            return
        }
        val transitions = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE
        ).flatMap { activityType ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }

        activityRecognitionClient.requestActivityTransitionUpdates(
            ActivityTransitionRequest(transitions),
            transitionPendingIntent()
        ).addOnSuccessListener {
            activityTransitionsRegistered = true
        }
    }

    private fun unregisterActivityTransitions() {
        if (!activityTransitionsRegistered) return
        activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent())
        activityTransitionsRegistered = false
    }

    private fun transitionPendingIntent(): PendingIntent {
        val intent = Intent(this, ActivityTransitionReceiver::class.java).apply {
            action = ACTION_ACTIVITY_TRANSITION
        }
        return PendingIntent.getBroadcast(
            this,
            2201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(
        title: String = if (currentSession != null) {
            "\u6b63\u5728\u667a\u80fd\u8bb0\u5f55\u884c\u7a0b"
        } else {
            "\u667a\u80fd\u8f68\u8ff9\u5df2\u5f00\u542f"
        },
        content: String = if (currentSession != null) {
            val session = currentSession
            val distance = session?.totalDistanceKm ?: 0.0
            "\u5df2\u8bb0\u5f55 %.2f \u516c\u91cc\uff0c\u9759\u6b62\u4e00\u6bb5\u65f6\u95f4\u540e\u4f1a\u81ea\u52a8\u4fdd\u5b58\u3002".format(distance)
        } else {
            "\u6b63\u5728\u540e\u53f0\u5b88\u5019\u4f60\u7684\u51fa\u884c\uff0c\u68c0\u6d4b\u5230\u79fb\u52a8\u540e\u4f1a\u81ea\u52a8\u5f00\u59cb\u3002"
        }
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            2202,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_record)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(
        title: String = if (currentSession != null) {
            "\u6b63\u5728\u667a\u80fd\u8bb0\u5f55\u884c\u7a0b"
        } else {
            "\u667a\u80fd\u8f68\u8ff9\u5df2\u5f00\u542f"
        },
        content: String = if (currentSession != null) {
            val distance = currentSession?.totalDistanceKm ?: 0.0
            "\u5df2\u8bb0\u5f55 %.2f \u516c\u91cc\uff0c\u9759\u6b62\u4e00\u6bb5\u65f6\u95f4\u540e\u4f1a\u81ea\u52a8\u4fdd\u5b58\u3002".format(distance)
        } else {
            "\u6b63\u5728\u540e\u53f0\u5b88\u5019\u4f60\u7684\u51fa\u884c\uff0c\u68c0\u6d4b\u5230\u79fb\u52a8\u540e\u4f1a\u81ea\u52a8\u5f00\u59cb\u3002"
        }
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "\u667a\u80fd\u8f68\u8ff9\u8bb0\u5f55",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "\u7528\u4e8e\u5728\u540e\u53f0\u6301\u7eed\u5b88\u5019\u4f60\u7684\u51fa\u884c"
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopAllTracking(clearSession: Boolean) {
        handler.removeCallbacks(finalizeRunnable)
        stopLocationUpdates()
        unregisterActivityTransitions()
        if (clearSession) {
            currentSession = null
            AutoTrackStorage.clearSession(this)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isMovingActivity(activityType: Int): Boolean {
        return activityType == DetectedActivity.WALKING ||
            activityType == DetectedActivity.RUNNING ||
            activityType == DetectedActivity.ON_FOOT ||
            activityType == DetectedActivity.ON_BICYCLE ||
            activityType == DetectedActivity.IN_VEHICLE
    }
}
