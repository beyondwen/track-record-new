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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.FrequentPlaceAnchor
import com.wenhao.record.data.tracking.FrequentPlaceStorage
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.CoordinateTransformUtils
import com.wenhao.record.ui.main.MainActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

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

    companion object {
        const val ACTION_START = "com.wenhao.record.action.START_BACKGROUND_TRACKING"
        const val ACTION_STOP = "com.wenhao.record.action.STOP_BACKGROUND_TRACKING"

        private const val CHANNEL_ID = "smart_tracking"
        private const val NOTIFICATION_ID = 1107

        private const val STILL_STOP_DELAY_MS = 2 * 60 * 1000L
        private const val STOPPING_CONFIRM_DELAY_MS = 75_000L
        private const val SUSPECT_MOVING_TIMEOUT_MS = 45_000L
        private const val MAX_LAST_KNOWN_LOCATION_AGE_MS = 10 * 60 * 1000L
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

        private const val IDLE_INTERVAL_MS = 45_000L
        private const val IDLE_INTERVAL_ANCHOR_MS = 90_000L
        private const val IDLE_MIN_DISTANCE_METERS = 30f
        private const val IDLE_MIN_DISTANCE_ANCHOR_METERS = 65f
        private const val SUSPECT_INTERVAL_MS = 12_000L
        private const val SUSPECT_MIN_DISTANCE_METERS = 10f
        private const val STOPPING_INTERVAL_MS = 18_000L
        private const val STOPPING_MIN_DISTANCE_METERS = 8f
        private const val ACTIVE_FAST_INTERVAL_MS = 2_500L
        private const val ACTIVE_NORMAL_INTERVAL_MS = 4_000L
        private const val ACTIVE_SLOW_INTERVAL_MS = 6_500L

        private const val IDLE_TRIGGER_DISTANCE_METERS = 85f
        private const val IDLE_TRIGGER_DISTANCE_ANCHOR_METERS = 120f
        private const val IDLE_ASSIST_DISTANCE_METERS = 48f
        private const val IDLE_ASSIST_DISTANCE_ANCHOR_METERS = 72f
        private const val IDLE_TRIGGER_SPEED_METERS_PER_SECOND = 2.2f
        private const val IDLE_TRIGGER_INFERRED_SPEED_METERS_PER_SECOND = 1.25f
        private const val MAX_IDLE_LOCATION_AGE_MS = 2 * 60 * 1000L
        private const val MAX_IDLE_ACCURACY_METERS = 80f
        private const val IDLE_BASELINE_RESET_GAP_MS = 4 * 60 * 1000L
        private const val IDLE_STATIONARY_RESET_DISTANCE_METERS = 18f

        private const val ACCELERATION_WINDOW_SIZE = 12

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
    private val motionConfidenceEngine = MotionConfidenceEngine()
    private val wifiStayContext = WifiStayContext()
    private val accelerationWindow = ArrayDeque<Float>()
    private val locationListener = android.location.LocationListener(::handleLocationUpdate)
    private val finalizeRunnable = Runnable { finalizeCurrentSession() }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> handleAccelerometerChanged(event.values)
                Sensor.TYPE_STEP_COUNTER -> handleStepCounterChanged(event.values.firstOrNull())
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            motionConfidenceEngine.noteSignificantMotion(System.currentTimeMillis())
            maybePromoteToSuspect("检测到显著运动")
            registerSignificantMotionSensor(true)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshWifiSnapshot()
        }

        override fun onLost(network: Network) {
            refreshWifiSnapshot()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshWifiSnapshot()
        }
    }

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager

    private var stepCounterSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var significantMotionSensor: Sensor? = null

    private var accelerometerRegistered = false
    private var stepCounterRegistered = false
    private var significantMotionRegistered = false
    private var currentAccelerometerDelay = -1
    private var networkCallbackRegistered = false

    private var currentSession: AutoTrackSession? = null
    private var currentPhase = TrackingPhase.IDLE
    private var phaseEnteredAt = 0L
    private var isLocationUpdatesActive = false
    private var requestedHighAccuracy = false
    private var requestedIntervalMs = -1L
    private var requestedMinDistanceMeters = -1f
    private var stayAnchors: List<FrequentPlaceAnchor> = emptyList()
    private var insideAnchorIds: MutableSet<String> = mutableSetOf()
    private var lastIdleScoutPoint: TrackPoint? = null
    private var lastIdleScoutSamplePoint: TrackPoint? = null
    private var currentWifiSnapshot = WifiSnapshot(false)

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        significantMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        currentSession = AutoTrackStorage.loadSession(this)
        stayAnchors = FrequentPlaceStorage.loadAnchors(this)
        insideAnchorIds = FrequentPlaceStorage.loadInsideAnchorIds(this).toMutableSet()
        registerNetworkCallback()
        createNotificationChannelClean()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AutoTrackStorage.setAutoTrackingEnabled(this, false)
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.DISABLED)
                AutoTrackDiagnosticsStorage.markServiceStatus(this, "已关闭", "后台智能记录已停止")
                stopAllTracking(clearSession = false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                ensureForeground()
                AutoTrackStorage.setAutoTrackingEnabled(this, true)
                refreshFrequentPlaces()
                refreshInsideAnchorsFromLastKnownLocation()
                refreshWifiSnapshot()
                if (currentSession != null) {
                    motionConfidenceEngine.onActiveEntered()
                    enterPhase(TrackingPhase.ACTIVE, "已恢复上一次未结束的行程", emitEvent = true)
                    scheduleFinalizeForCurrentSession()
                } else {
                    enterPhase(TrackingPhase.IDLE, "后台等待自动识别出行", emitEvent = true)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAllTracking(clearSession = false)
        AutoTrackDiagnosticsStorage.flush(this)
        unregisterNetworkCallback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun enterPhase(phase: TrackingPhase, reason: String, emitEvent: Boolean) {
        currentPhase = phase
        phaseEnteredAt = System.currentTimeMillis()
        if (emitEvent) {
            AutoTrackDiagnosticsStorage.markEvent(this, "${phaseLabel(phase)}：$reason")
        }
        when (phase) {
            TrackingPhase.IDLE -> {
                motionConfidenceEngine.onIdleEntered()
                lastIdleScoutPoint = null
                lastIdleScoutSamplePoint = null
                requestIdleLocationUpdates()
                seedIdleScoutBaselineFromLastKnownLocation()
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.IDLE)
                AutoTrackDiagnosticsStorage.markServiceStatus(this, phaseLabel(phase), reason)
                updateNotification(
                    title = "智能轨迹已开启",
                    content = "正在后台低功耗待命，检测到移动后会自动开始。"
                )
            }

            TrackingPhase.SUSPECT_MOVING -> {
                requestSuspectLocationUpdates()
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.PREPARING)
                AutoTrackDiagnosticsStorage.markServiceStatus(this, phaseLabel(phase), reason)
                updateNotification(
                    title = "正在确认是否开始出行",
                    content = "检测到运动迹象，正在提升采样频率确认是否出行。"
                )
            }

            TrackingPhase.ACTIVE -> {
                motionConfidenceEngine.onActiveEntered()
                requestActiveLocationUpdates(speedMetersPerSecond = null)
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.TRACKING)
                AutoTrackDiagnosticsStorage.markServiceStatus(this, phaseLabel(phase), reason)
                updateNotification()
            }

            TrackingPhase.SUSPECT_STOPPING -> {
                requestStoppingLocationUpdates()
                AutoTrackStorage.saveUiState(this, AutoTrackUiState.PAUSED_STILL)
                AutoTrackDiagnosticsStorage.markServiceStatus(this, phaseLabel(phase), reason)
                updateNotification(
                    title = "正在判断本次行程是否结束",
                    content = "检测到你可能已经停止移动，若持续静止将自动保存。"
                )
            }
        }
        updateSensorRegistration()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(highAccuracy: Boolean, intervalMs: Long, minDistanceMeters: Float) {
        if (!hasLocationPermission()) return
        if (isLocationUpdatesActive &&
            requestedHighAccuracy == highAccuracy &&
            requestedIntervalMs == intervalMs &&
            requestedMinDistanceMeters == minDistanceMeters
        ) {
            return
        }

        val providers = collectEnabledProviders(highAccuracy)
        if (providers.isEmpty()) {
            stopLocationUpdates()
            AutoTrackStorage.saveUiState(this, AutoTrackUiState.WAITING_PERMISSION)
            AutoTrackDiagnosticsStorage.markServiceStatus(this, "定位服务未开启", "请打开系统定位后继续后台记录")
            updateNotification(
                title = "后台记录等待定位服务",
                content = "系统定位当前不可用，打开定位后会继续自动记录。"
            )
            return
        }

        locationManager.removeUpdates(locationListener)
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                intervalMs,
                minDistanceMeters,
                locationListener,
                Looper.getMainLooper()
            )
        }
        isLocationUpdatesActive = true
        requestedHighAccuracy = highAccuracy
        requestedIntervalMs = intervalMs
        requestedMinDistanceMeters = minDistanceMeters
    }

    private fun stopLocationUpdates() {
        if (!isLocationUpdatesActive) return
        locationManager.removeUpdates(locationListener)
        isLocationUpdatesActive = false
        requestedHighAccuracy = false
        requestedIntervalMs = -1L
        requestedMinDistanceMeters = -1f
    }

    private fun requestIdleLocationUpdates() {
        val insideAnchor = insideAnchorIds.isNotEmpty()
        requestLocationUpdates(
            highAccuracy = false,
            intervalMs = if (insideAnchor) IDLE_INTERVAL_ANCHOR_MS else IDLE_INTERVAL_MS,
            minDistanceMeters = if (insideAnchor) IDLE_MIN_DISTANCE_ANCHOR_METERS else IDLE_MIN_DISTANCE_METERS
        )
    }

    private fun requestSuspectLocationUpdates() {
        val sameAnchorWifi = wifiStayContext.isSameAnchorWifi(currentWifiSnapshot, insideAnchorIds.isNotEmpty())
        requestLocationUpdates(
            highAccuracy = !sameAnchorWifi,
            intervalMs = SUSPECT_INTERVAL_MS,
            minDistanceMeters = SUSPECT_MIN_DISTANCE_METERS
        )
    }

    private fun requestStoppingLocationUpdates() {
        requestLocationUpdates(
            highAccuracy = false,
            intervalMs = STOPPING_INTERVAL_MS,
            minDistanceMeters = STOPPING_MIN_DISTANCE_METERS
        )
    }

    private fun requestActiveLocationUpdates(speedMetersPerSecond: Float?) {
        when {
            speedMetersPerSecond != null && speedMetersPerSecond >= 10f -> {
                requestLocationUpdates(true, ACTIVE_FAST_INTERVAL_MS, 12f)
            }

            speedMetersPerSecond != null && speedMetersPerSecond >= 3f -> {
                requestLocationUpdates(true, ACTIVE_NORMAL_INTERVAL_MS, 8f)
            }

            else -> {
                requestLocationUpdates(true, ACTIVE_SLOW_INTERVAL_MS, 5f)
            }
        }
    }

    private fun handleLocationUpdate(location: Location) {
        updateInsideAnchorsFromLocation(location)
        if (currentSession == null) {
            handleIdleOrSuspectLocation(location)
        } else {
            handleActiveLocation(location)
        }
    }

    private fun handleIdleOrSuspectLocation(location: Location) {
        val now = System.currentTimeMillis()
        if (isLocationTooOld(location)) {
            AutoTrackDiagnosticsStorage.markLocationDecision(
                this,
                "低功耗探测忽略过旧缓存定位",
                0,
                location.accuracy.takeIf { location.hasAccuracy() }
            )
            return
        }
        if (location.hasAccuracy() && location.accuracy > MAX_IDLE_ACCURACY_METERS) {
            AutoTrackDiagnosticsStorage.markLocationDecision(
                this,
                "低功耗探测忽略低质量定位",
                0,
                location.accuracy
            )
            return
        }

        val scoutPoint = convertToGcj02TrackPoint(location)
        val baselinePoint = lastIdleScoutPoint
        val previousScoutPoint = lastIdleScoutSamplePoint

        if (baselinePoint == null) {
            lastIdleScoutPoint = scoutPoint
            lastIdleScoutSamplePoint = scoutPoint
            AutoTrackDiagnosticsStorage.markLocationDecision(
                this,
                "低功耗探测已建立起点",
                0,
                location.accuracy.takeIf { location.hasAccuracy() }
            )
            return
        }

        if (previousScoutPoint != null &&
            scoutPoint.timestampMillis - previousScoutPoint.timestampMillis > IDLE_BASELINE_RESET_GAP_MS
        ) {
            lastIdleScoutPoint = scoutPoint
            lastIdleScoutSamplePoint = scoutPoint
            AutoTrackDiagnosticsStorage.markLocationDecision(
                this,
                "低功耗探测刷新了观察基准点",
                0,
                location.accuracy.takeIf { location.hasAccuracy() }
            )
            return
        }

        val movedDistanceMeters = distanceBetween(baselinePoint, scoutPoint)
        val sampleDistanceMeters = previousScoutPoint?.let { distanceBetween(it, scoutPoint) } ?: movedDistanceMeters
        val baselineAccuracy = baselinePoint.accuracyMeters ?: 0f
        val scoutAccuracy = scoutPoint.accuracyMeters ?: 0f
        val effectiveDistanceMeters = max(
            0f,
            movedDistanceMeters - ((baselineAccuracy + scoutAccuracy) * 0.5f)
        )
        val timeDeltaMillis = scoutPoint.timestampMillis - baselinePoint.timestampMillis
        val inferredSpeedMetersPerSecond = if (timeDeltaMillis > 0L) {
            movedDistanceMeters / (timeDeltaMillis / 1000f)
        } else {
            0f
        }
        val motionSnapshot = buildMotionSnapshot(
            effectiveDistanceMeters = effectiveDistanceMeters,
            reportedSpeedMetersPerSecond = location.speed.takeIf { it >= 0f } ?: 0f,
            inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
            poorAccuracy = location.accuracy > 35f,
            timestampMillis = now
        )
        val sameAnchorWifi = wifiStayContext.isSameAnchorWifi(currentWifiSnapshot, insideAnchorIds.isNotEmpty())
        val triggerDistanceMeters = if (insideAnchorIds.isNotEmpty()) {
            if (sameAnchorWifi) IDLE_TRIGGER_DISTANCE_ANCHOR_METERS + 20f else IDLE_TRIGGER_DISTANCE_ANCHOR_METERS
        } else {
            IDLE_TRIGGER_DISTANCE_METERS
        }
        val assistedTriggerDistanceMeters = if (insideAnchorIds.isNotEmpty()) {
            if (sameAnchorWifi) IDLE_ASSIST_DISTANCE_ANCHOR_METERS + 12f else IDLE_ASSIST_DISTANCE_ANCHOR_METERS
        } else {
            IDLE_ASSIST_DISTANCE_METERS
        }
        val maxObservedSpeed = max(location.speed.takeIf { it >= 0f } ?: 0f, inferredSpeedMetersPerSecond)
        val movingConfirmed = motionSnapshot.stronglyMoving ||
            effectiveDistanceMeters >= triggerDistanceMeters ||
            maxObservedSpeed >= IDLE_TRIGGER_SPEED_METERS_PER_SECOND ||
            (effectiveDistanceMeters >= assistedTriggerDistanceMeters &&
                motionSnapshot.movingLikely &&
                inferredSpeedMetersPerSecond >= IDLE_TRIGGER_INFERRED_SPEED_METERS_PER_SECOND)

        if (currentPhase == TrackingPhase.IDLE && motionSnapshot.movingLikely) {
            enterPhase(
                TrackingPhase.SUSPECT_MOVING,
                "检测到运动迹象，${motionSnapshot.summary}",
                emitEvent = true
            )
        }

        if (movingConfirmed) {
            beginActiveTracking("已确认开始移动，${motionSnapshot.summary}", location)
            return
        }

        if (sampleDistanceMeters <= IDLE_STATIONARY_RESET_DISTANCE_METERS &&
            maxObservedSpeed < 0.8f &&
            !motionSnapshot.movingLikely
        ) {
            lastIdleScoutPoint = scoutPoint
        }
        lastIdleScoutSamplePoint = scoutPoint

        if (currentPhase == TrackingPhase.SUSPECT_MOVING &&
            now - phaseEnteredAt >= SUSPECT_MOVING_TIMEOUT_MS &&
            !motionSnapshot.movingLikely
        ) {
            enterPhase(TrackingPhase.IDLE, "本轮移动证据不足，恢复低功耗待命", emitEvent = true)
            return
        }

        AutoTrackDiagnosticsStorage.markLocationDecision(
            this,
            "${phaseLabel(currentPhase)} / ${motionSnapshot.summary} / 有效位移 ${effectiveDistanceMeters.toInt()} 米",
            0,
            location.accuracy.takeIf { location.hasAccuracy() }
        )
    }

    private fun beginActiveTracking(reason: String, firstLocation: Location?) {
        val now = System.currentTimeMillis()
        val session = currentSession ?: AutoTrackSession(
            startTimestamp = now,
            lastMotionTimestamp = now,
            totalDistanceKm = 0.0
        )
        currentSession = session.copy(lastMotionTimestamp = now)
        AutoTrackStorage.saveSession(this, currentSession!!)
        enterPhase(TrackingPhase.ACTIVE, reason, emitEvent = true)
        firstLocation?.let(::appendLocationToCurrentSession)
    }

    private fun handleActiveLocation(location: Location) {
        val now = System.currentTimeMillis()
        val motionSnapshot = buildMotionSnapshot(
            effectiveDistanceMeters = 0f,
            reportedSpeedMetersPerSecond = location.speed.takeIf { it >= 0f } ?: 0f,
            inferredSpeedMetersPerSecond = 0f,
            poorAccuracy = location.accuracy > 35f,
            timestampMillis = now
        )

        if (currentPhase == TrackingPhase.SUSPECT_STOPPING && motionSnapshot.movingLikely) {
            enterPhase(TrackingPhase.ACTIVE, "检测到再次移动，继续记录", emitEvent = true)
        }

        appendLocationToCurrentSession(location)

        val session = currentSession ?: return
        val quietForMillis = now - session.lastMotionTimestamp
        val sensorsQuiet = !motionConfidenceEngine.hasRecentAccelerationMotion(now) &&
            !motionConfidenceEngine.hasRecentStepMovement(now) &&
            !motionConfidenceEngine.hasRecentSignificantMotion(now)
        val lowSpeed = (location.speed.takeIf { it >= 0f } ?: 0f) < 0.9f

        when {
            currentPhase == TrackingPhase.ACTIVE &&
                quietForMillis >= STOPPING_CONFIRM_DELAY_MS &&
                sensorsQuiet &&
                lowSpeed -> {
                enterPhase(TrackingPhase.SUSPECT_STOPPING, "速度和传感器都趋于静止，开始观察是否结束", emitEvent = true)
            }

            currentPhase == TrackingPhase.SUSPECT_STOPPING &&
                quietForMillis < STOPPING_CONFIRM_DELAY_MS / 2 -> {
                enterPhase(TrackingPhase.ACTIVE, "检测到新的运动证据，恢复正常记录", emitEvent = true)
            }
        }
    }

    private fun appendLocationToCurrentSession(location: Location) {
        val previousSession = currentSession ?: return
        val trackPoint = convertToGcj02TrackPoint(location)
        val noiseResult = evaluateTrackPoint(
            lastPoint = previousSession.points.lastOrNull(),
            candidatePoint = trackPoint,
            location = location
        )
        val smoothedPoint = noiseResult.acceptedPoint?.let { accepted ->
            AdaptiveTrackSmoother.smooth(
                previousPoint = previousSession.points.lastOrNull(),
                candidatePoint = accepted,
                speedMetersPerSecond = location.speed.takeIf { it >= 0f } ?: 0f,
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }
            )
        }
        val addedDistanceMeters = when {
            smoothedPoint == null -> 0f
            previousSession.points.isEmpty() -> 0f
            else -> distanceBetween(previousSession.points.last(), smoothedPoint)
        }
        val points = when (noiseResult.action) {
            TrackNoiseAction.ACCEPT -> previousSession.points + requireNotNull(smoothedPoint)
            TrackNoiseAction.MERGE_STILL,
            TrackNoiseAction.DROP_DRIFT,
            TrackNoiseAction.DROP_JUMP -> previousSession.points
        }

        val updatedSession = previousSession.copy(
            lastMotionTimestamp = if (noiseResult.action == TrackNoiseAction.ACCEPT) {
                System.currentTimeMillis()
            } else {
                previousSession.lastMotionTimestamp
            },
            totalDistanceKm = previousSession.totalDistanceKm + (addedDistanceMeters / 1000.0),
            lastLatitude = smoothedPoint?.latitude ?: previousSession.lastLatitude,
            lastLongitude = smoothedPoint?.longitude ?: previousSession.lastLongitude,
            points = points
        )
        currentSession = updatedSession
        AutoTrackStorage.saveSession(this, updatedSession)
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.TRACKING)
        AutoTrackDiagnosticsStorage.markServiceStatus(this, phaseLabel(currentPhase))
        AutoTrackDiagnosticsStorage.markLocationDecision(
            this,
            "${phaseLabel(currentPhase)} / ${locationDecisionLabel(noiseResult.action)}",
            updatedSession.points.size,
            location.accuracy.takeIf { location.hasAccuracy() }
        )

        if (noiseResult.action == TrackNoiseAction.ACCEPT) {
            scheduleFinalizeForCurrentSession()
        }

        requestActiveLocationUpdates(location.speed.takeIf { it >= 0f })
        updateNotification()
    }

    private fun scheduleFinalizeForCurrentSession() {
        val session = currentSession ?: return
        handler.removeCallbacks(finalizeRunnable)
        val delayMs = max(15_000L, session.lastMotionTimestamp + STILL_STOP_DELAY_MS - System.currentTimeMillis())
        handler.postDelayed(finalizeRunnable, delayMs)
    }

    private fun finalizeCurrentSession() {
        val session = currentSession ?: return
        handler.removeCallbacks(finalizeRunnable)
        currentSession = null
        AutoTrackStorage.clearSession(this)
        stopLocationUpdates()

        val durationSeconds = currentSessionDurationSeconds(session)
        if (durationSeconds < MIN_USEFUL_DURATION_SECONDS && session.totalDistanceKm < MIN_USEFUL_DISTANCE_KM) {
            AutoTrackStorage.saveUiState(this, AutoTrackUiState.IDLE)
            AutoTrackDiagnosticsStorage.markSessionDiscarded(
                this,
                "本次行程未保存：时长 ${durationSeconds} 秒，距离 ${formatDistance(session.totalDistanceKm)}，低于保存阈值"
            )
            enterPhase(TrackingPhase.IDLE, "本段行程过短，已恢复待命", emitEvent = true)
            return
        }

        val averageSpeedKmh = if (durationSeconds > 0) {
            session.totalDistanceKm / (durationSeconds / 3600.0)
        } else {
            0.0
        }
        val historyItem = HistoryItem(
            id = HistoryStorage.nextHistoryId(this),
            timestamp = session.startTimestamp,
            distanceKm = session.totalDistanceKm,
            durationSeconds = durationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            points = session.points
        )
        HistoryStorage.add(this, historyItem)
        refreshFrequentPlaces()
        AutoTrackStorage.saveUiState(this, AutoTrackUiState.SAVED_RECENTLY)
        AutoTrackDiagnosticsStorage.markSessionSaved(
            this,
            "已保存 ${formatDistance(session.totalDistanceKm)} / ${durationSeconds / 60} 分钟"
        )
        enterPhase(TrackingPhase.IDLE, "本次行程已自动保存，继续后台待命", emitEvent = true)
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
                TrackNoiseResult(TrackNoiseAction.ACCEPT, acceptedPoint = candidatePoint)
            }
        }

        val distanceMeters = distanceBetween(lastPoint, candidatePoint)
        val jitterThreshold = max(
            MIN_ACCEPTED_POINT_DISTANCE_METERS,
            minOf(MAX_STATIONARY_JITTER_METERS, candidateAccuracy * 0.28f)
        )
        if (distanceMeters <= jitterThreshold && candidateSpeed <= LOW_SPEED_METERS_PER_SECOND) {
            return TrackNoiseResult(TrackNoiseAction.MERGE_STILL)
        }

        val driftDistanceThreshold = max(20f, candidateAccuracy * 0.7f)
        if (candidateAccuracy >= MAX_POOR_ACCURACY_METERS &&
            candidateSpeed <= LOW_SPEED_METERS_PER_SECOND + 0.7f &&
            distanceMeters <= driftDistanceThreshold
        ) {
            return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
        }

        val timeDeltaMillis = candidatePoint.timestampMillis - lastPoint.timestampMillis
        if (timeDeltaMillis in MIN_JUMP_TIME_DELTA_MS..MAX_JUMP_TIME_DELTA_MS) {
            val inferredSpeed = distanceMeters / (timeDeltaMillis / 1000f)
            val reportedSpeedAllowance = max(18f, candidateSpeed * 3.2f + 12f)
            if (distanceMeters >= max(MAX_JUMP_DISTANCE_METERS, candidateAccuracy * 2.5f) &&
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

    private fun buildMotionSnapshot(
        effectiveDistanceMeters: Float,
        reportedSpeedMetersPerSecond: Float,
        inferredSpeedMetersPerSecond: Float,
        poorAccuracy: Boolean,
        timestampMillis: Long
    ): MotionScoreSnapshot {
        return motionConfidenceEngine.evaluate(
            nowMillis = timestampMillis,
            signals = MotionSignals(
                stepDelta = motionConfidenceEngine.currentStepDelta(),
                effectiveDistanceMeters = effectiveDistanceMeters,
                reportedSpeedMetersPerSecond = reportedSpeedMetersPerSecond,
                inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
                insideAnchor = insideAnchorIds.isNotEmpty(),
                sameAnchorWifi = wifiStayContext.isSameAnchorWifi(currentWifiSnapshot, insideAnchorIds.isNotEmpty()),
                poorAccuracy = poorAccuracy
            )
        )
    }

    private fun maybePromoteToSuspect(reason: String) {
        val snapshot = buildMotionSnapshot(
            effectiveDistanceMeters = 0f,
            reportedSpeedMetersPerSecond = 0f,
            inferredSpeedMetersPerSecond = 0f,
            poorAccuracy = false,
            timestampMillis = System.currentTimeMillis()
        )
        when {
            currentSession != null && currentPhase == TrackingPhase.SUSPECT_STOPPING && snapshot.movingLikely -> {
                enterPhase(TrackingPhase.ACTIVE, "$reason，恢复正常记录", emitEvent = true)
            }

            currentSession == null && currentPhase == TrackingPhase.IDLE && snapshot.movingLikely -> {
                enterPhase(TrackingPhase.SUSPECT_MOVING, "$reason，${snapshot.summary}", emitEvent = true)
            }
        }
    }

    private fun updateSensorRegistration() {
        val accelerometerDelay = when (currentPhase) {
            TrackingPhase.SUSPECT_MOVING,
            TrackingPhase.SUSPECT_STOPPING -> SensorManager.SENSOR_DELAY_UI
            TrackingPhase.ACTIVE,
            TrackingPhase.IDLE -> SensorManager.SENSOR_DELAY_NORMAL
        }
        registerAccelerometerIfNeeded(accelerometerDelay)
        registerStepCounterIfNeeded(enable = hasActivityRecognitionPermission())
        registerSignificantMotionSensor(enable = hasActivityRecognitionPermission())
    }

    private fun registerAccelerometerIfNeeded(delay: Int) {
        val sensor = accelerometerSensor ?: return
        if (accelerometerRegistered && currentAccelerometerDelay == delay) return
        if (accelerometerRegistered) {
            sensorManager.unregisterListener(sensorListener, sensor)
        }
        sensorManager.registerListener(sensorListener, sensor, delay)
        accelerometerRegistered = true
        currentAccelerometerDelay = delay
    }

    private fun registerStepCounterIfNeeded(enable: Boolean) {
        val sensor = stepCounterSensor ?: return
        if (enable && !stepCounterRegistered) {
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            stepCounterRegistered = true
        } else if (!enable && stepCounterRegistered) {
            sensorManager.unregisterListener(sensorListener, sensor)
            stepCounterRegistered = false
        }
    }

    private fun registerSignificantMotionSensor(enable: Boolean) {
        val sensor = significantMotionSensor ?: return
        if (enable && !significantMotionRegistered) {
            significantMotionRegistered = sensorManager.requestTriggerSensor(significantMotionListener, sensor)
        } else if (!enable && significantMotionRegistered) {
            sensorManager.cancelTriggerSensor(significantMotionListener, sensor)
            significantMotionRegistered = false
        }
    }

    private fun handleAccelerometerChanged(values: FloatArray) {
        if (values.size < 3) return
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val linearAcceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)
        accelerationWindow.addLast(linearAcceleration)
        if (accelerationWindow.size > ACCELERATION_WINDOW_SIZE) {
            accelerationWindow.removeFirst()
        }
        if (accelerationWindow.size < ACCELERATION_WINDOW_SIZE) return

        val average = accelerationWindow.average().toFloat()
        val variance = accelerationWindow
            .map { (it - average) * (it - average) }
            .average()
            .toFloat()
        motionConfidenceEngine.noteAccelerationVariance(variance, System.currentTimeMillis())
        if (variance >= 0.85f) {
            maybePromoteToSuspect("加速度波动明显")
        }
    }

    private fun handleStepCounterChanged(totalSteps: Float?) {
        totalSteps ?: return
        val delta = motionConfidenceEngine.noteStepCount(totalSteps, System.currentTimeMillis())
        if (delta <= 0) return
        when {
            currentSession != null && currentPhase == TrackingPhase.SUSPECT_STOPPING -> {
                enterPhase(TrackingPhase.ACTIVE, "步数再次增加，恢复轨迹记录", emitEvent = true)
            }

            currentSession == null -> {
                maybePromoteToSuspect("检测到步数变化")
            }
        }
    }

    private fun refreshFrequentPlaces(historyItems: List<HistoryItem> = HistoryStorage.load(this)) {
        stayAnchors = FrequentPlaceDetector.buildAnchors(historyItems)
        FrequentPlaceStorage.saveAnchors(this, stayAnchors)
        val retainedIds = insideAnchorIds.filterTo(mutableSetOf()) { currentId ->
            stayAnchors.any { it.id == currentId }
        }
        persistInsideAnchorIdsIfChanged(retainedIds)
    }

    private fun refreshInsideAnchorsFromLastKnownLocation() {
        loadBestLastKnownLocation()?.let(::updateInsideAnchorsFromLocation)
    }

    private fun updateInsideAnchorsFromLocation(location: Location) {
        if (stayAnchors.isEmpty()) {
            persistInsideAnchorIdsIfChanged(emptySet())
            return
        }
        val point = convertToGcj02TrackPoint(location)
        val updatedIds = stayAnchors.filter { anchor ->
            distanceBetween(anchor.latitude, anchor.longitude, point.latitude, point.longitude) <= anchor.radiusMeters
        }.map { it.id }.toSet()
        persistInsideAnchorIdsIfChanged(updatedIds)
        wifiStayContext.update(currentWifiSnapshot, insideAnchorIds.isNotEmpty())
    }

    private fun persistInsideAnchorIdsIfChanged(newIds: Set<String>) {
        if (insideAnchorIds == newIds) return
        insideAnchorIds = newIds.toMutableSet()
        FrequentPlaceStorage.saveInsideAnchorIds(this, insideAnchorIds)
    }

    private fun seedIdleScoutBaselineFromLastKnownLocation() {
        if (lastIdleScoutPoint != null) return
        val lastKnownLocation = loadBestLastKnownLocation() ?: return
        if (isLocationTooOld(lastKnownLocation)) return
        if (lastKnownLocation.hasAccuracy() && lastKnownLocation.accuracy > MAX_IDLE_ACCURACY_METERS) return
        val baseline = convertToGcj02TrackPoint(lastKnownLocation)
        lastIdleScoutPoint = baseline
        lastIdleScoutSamplePoint = baseline
        AutoTrackDiagnosticsStorage.markLocationDecision(
            this,
            "低功耗探测已加载最近定位作为基准",
            0,
            lastKnownLocation.accuracy.takeIf { lastKnownLocation.hasAccuracy() }
        )
    }

    @SuppressLint("MissingPermission")
    private fun loadBestLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return LocationSelectionUtils.loadBestLastKnownLocation(
            locationManager = locationManager,
            hasFineLocation = hasFineLocation,
            maxAgeMs = MAX_LAST_KNOWN_LOCATION_AGE_MS
        )
    }

    @Suppress("DEPRECATION")
    private fun refreshWifiSnapshot() {
        val info = runCatching { wifiManager.connectionInfo }.getOrNull()
        val bssid = info?.bssid?.takeUnless { it.isNullOrBlank() || it == "02:00:00:00:00:00" }
        val ssid = info?.ssid
            ?.takeUnless { it.isNullOrBlank() || it == WifiManager.UNKNOWN_SSID }
            ?.trim('"')
        val connected = info?.networkId?.takeIf { it != -1 } != null && bssid != null
        currentWifiSnapshot = WifiSnapshot(
            isConnected = connected,
            ssid = ssid,
            bssid = bssid
        )
        wifiStayContext.update(currentWifiSnapshot, insideAnchorIds.isNotEmpty())
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        networkCallbackRegistered = true
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        networkCallbackRegistered = false
    }

    private fun collectEnabledProviders(highAccuracy: Boolean): List<String> {
        val providers = mutableListOf<String>()
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!highAccuracy && isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers += LocationManager.NETWORK_PROVIDER
        }
        if (hasFineLocation && isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers += LocationManager.GPS_PROVIDER
        }
        if (highAccuracy &&
            LocationManager.NETWORK_PROVIDER !in providers &&
            isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            providers += LocationManager.NETWORK_PROVIDER
        }
        if (providers.isEmpty() && isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            providers += LocationManager.PASSIVE_PROVIDER
        }
        return providers
    }

    private fun isProviderEnabled(provider: String): Boolean {
        return runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
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

    private fun isLocationTooOld(location: Location): Boolean {
        val locationTime = location.time.takeIf { it > 0L } ?: return false
        return System.currentTimeMillis() - locationTime > MAX_IDLE_LOCATION_AGE_MS
    }

    private fun distanceBetween(first: TrackPoint, second: TrackPoint): Float {
        return distanceBetween(first.latitude, first.longitude, second.latitude, second.longitude)
    }

    private fun distanceBetween(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(firstLatitude, firstLongitude, secondLatitude, secondLongitude, result)
        return result[0]
    }

    private fun phaseLabel(phase: TrackingPhase): String {
        return when (phase) {
            TrackingPhase.IDLE -> "后台待命中"
            TrackingPhase.SUSPECT_MOVING -> "疑似移动中"
            TrackingPhase.ACTIVE -> "记录进行中"
            TrackingPhase.SUSPECT_STOPPING -> "疑似停止中"
        }
    }

    private fun locationDecisionLabel(action: TrackNoiseAction): String {
        return when (action) {
            TrackNoiseAction.ACCEPT -> "定位点已接收"
            TrackNoiseAction.MERGE_STILL -> "静止抖动已合并"
            TrackNoiseAction.DROP_DRIFT -> "漂移点已过滤"
            TrackNoiseAction.DROP_JUMP -> "急跳点已过滤"
        }
    }

    private fun formatDistance(distanceKm: Double): String {
        return String.format(Locale.getDefault(), "%.2f 公里", distanceKm)
    }

    private fun defaultNotificationTitle(): String {
        return if (currentSession != null) "正在智能记录行程" else "智能轨迹已开启"
    }

    private fun defaultNotificationContent(): String {
        return when (currentPhase) {
            TrackingPhase.IDLE -> "正在后台低功耗待命，检测到移动后会自动开始。"
            TrackingPhase.SUSPECT_MOVING -> "检测到运动迹象，正在提升采样频率确认是否出行。"
            TrackingPhase.ACTIVE -> {
                val distance = currentSession?.totalDistanceKm ?: 0.0
                "已记录 ${String.format(Locale.getDefault(), "%.2f", distance)} 公里，静止后会自动保存。"
            }
            TrackingPhase.SUSPECT_STOPPING -> "正在判断本次行程是否结束，若持续静止将自动保存。"
        }
    }

    private fun buildNotification(
        title: String = resolvedNotificationTitle(),
        content: String = resolvedNotificationContent()
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
            .setContentTitle(TrackingTextSanitizer.normalize(title))
            .setContentText(TrackingTextSanitizer.normalize(content))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(TrackingTextSanitizer.normalize(content))
            )
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(
        title: String = resolvedNotificationTitle(),
        content: String = resolvedNotificationContent()
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = TrackingTextSanitizer.normalize(title),
                content = TrackingTextSanitizer.normalize(content)
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "智能轨迹记录",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于在后台持续守候你的出行"
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopAllTracking(clearSession: Boolean) {
        handler.removeCallbacks(finalizeRunnable)
        stopLocationUpdates()
        sensorManager.unregisterListener(sensorListener)
        registerSignificantMotionSensor(false)
        accelerometerRegistered = false
        stepCounterRegistered = false
        significantMotionRegistered = false
        currentAccelerometerDelay = -1
        accelerationWindow.clear()
        wifiStayContext.clear()
        motionConfidenceEngine.resetAll()
        lastIdleScoutPoint = null
        lastIdleScoutSamplePoint = null
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

    private fun resolvedNotificationTitle(): String {
        return if (currentSession != null) {
            "正在智能记录行程"
        } else {
            "智能轨迹已开启"
        }
    }

    private fun resolvedNotificationContent(): String {
        return when (currentPhase) {
            TrackingPhase.IDLE -> "正在后台低功耗待命，检测到移动后会自动开始。"
            TrackingPhase.SUSPECT_MOVING -> "检测到运动迹象，正在提升采样频率确认是否出行。"
            TrackingPhase.ACTIVE -> {
                val distance = currentSession?.totalDistanceKm ?: 0.0
                "已记录 ${String.format(Locale.getDefault(), "%.2f", distance)} 公里，静止后会自动保存。"
            }
            TrackingPhase.SUSPECT_STOPPING -> "正在判断本次行程是否结束，若持续静止将自动保存。"
        }
    }

    private fun currentSessionDurationSeconds(session: AutoTrackSession): Int {
        val effectiveEndTimestamp = max(
            session.lastMotionTimestamp,
            session.points.lastOrNull()?.timestampMillis ?: session.lastMotionTimestamp
        )
        return ((effectiveEndTimestamp - session.startTimestamp) / 1000L)
            .toInt()
            .coerceAtLeast(0)
    }

    private fun createNotificationChannelClean() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "智能轨迹记录",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于在后台持续守候你的出行"
        }
        manager.createNotificationChannel(channel)
    }
}
