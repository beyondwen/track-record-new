package com.wenhao.record.tracking

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
import com.wenhao.record.map.CoordinateTransformUtils
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.FrequentPlaceAnchor
import com.wenhao.record.data.tracking.FrequentPlaceStorage
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.ui.main.MainActivity
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
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

    private enum class TrackNoiseAction {
        ACCEPT,
        MERGE_STILL,
        DROP_DRIFT,
        DROP_JUMP
    }

    private data class TrackNoiseResult(
        val action: TrackNoiseAction,
        val distanceMeters: Float = 0f,
        val acceptedPoint: TrackPoint? = null
    )

    private data class PendingMovementVerification(
        val startedAt: Long,
        val anchorIds: Set<String>,
        val originPoint: TrackPoint? = null
    )

    companion object {
        const val ACTION_START = "com.wenhao.record.action.START_BACKGROUND_TRACKING"
        const val ACTION_STOP = "com.wenhao.record.action.STOP_BACKGROUND_TRACKING"
        const val ACTION_ACTIVITY_TRANSITION = "com.wenhao.record.action.ACTIVITY_TRANSITION"
        const val ACTION_GEOFENCE_TRANSITION = "com.wenhao.record.action.GEOFENCE_TRANSITION"
        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_TRANSITION_TYPE = "extra_transition_type"
        const val EXTRA_GEOFENCE_TRANSITION = "extra_geofence_transition"
        const val EXTRA_GEOFENCE_IDS = "extra_geofence_ids"

        private const val CHANNEL_ID = "smart_tracking"
        private const val NOTIFICATION_ID = 1107
        private const val STILL_STOP_DELAY_MS = 2 * 60 * 1000L
        private const val STAY_ZONE_VERIFY_TIMEOUT_MS = 2 * 60 * 1000L
        private const val MIN_USEFUL_DURATION_SECONDS = 45
        private const val MIN_USEFUL_DISTANCE_KM = 0.08
        private const val MIN_ACCEPTED_POINT_DISTANCE_METERS = 5f
        private const val MAX_STATIONARY_JITTER_METERS = 9f
        private const val MAX_POOR_ACCURACY_METERS = 45f
        private const val MAX_POOR_ACCURACY_INITIAL_METERS = 70f
        private const val LOW_SPEED_METERS_PER_SECOND = 1.5f
        private const val MAX_JUMP_DISTANCE_METERS = 180f
        private const val MAX_JUMP_SPEED_METERS_PER_SECOND = 55f
        private const val MIN_JUMP_TIME_DELTA_MS = 2_000L
        private const val MAX_JUMP_TIME_DELTA_MS = 20_000L
        private const val STAY_ZONE_EXIT_MARGIN_METERS = 60f
        private const val STAY_ZONE_VERIFY_DISTANCE_METERS = 120f
        private const val STAY_ZONE_VERIFY_MIN_SPEED_METERS_PER_SECOND = 3f

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
    private lateinit var geofencingClient: GeofencingClient
    private var currentSession: AutoTrackSession? = null
    private var activityTransitionsRegistered = false
    private var isLocationUpdatesActive = false
    private var requestedPriority = -1
    private var requestedIntervalMs = -1L
    private var requestedMinDistanceMeters = -1f
    private var stayAnchors: List<FrequentPlaceAnchor> = emptyList()
    private var insideAnchorIds: MutableSet<String> = mutableSetOf()
    private var pendingMovementVerification: PendingMovementVerification? = null

    private val finalizeRunnable = Runnable {
        finalizeCurrentSession()
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        currentSession = AutoTrackStorage.loadSession(this)
        stayAnchors = FrequentPlaceStorage.loadAnchors(this)
        insideAnchorIds = FrequentPlaceStorage.loadInsideAnchorIds(this).toMutableSet()
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

            ACTION_GEOFENCE_TRANSITION -> {
                ensureForeground()
                val transition = intent.getIntExtra(EXTRA_GEOFENCE_TRANSITION, -1)
                val ids = intent.getStringArrayListExtra(EXTRA_GEOFENCE_IDS).orEmpty()
                handleGeofenceTransition(ids, transition)
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
                refreshFrequentPlaces()
                registerActivityTransitionsIfPossible()
                registerStayGeofencesIfPossible()
                refreshInsideAnchorsFromLastKnownLocation()
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
                pendingMovementVerification = null
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
                if (shouldUseConservativeMovementVerification()) {
                    enterConservativeMovementVerification()
                } else {
                    beginActiveTracking()
                }
            }

            activityType == DetectedActivity.STILL &&
                transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                handler.removeCallbacks(finalizeRunnable)
                when {
                    currentSession != null -> {
                        AutoTrackStorage.saveUiState(this, AutoTrackUiState.PREPARING)
                        requestLocationUpdates(
                            priority = Priority.PRIORITY_HIGH_ACCURACY,
                            intervalMs = 5_000L,
                            minDistanceMeters = 6f
                        )
                        updateNotification()
                    }

                    shouldUseConservativeMovementVerification() -> {
                        enterConservativeMovementVerification()
                    }
                }
            }
        }
    }

    private fun shouldUseConservativeMovementVerification(): Boolean {
        return currentSession == null && insideAnchorIds.isNotEmpty()
    }

    private fun startOrResumeSession(updateMotionTimestamp: Boolean = true) {
        val now = System.currentTimeMillis()
        val updatedSession = (currentSession ?: AutoTrackSession(
            startTimestamp = now,
            lastMotionTimestamp = now,
            totalDistanceKm = 0.0
        )).copy(
            lastMotionTimestamp = if (updateMotionTimestamp) {
                now
            } else {
                currentSession?.lastMotionTimestamp ?: now
            }
        )
        currentSession = updatedSession
        AutoTrackStorage.saveSession(this, updatedSession)
    }

    private fun beginActiveTracking() {
        pendingMovementVerification = null
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.PREPARING)
        startOrResumeSession()
        requestLocationUpdates(
            priority = Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs = 5_000L,
            minDistanceMeters = 6f
        )
        updateNotification()
    }

    private fun enterConservativeMovementVerification() {
        pendingMovementVerification = PendingMovementVerification(
            startedAt = System.currentTimeMillis(),
            anchorIds = insideAnchorIds.toSet()
        )
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.PREPARING)
        requestLocationUpdates(
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs = 15_000L,
            minDistanceMeters = 18f
        )
        updateNotification(
            title = "\u5e38\u9a7b\u5730\u9644\u8fd1\u4f4e\u529f\u8017\u89c2\u5bdf\u4e2d",
            content = "\u68c0\u6d4b\u5230\u4f60\u53ef\u80fd\u8fd8\u5728\u5bb6\u6216\u516c\u53f8\u9644\u8fd1\uff0c\u4f1a\u5148\u7528\u66f4\u7701\u7535\u7684\u65b9\u5f0f\u786e\u8ba4\u662f\u5426\u771f\u6b63\u79bb\u5f00\u3002"
        )
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
        pendingMovementVerification = null
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
        refreshFrequentPlaces(historyItems)
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.SAVED_RECENTLY)
        updateNotification(
            title = "\u884c\u7a0b\u5df2\u81ea\u52a8\u4fdd\u5b58",
            content = "\u5df2\u4e3a\u4f60\u4fdd\u5b58\u6700\u65b0\u4e00\u6bb5\u51fa\u884c\uff0c\u5f53\u524d\u7ee7\u7eed\u5728\u540e\u53f0\u5b88\u5019\u3002"
        )
    }

    private fun handleLocationUpdate(location: Location) {
        if (pendingMovementVerification != null && currentSession == null) {
            handleConservativeVerificationLocation(location)
            return
        }

        startOrResumeSession(updateMotionTimestamp = false)
        appendLocationToCurrentSession(location)
    }

    private fun appendLocationToCurrentSession(location: Location) {
        val previousSession = currentSession ?: return
        val trackPoint = convertToGcj02TrackPoint(location)
        val noiseResult = evaluateTrackPoint(
            lastPoint = previousSession.points.lastOrNull(),
            candidatePoint = trackPoint,
            location = location
        )
        val points = when (noiseResult.action) {
            TrackNoiseAction.ACCEPT -> previousSession.points + requireNotNull(noiseResult.acceptedPoint)
            TrackNoiseAction.MERGE_STILL,
            TrackNoiseAction.DROP_DRIFT,
            TrackNoiseAction.DROP_JUMP -> previousSession.points
        }
        val acceptedPoint = noiseResult.acceptedPoint
        val totalDistance = previousSession.totalDistanceKm + (noiseResult.distanceMeters / 1000.0)

        val updatedSession = previousSession.copy(
            lastMotionTimestamp = if (noiseResult.action == TrackNoiseAction.ACCEPT) {
                System.currentTimeMillis()
            } else {
                previousSession.lastMotionTimestamp
            },
            totalDistanceKm = totalDistance,
            lastLatitude = acceptedPoint?.latitude ?: previousSession.lastLatitude,
            lastLongitude = acceptedPoint?.longitude ?: previousSession.lastLongitude,
            points = points
        )
        currentSession = updatedSession
        AutoTrackStorage.saveSession(this, updatedSession)
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.TRACKING)
        adaptLocationRequestForSpeed(location.speed)
        updateNotification()
    }

    private fun handleConservativeVerificationLocation(location: Location) {
        val verification = pendingMovementVerification ?: return
        val trackPoint = convertToGcj02TrackPoint(location)
        val originPoint = verification.originPoint ?: trackPoint
        val distanceFromOrigin = if (verification.originPoint == null) {
            0f
        } else {
            distanceBetween(originPoint, trackPoint)
        }
        val escapedStayZone = hasEscapedStayZone(trackPoint, verification.anchorIds)
        val confirmedMovingAway = escapedStayZone ||
            distanceFromOrigin >= STAY_ZONE_VERIFY_DISTANCE_METERS ||
            location.speed >= STAY_ZONE_VERIFY_MIN_SPEED_METERS_PER_SECOND

        if (confirmedMovingAway) {
            pendingMovementVerification = null
            beginActiveTracking()
            appendLocationToCurrentSession(location)
            return
        }

        pendingMovementVerification = verification.copy(originPoint = originPoint)
        val verificationDuration = System.currentTimeMillis() - verification.startedAt
        if (verificationDuration >= STAY_ZONE_VERIFY_TIMEOUT_MS) {
            pendingMovementVerification = null
            stopLocationUpdates()
            AutoTrackStorage.saveUiState(this, AutoTrackUiState.IDLE)
            updateNotification(
                title = "\u667a\u80fd\u8f68\u8ff9\u5df2\u5f00\u542f",
                content = "\u76ee\u524d\u4ecd\u5728\u5e38\u9a7b\u5730\u9644\u8fd1\uff0c\u7ee7\u7eed\u4fdd\u6301\u4f4e\u529f\u8017\u5f85\u547d\uff0c\u79bb\u5f00\u540e\u518d\u63d0\u9ad8\u7075\u654f\u5ea6\u3002"
            )
            return
        }

        requestLocationUpdates(
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs = 15_000L,
            minDistanceMeters = 18f
        )
        updateNotification(
            title = "\u5e38\u9a7b\u5730\u9644\u8fd1\u4f4e\u529f\u8017\u89c2\u5bdf\u4e2d",
            content = "\u76ee\u524d\u8fd8\u50cf\u662f\u5e38\u9a7b\u5730\u9644\u8fd1\u7684\u6d3b\u52a8\uff0c\u4f1a\u7b49\u786e\u8ba4\u79bb\u5f00\u540e\u518d\u5f00\u542f\u9ad8\u7cbe\u5ea6\u8f68\u8ff9\u3002"
        )
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

    private fun evaluateTrackPoint(
        lastPoint: TrackPoint?,
        candidatePoint: TrackPoint,
        location: Location
    ): TrackNoiseResult {
        val candidateAccuracy = candidatePoint.accuracyMeters ?: Float.MAX_VALUE
        val candidateSpeed = location.speed.takeIf { it >= 0f } ?: 0f

        if (lastPoint == null) {
            return if (candidateAccuracy > MAX_POOR_ACCURACY_INITIAL_METERS &&
                candidateSpeed <= LOW_SPEED_METERS_PER_SECOND
            ) {
                TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
            } else {
                TrackNoiseResult(
                    action = TrackNoiseAction.ACCEPT,
                    acceptedPoint = candidatePoint
                )
            }
        }

        val distanceMeters = distanceBetween(lastPoint, candidatePoint)
        val jitterThreshold = maxOf(
            MIN_ACCEPTED_POINT_DISTANCE_METERS,
            minOf(MAX_STATIONARY_JITTER_METERS, candidateAccuracy * 0.28f)
        )
        if (distanceMeters <= jitterThreshold && candidateSpeed <= LOW_SPEED_METERS_PER_SECOND) {
            return TrackNoiseResult(TrackNoiseAction.MERGE_STILL)
        }

        val driftDistanceThreshold = maxOf(20f, candidateAccuracy * 0.7f)
        if (candidateAccuracy >= MAX_POOR_ACCURACY_METERS &&
            candidateSpeed <= LOW_SPEED_METERS_PER_SECOND + 0.7f &&
            distanceMeters <= driftDistanceThreshold
        ) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
        }

        val timeDeltaMillis = candidatePoint.timestampMillis - lastPoint.timestampMillis
        if (timeDeltaMillis in MIN_JUMP_TIME_DELTA_MS..MAX_JUMP_TIME_DELTA_MS) {
            val inferredSpeed = distanceMeters / (timeDeltaMillis / 1000f)
            val reportedSpeedAllowance = maxOf(18f, candidateSpeed * 3.2f + 12f)
            if (distanceMeters >= maxOf(MAX_JUMP_DISTANCE_METERS, candidateAccuracy * 2.5f) &&
                inferredSpeed >= MAX_JUMP_SPEED_METERS_PER_SECOND &&
                inferredSpeed > reportedSpeedAllowance
            ) {
                return TrackNoiseResult(TrackNoiseAction.DROP_JUMP)
            }
        }

        if (distanceMeters < MIN_ACCEPTED_POINT_DISTANCE_METERS) {
            return TrackNoiseResult(TrackNoiseAction.MERGE_STILL)
        }

        return TrackNoiseResult(
            action = TrackNoiseAction.ACCEPT,
            distanceMeters = distanceMeters,
            acceptedPoint = candidatePoint
        )
    }

    private fun distanceBetween(first: TrackPoint, second: TrackPoint): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            first.latitude,
            first.longitude,
            second.latitude,
            second.longitude,
            result
        )
        return result[0]
    }

    private fun convertToGcj02TrackPoint(location: Location): TrackPoint {
        val coordinate = CoordinateTransformUtils.wgs84ToGcj02(
            latitude = location.latitude,
            longitude = location.longitude
        )
        return TrackPoint(
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }
        )
    }

    private fun hasEscapedStayZone(trackPoint: TrackPoint, anchorIds: Set<String>): Boolean {
        val relevantAnchors = stayAnchors.filter { it.id in anchorIds }
        if (relevantAnchors.isEmpty()) return true
        return relevantAnchors.none { anchor ->
            distanceBetween(
                anchor.latitude,
                anchor.longitude,
                trackPoint.latitude,
                trackPoint.longitude
            ) <= anchor.radiusMeters + STAY_ZONE_EXIT_MARGIN_METERS
        }
    }

    private fun handleGeofenceTransition(ids: List<String>, transition: Int) {
        if (ids.isEmpty()) return
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> insideAnchorIds.addAll(ids)
            Geofence.GEOFENCE_TRANSITION_EXIT -> insideAnchorIds.removeAll(ids.toSet())
            else -> return
        }
        insideAnchorIds.retainAll(stayAnchors.map { it.id }.toSet())
        FrequentPlaceStorage.saveInsideAnchorIds(this, insideAnchorIds)

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER && currentSession == null && pendingMovementVerification == null) {
            stopLocationUpdates()
            AutoTrackStorage.saveUiState(this, AutoTrackUiState.IDLE)
            updateNotification(
                title = "\u667a\u80fd\u8f68\u8ff9\u5df2\u5f00\u542f",
                content = "\u5df2\u8fdb\u5165\u5e38\u9a7b\u5730\u533a\u57df\uff0c\u5207\u6362\u4e3a\u66f4\u7701\u7535\u7684\u5f85\u547d\u7b56\u7565\u3002"
            )
        }
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

    @SuppressLint("MissingPermission")
    private fun registerStayGeofencesIfPossible() {
        if (!hasLocationPermission()) return
        geofencingClient.removeGeofences(geofencePendingIntent())
        if (stayAnchors.isEmpty()) return

        val geofences = stayAnchors.map { anchor ->
            Geofence.Builder()
                .setRequestId(anchor.id)
                .setCircularRegion(anchor.latitude, anchor.longitude, anchor.radiusMeters)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setNotificationResponsiveness(2 * 60 * 1000)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent())
            .addOnFailureListener { exception ->
                val code = (exception as? com.google.android.gms.common.api.ApiException)?.statusCode
                if (code == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                    stopLocationUpdates()
                }
            }
    }

    private fun unregisterActivityTransitions() {
        if (!activityTransitionsRegistered) return
        activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent())
        activityTransitionsRegistered = false
    }

    private fun unregisterStayGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent())
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

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceTransitionReceiver::class.java).apply {
            action = ACTION_GEOFENCE_TRANSITION
        }
        return PendingIntent.getBroadcast(
            this,
            2203,
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
        pendingMovementVerification = null
        stopLocationUpdates()
        unregisterActivityTransitions()
        unregisterStayGeofences()
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

    private fun refreshFrequentPlaces(historyItems: List<HistoryItem> = HistoryStorage.load(this)) {
        stayAnchors = FrequentPlaceDetector.buildAnchors(historyItems)
        FrequentPlaceStorage.saveAnchors(this, stayAnchors)
        insideAnchorIds.retainAll(stayAnchors.map { it.id }.toSet())
        FrequentPlaceStorage.saveInsideAnchorIds(this, insideAnchorIds)
        registerStayGeofencesIfPossible()
    }

    @SuppressLint("MissingPermission")
    private fun refreshInsideAnchorsFromLastKnownLocation() {
        if (!hasLocationPermission() || stayAnchors.isEmpty()) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) return@addOnSuccessListener
            val trackPoint = convertToGcj02TrackPoint(location)
            val currentInsideIds = stayAnchors.filter { anchor ->
                distanceBetween(
                    anchor.latitude,
                    anchor.longitude,
                    trackPoint.latitude,
                    trackPoint.longitude
                ) <= anchor.radiusMeters
            }.map { it.id }.toMutableSet()
            insideAnchorIds = currentInsideIds
            FrequentPlaceStorage.saveInsideAnchorIds(this, insideAnchorIds)
        }
    }

    private fun distanceBetween(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            firstLatitude,
            firstLongitude,
            secondLatitude,
            secondLongitude,
            result
        )
        return result[0]
    }
}
