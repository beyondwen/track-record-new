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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wenhao.record.R
import com.wenhao.record.data.diagnostics.DiagnosticLogger
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AnalysisHistoryProjector
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.ContinuousPointStorage
import com.wenhao.record.data.tracking.RawPointRetentionPolicy
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TodaySessionStorage
import com.wenhao.record.data.tracking.TodaySessionSyncCoordinator
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackUploadScheduler
import com.wenhao.record.data.tracking.TodayTrackDisplayCache
import com.wenhao.record.data.tracking.WorkerSafeIntegerIds
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import com.wenhao.record.data.tracking.UploadCursorStorage
import com.wenhao.record.data.tracking.UploadCursorType
import com.wenhao.record.map.GeoMath
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import com.wenhao.record.tracking.analysis.AnalyzedPoint
import com.wenhao.record.tracking.analysis.SegmentKind
import com.wenhao.record.tracking.analysis.TrackAnalysisRunner
import com.wenhao.record.ui.main.MainActivity
import com.wenhao.record.util.AppTaskExecutor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.sqrt

class BackgroundTrackingService : Service() {
    companion object {
        private const val TAG = "BackgroundTracking"
        const val ACTION_START = "com.wenhao.record.action.START_BACKGROUND_TRACKING"
        const val ACTION_STOP = "com.wenhao.record.action.STOP_BACKGROUND_TRACKING"

        private const val CHANNEL_ID = "smart_tracking"
        private const val NOTIFICATION_ID = 1107
        private const val ANALYSIS_VERSION = 1
        private const val MAX_IDLE_ACCURACY_METERS = 80f
        private const val MAX_ACTIVE_ACCURACY_METERS = 35f
        private const val MAX_ACTIVE_SPEED_METERS_PER_SECOND = 45f
        private const val PERF_WARN_REALTIME_ANALYSIS_MS = 2_500L

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

    private data class SamplingConfig(
        val intervalMillis: Long,
        val minDistanceMeters: Float,
    )

    private val phasePolicy = BackgroundTrackingServicePhasePolicy()
    private val analysisRunner = TrackAnalysisRunner()
    private val analysisHistoryProjector = AnalysisHistoryProjector()
    private val motionConfidenceEngine = MotionConfidenceEngine()
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var stepCounter: Sensor? = null
    private lateinit var trackingThread: HandlerThread
    private lateinit var trackingHandler: Handler
    private lateinit var pointStorage: ContinuousPointStorage
    private lateinit var uploadCursorStorage: UploadCursorStorage
    private lateinit var todaySessionStorage: TodaySessionStorage
    private lateinit var todaySessionSyncCoordinator: TodaySessionSyncCoordinator

    private val locationListener = LocationListener { location ->
        runOnTrackingThread {
            handleLocationUpdate(location)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> noteAccelerometerSample(event)
                Sensor.TYPE_STEP_COUNTER -> {
                    motionConfidenceEngine.noteStepCount(
                        event.values.firstOrNull() ?: return,
                        System.currentTimeMillis(),
                    )
                    wakeFromSensorMotionIfNeeded("步数传感器检测到移动趋势")
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val fixQualityGate = TrackFixQualityGate(
        requiredConsecutiveGoodFixes = 3,
        maxAcceptedAccuracyMeters = 25f,
        maxFixAgeMillis = 8_000L,
        minMeaningfulDistanceMeters = 8f,
    )

    private var enabled = false
    private var currentPhase = TrackingPhase.IDLE
    private var requestedConfig: SamplingConfig? = null
    private var latestPoint: TrackPoint? = null
    private var previousAcceptedPoint: TrackPoint? = null
    private var phaseAnchorPoint: TrackPoint? = null
    private var stillSinceMillis: Long? = null
    private var lastAnalysisAt: Long? = null
    private var analysisInFlight = false
    private var suspectEnteredAt: Long? = null
    private var acceptedPointCount = 0
    private var signalLost = false
    private var lastLowPowerLocation: Location? = null
    private var lastGoodFixCandidate: TrackPoint? = null
    private var lastAccelerometerMagnitude = 0f
    private var currentSessionId: String? = null

    private val suspectTimeoutCheck = Runnable {
        val enteredAt = suspectEnteredAt
        if (currentPhase == TrackingPhase.SUSPECT_MOVING && enteredAt != null) {
            val elapsed = System.currentTimeMillis() - enteredAt
            if (elapsed >= 90_000L) {
                enterPhase(TrackingPhase.IDLE, reason = "疑似移动超时未确认，恢复低功耗待命")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_BACKGROUND_TRACKING)
        locationManager = getSystemService(LocationManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        trackingThread = HandlerThread("continuous-tracking").apply { start() }
        trackingHandler = Handler(trackingThread.looper)
        val database = TrackDatabase.getInstance(this)
        val continuousTrackDao = database.continuousTrackDao()
        pointStorage = ContinuousPointStorage(continuousTrackDao)
        uploadCursorStorage = UploadCursorStorage(continuousTrackDao)
        todaySessionStorage = TodaySessionStorage(database.todaySessionDao())
        todaySessionSyncCoordinator = TodaySessionSyncCoordinator.create(this)
        val snapshot = TrackingRuntimeSnapshotStorage.peek(this)
        enabled = snapshot.isEnabled
        currentPhase = snapshot.phase
        latestPoint = snapshot.latestPoint
        phaseAnchorPoint = snapshot.latestPoint
        lastAnalysisAt = snapshot.lastAnalysisAt
        currentSessionId = snapshot.sessionId
        acceptedPointCount = AutoTrackDiagnosticsStorage.load(this).acceptedPointCount
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = phaseLabel(currentPhase),
            event = "后台采点服务已创建",
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> runOnTrackingThread(::enableTracking)
            ACTION_STOP -> runOnTrackingThread(::disableTracking)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        unregisterMotionSensors()
        if (enabled) {
            saveSnapshot()
        }
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = "后台待命中",
            event = "后台采点服务已销毁",
        )
        AutoTrackDiagnosticsStorage.flush(this)
        trackingThread.quitSafely()
        super.onDestroy()
    }

    private fun enableTracking() {
        if (enabled) {
            ensureNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            registerMotionSensors()
            requestLocationUpdatesForPhase(currentPhase)
            AutoTrackDiagnosticsStorage.markServiceStatus(
                this,
                status = phaseLabel(currentPhase),
                event = "后台采点服务已保持运行",
            )
            saveSnapshot()
            return
        }
        enabled = true
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerMotionSensors()
        initializePhaseAnchorFromLastKnownLocation()
        enterPhase(TrackingPhase.IDLE, reason = "启用连续采点")
        TrackingRuntimeSnapshotStorage.setEnabled(this, true)
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = phaseLabel(currentPhase),
            event = "后台采点服务已启动",
        )
    }

    private fun disableTracking() {
        if (!enabled) {
            stopSelf()
            return
        }
        enabled = false
        currentSessionId?.let { sessionId ->
            runBlocking {
                todaySessionStorage.markPaused(
                    sessionId = sessionId,
                    phase = currentPhase.name,
                    nowMillis = System.currentTimeMillis(),
                )
            }
            triggerTodaySessionSync(nowMillis = System.currentTimeMillis(), sessionId = sessionId, force = true)
        }
        currentSessionId = null
        stopLocationUpdates()
        unregisterMotionSensors()
        currentPhase = TrackingPhase.IDLE
        phaseAnchorPoint = latestPoint
        stillSinceMillis = null
        saveSnapshot()
        TrackingRuntimeSnapshotStorage.setEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = "后台待命中",
            event = "后台采点已停止",
        )
        stopSelf()
    }

    private fun enterPhase(phase: TrackingPhase, reason: String) {
        val previousPhase = currentPhase
        currentPhase = phase
        if (phase == TrackingPhase.IDLE) {
            phaseAnchorPoint = latestPoint
        } else if (previousPhase == TrackingPhase.IDLE) {
            phaseAnchorPoint = latestPoint
        }

        trackingHandler.removeCallbacks(suspectTimeoutCheck)
        if (phase == TrackingPhase.SUSPECT_MOVING) {
            suspectEnteredAt = System.currentTimeMillis()
            trackingHandler.postDelayed(suspectTimeoutCheck, 90_000L)
        } else {
            suspectEnteredAt = null
        }
        requestLocationUpdatesForPhase(phase)
        updateNotification(reason)
        saveSnapshot()
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = phaseLabel(phase),
            event = reason,
        )
        Log.i(TAG, "Phase changed to $phase, reason=$reason")

        if (
            previousPhase == TrackingPhase.ACTIVE &&
            (phase == TrackingPhase.SUSPECT_STOPPING || phase == TrackingPhase.IDLE)
        ) {
            triggerAnalysis(reason = "活跃采样降频")
        }
        currentSessionId?.let { sessionId ->
            triggerTodaySessionSync(
                nowMillis = System.currentTimeMillis(),
                sessionId = sessionId,
                force = previousPhase != phase,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdatesForPhase(phase: TrackingPhase) {
        if (!hasLocationPermission()) {
            saveSnapshot()
            return
        }

        val config = samplingConfigFor(phase)
        if (config == requestedConfig) return

        stopLocationUpdates()
        requestedConfig = config

        val providers = providersForPhase(phase)
            .filter(locationManager::isProviderEnabled)
        if (providers.isEmpty()) {
            AutoTrackDiagnosticsStorage.markEvent(this, "未发现可用定位 Provider")
            Log.w(TAG, "No enabled location providers for phase=$phase")
            return
        }
        AutoTrackDiagnosticsStorage.markEvent(
            this,
            "开始监听 ${providers.joinToString()}，${config.intervalMillis / 1000}s / ${config.minDistanceMeters.toInt()}m"
        )
        Log.i(
            TAG,
            "Request location updates. phase=$phase, providers=$providers, interval=${config.intervalMillis}, minDistance=${config.minDistanceMeters}"
        )
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                config.intervalMillis,
                config.minDistanceMeters,
                locationListener,
                trackingThread.looper,
            )
        }
    }

    private fun providersForPhase(phase: TrackingPhase): List<String> {
        return when (phase) {
            TrackingPhase.IDLE -> listOf(LocationManager.PASSIVE_PROVIDER)
            TrackingPhase.SUSPECT_MOVING -> listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER)
            TrackingPhase.ACTIVE -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            TrackingPhase.SUSPECT_STOPPING -> listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun stopLocationUpdates() {
        requestedConfig = null
        runCatching {
            locationManager.removeUpdates(locationListener)
        }
    }

    private fun registerMotionSensors() {
        val samplingPeriod = SensorManager.SENSOR_DELAY_NORMAL
        accelerometer?.let { sensorManager.registerListener(sensorListener, it, samplingPeriod, trackingHandler) }
        stepCounter?.let { sensorManager.registerListener(sensorListener, it, samplingPeriod, trackingHandler) }
    }

    private fun unregisterMotionSensors() {
        sensorManager.unregisterListener(sensorListener)
    }

    private fun initializePhaseAnchorFromLastKnownLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val lastKnownLocation = LocationSelectionUtils.loadBestLastKnownLocation(
            locationManager = locationManager,
            hasFineLocation = hasFineLocation,
            maxAgeMs = 15_000L,
        ) ?: return
        val point = lastKnownLocation.toTrackPoint()
        latestPoint = point
        phaseAnchorPoint = point
        lastLowPowerLocation = lastKnownLocation
    }


    private fun samplingConfigFor(phase: TrackingPhase): SamplingConfig {
        return when (phasePolicy.samplingTierFor(phase)) {
            SamplingTier.IDLE -> SamplingConfig(intervalMillis = 30_000L, minDistanceMeters = 30f)
            SamplingTier.SUSPECT -> SamplingConfig(intervalMillis = 10_000L, minDistanceMeters = 12f)
            SamplingTier.ACTIVE -> SamplingConfig(intervalMillis = 3_000L, minDistanceMeters = 4f)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        if (!enabled) return

        val accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }
        val speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() && it >= 0f }
        val ignoredDecision = ignoredLocationDecision(
            phase = currentPhase,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
        )
        if (ignoredDecision != null && shouldIgnoreLocation(location)) {
            AutoTrackDiagnosticsStorage.markLocationDecision(
                context = this,
                decision = ignoredDecision,
                acceptedPointCount = acceptedPointCount,
                accuracyMeters = accuracyMeters,
            )
            Log.i(
                TAG,
                "Ignored location. phase=$currentPhase, accuracy=$accuracyMeters, speed=$speedMetersPerSecond, lat=${location.latitude}, lng=${location.longitude}"
            )
            return
        }

        val point = location.toTrackPoint()
        val previousPoint = latestPoint
        val anchorPoint = phaseAnchorPoint ?: previousPoint ?: point
        val nowMillis = System.currentTimeMillis()
        val fixEvaluation = fixQualityGate.noteFix(
            point = point,
            nowMillis = nowMillis,
            previousCandidate = lastGoodFixCandidate,
        )
        if (fixEvaluation.acceptedAsGoodFix) {
            lastGoodFixCandidate = point
        }
        val signalHealthy =
            point.timestampMillis > 0L &&
                nowMillis - point.timestampMillis <= 8_000L &&
                (point.accuracyMeters ?: Float.MAX_VALUE) <= 25f
        if (!signalLost && !signalHealthy) {
            signalLost = true
            fixQualityGate.reset()
            lastGoodFixCandidate = null
        }
        val goodFixReady = fixEvaluation.isReadyToRecord
        val recoveredFromSignalLost = signalLost && goodFixReady
        if (recoveredFromSignalLost) {
            signalLost = false
            fixQualityGate.reset()
            lastGoodFixCandidate = null
            phaseAnchorPoint = point
            stillSinceMillis = null
        }
        val lowPowerSignals = TrackingLowPowerSignals.fromLocations(
            previous = lastLowPowerLocation,
            current = location,
            nowMillis = nowMillis,
        )
        lastLowPowerLocation = location
        val netDistanceMeters = GeoMath.distanceMeters(anchorPoint, point)
        val inferredSpeedMetersPerSecond = inferredSpeed(
            previousPoint = previousPoint,
            currentPoint = point,
            location = location,
        )
        val motionSnapshot = motionConfidenceEngine.evaluate(
            nowMillis = nowMillis,
            signals = MotionSignals(
                stepDelta = motionConfidenceEngine.currentStepDelta(),
                effectiveDistanceMeters = netDistanceMeters,
                reportedSpeedMetersPerSecond = speedMetersPerSecond ?: 0f,
                inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
                insideAnchor = false,
                sameAnchorWifi = false,
                poorAccuracy = (point.accuracyMeters ?: Float.MAX_VALUE) > 35f,
            ),
        )
        val shouldWake = lowPowerSignals.shouldEnterSuspectMoving || motionSnapshot.movingLikely
        val motionType = if (motionSnapshot.stronglyMoving) "WALKING" else inferMotionType(
            location = location,
            speedMetersPerSecond = inferredSpeedMetersPerSecond,
        )
        val motionConfidence = motionSnapshot.score.coerceAtLeast(1) / 12f
        val noiseResult = if (recoveredFromSignalLost) {
            TrackNoiseResult(TrackNoiseAction.ACCEPT, acceptedPoint = point)
        } else if (currentPhase == TrackingPhase.ACTIVE || currentPhase == TrackingPhase.SUSPECT_STOPPING) {
            TrackNoiseFilter.evaluate(
                previousPoint = previousAcceptedPoint,
                lastPoint = previousPoint,
                sample = TrackNoiseSample(
                    point = point,
                    speedMetersPerSecond = inferredSpeedMetersPerSecond,
                    locationAgeMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0L),
                ),
            )
        } else {
            TrackNoiseResult(TrackNoiseAction.ACCEPT, acceptedPoint = point)
        }
        if (noiseResult.action == TrackNoiseAction.DROP_DRIFT || noiseResult.action == TrackNoiseAction.DROP_JUMP) {
            val decision = when (noiseResult.action) {
                TrackNoiseAction.DROP_JUMP -> "已忽略：实时噪声过滤识别为跳点"
                else -> "已忽略：实时噪声过滤识别为漂移点"
            }
            AutoTrackDiagnosticsStorage.markLocationDecision(
                context = this,
                decision = decision,
                acceptedPointCount = acceptedPointCount,
                accuracyMeters = point.accuracyMeters,
            )
            Log.i(
                TAG,
                "Noise-filtered location. action=${noiseResult.action}, phase=$currentPhase, distance=${noiseResult.distanceMeters}, accuracy=${point.accuracyMeters}, speed=$inferredSpeedMetersPerSecond"
            )
            return
        }
        val stillDurationMillis = updateStillDuration(
            timestampMillis = point.timestampMillis,
            motionType = motionType,
            netDistanceMeters = netDistanceMeters,
            inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
        )

        previousAcceptedPoint = previousPoint
        latestPoint = point
        acceptedPointCount += 1
        val acceptedDecision = acceptedLocationDecision(
            phase = currentPhase,
            acceptedPointCount = acceptedPointCount,
            accuracyMeters = point.accuracyMeters,
        )
        AutoTrackDiagnosticsStorage.markLocationDecision(
            context = this,
            decision = acceptedDecision,
            acceptedPointCount = acceptedPointCount,
            accuracyMeters = point.accuracyMeters,
        )
        Log.i(
            TAG,
            "Accepted location. phase=$currentPhase, count=$acceptedPointCount, accuracy=${point.accuracyMeters}, speed=$inferredSpeedMetersPerSecond, lat=${point.latitude}, lng=${point.longitude}"
        )
        if (shouldPersistPoint(point, goodFixReady = goodFixReady)) {
            appendRawPoint(
                point = point,
                motionType = motionType,
                motionConfidence = motionConfidence,
                inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
            )
        }

        val nextPhase = phasePolicy.nextPhase(
            current = currentPhase,
            lowPowerSignals = lowPowerSignals.copy(
                shouldEnterSuspectMoving = shouldWake,
            ),
            hasEnoughGoodFixesToRecord = goodFixReady,
            signalLost = signalLost,
            prolongedStill = stillDurationMillis >= 5 * 60_000L,
        )
        if (nextPhase != currentPhase) {
            enterPhase(nextPhase, reason = "定位与运动信号触发相位调整")
        } else {
            saveSnapshot()
            if (currentPhase == TrackingPhase.ACTIVE && shouldTriggerRollingAnalysis(point.timestampMillis)) {
                triggerAnalysis(reason = "长时活跃窗口补算")
            }
        }
    }

    private fun noteAccelerometerSample(event: SensorEvent) {
        val x = event.values.getOrNull(0) ?: return
        val y = event.values.getOrNull(1) ?: return
        val z = event.values.getOrNull(2) ?: return
        val magnitude = sqrt((x * x) + (y * y) + (z * z))
        val delta = kotlin.math.abs(magnitude - lastAccelerometerMagnitude)
        lastAccelerometerMagnitude = magnitude
        if (delta > 0.85f) {
            motionConfidenceEngine.noteAccelerationVariance(delta, System.currentTimeMillis())
            wakeFromSensorMotionIfNeeded("加速度传感器检测到移动趋势")
        }
    }

    private fun wakeFromSensorMotionIfNeeded(reason: String) {
        if (!enabled || currentPhase != TrackingPhase.IDLE) return
        val nowMillis = System.currentTimeMillis()
        val stepDelta = motionConfidenceEngine.currentStepDelta()
        val motionSnapshot = motionConfidenceEngine.evaluate(
            nowMillis = nowMillis,
            signals = MotionSignals(
                stepDelta = stepDelta,
                effectiveDistanceMeters = 0f,
                reportedSpeedMetersPerSecond = 0f,
                inferredSpeedMetersPerSecond = 0f,
                insideAnchor = false,
                sameAnchorWifi = false,
                poorAccuracy = false,
            ),
        )
        val strongSensorEvidence =
            motionConfidenceEngine.hasRecentSignificantMotion(nowMillis) ||
                stepDelta >= 3 ||
                (stepDelta > 0 && motionConfidenceEngine.hasRecentAccelerationMotion(nowMillis))
        if (strongSensorEvidence || motionSnapshot.movingLikely) {
            enterPhase(TrackingPhase.SUSPECT_MOVING, reason = reason)
        }
    }

    private fun shouldTriggerRollingAnalysis(timestampMillis: Long): Boolean {
        val referenceTimestamp = lastAnalysisAt ?: phaseAnchorPoint?.timestampMillis ?: return false
        return timestampMillis - referenceTimestamp >= 8 * 60_000L
    }


    private fun updateStillDuration(
        timestampMillis: Long,
        motionType: String,
        netDistanceMeters: Float,
        inferredSpeedMetersPerSecond: Float,
    ): Long {
        val qualifiesStill = motionType == "STILL" &&
            netDistanceMeters <= 25f &&
            inferredSpeedMetersPerSecond <= 0.35f
        stillSinceMillis = when {
            qualifiesStill && stillSinceMillis == null -> timestampMillis
            qualifiesStill -> stillSinceMillis
            else -> null
        }
        return stillSinceMillis?.let { timestampMillis - it } ?: 0L
    }

    private fun shouldPersistPoint(point: TrackPoint, goodFixReady: Boolean): Boolean {
        return when {
            signalLost -> goodFixReady
            currentPhase == TrackingPhase.ACTIVE -> (point.accuracyMeters ?: Float.MAX_VALUE) <= 25f
            else -> false
        }
    }

    private fun ensureCurrentSession(nowMillis: Long): String {
        val existing = currentSessionId
        return runBlocking {
            val openSession = todaySessionStorage.loadOpenSession(nowMillis)
            if (existing != null && openSession?.sessionId == existing) {
                existing
            } else {
                todaySessionStorage.createOrRestoreOpenSession(nowMillis).sessionId
            }
        }.also { currentSessionId = it }
    }

    private fun appendRawPoint(
        point: TrackPoint,
        motionType: String,
        motionConfidence: Float,
        inferredSpeedMetersPerSecond: Float,
    ) {
        val provider = latestProvider(point.timestampMillis)
        val rawPoint = RawTrackPoint(
            timestampMillis = point.timestampMillis,
            latitude = point.latitude,
            longitude = point.longitude,
            accuracyMeters = point.accuracyMeters,
            altitudeMeters = point.altitudeMeters,
            speedMetersPerSecond = inferredSpeedMetersPerSecond,
            bearingDegrees = null,
            provider = provider,
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = null,
            activityType = motionType,
            activityConfidence = motionConfidence,
            samplingTier = phasePolicy.samplingTierFor(currentPhase),
        )
        runBlocking {
            val pointId = pointStorage.appendRawPoint(rawPoint)
            val sessionId = ensureCurrentSession(rawPoint.timestampMillis)
            TodayTrackDisplayCache.append(
                context = applicationContext,
                sessionId = sessionId,
                pointId = pointId,
                rawPoint = rawPoint.copy(pointId = pointId),
                phase = currentPhase.name,
                nowMillis = rawPoint.timestampMillis,
            )
            triggerTodaySessionSync(
                nowMillis = rawPoint.timestampMillis,
                sessionId = sessionId,
                force = false,
            )
            saveSnapshot()
        }
        AppTaskExecutor.runOnIo {
            TrackUploadScheduler.kickFullSyncPipeline(applicationContext)
        }
    }

    private fun triggerTodaySessionSync(
        nowMillis: Long,
        sessionId: String,
        force: Boolean,
    ) {
        val pendingPointCount = runBlocking {
            todaySessionStorage.loadPendingPoints(sessionId = sessionId, limit = 21).size
        }
        if (!todaySessionSyncCoordinator.shouldSync(nowMillis, pendingPointCount, force)) return
        TrackUploadScheduler.kickTodaySessionSync(applicationContext)
        todaySessionSyncCoordinator.markTriggered(nowMillis)
    }

    private fun triggerAnalysis(reason: String) {
        if (analysisInFlight) return
        analysisInFlight = true
        AppTaskExecutor.runOnIo {
            val analysisStartedAt = System.currentTimeMillis()
            var windowSize = 0
            var afterPointIdForLog = 0L
            try {
                runBlocking {
                    val cursor = pointStorage.loadAnalysisCursor()
                    val afterPointId = cursor?.lastAnalyzedPointId ?: 0L
                    afterPointIdForLog = afterPointId
                    val window = pointStorage.loadPendingWindow(afterPointId = afterPointId, limit = 2048)
                    windowSize = window.size
                    if (window.size < 3) {
                        runOnTrackingThread {
                            analysisInFlight = false
                            AutoTrackDiagnosticsStorage.markEvent(
                                applicationContext,
                                "分析跳过：有效点不足 3 个（当前 ${window.size}）"
                            )
                            updateNotification(reason)
                            saveSnapshot()
                        }
                        return@runBlocking
                    }

                    val analysisResult = analysisRunner.analyze(
                        points = window.map { rawPoint ->
                            AnalyzedPoint(
                                timestampMillis = rawPoint.timestampMillis,
                                latitude = rawPoint.latitude,
                                longitude = rawPoint.longitude,
                                accuracyMeters = rawPoint.accuracyMeters,
                                speedMetersPerSecond = rawPoint.speedMetersPerSecond,
                                activityType = rawPoint.activityType,
                                activityConfidence = rawPoint.activityConfidence,
                                wifiFingerprintDigest = rawPoint.wifiFingerprintDigest,
                            )
                        },
                    )
                    val persistedSegments = analysisResult.segments.mapNotNull { segment ->
                        if (segment.kind == SegmentKind.UNCERTAIN) {
                            return@mapNotNull null
                        }

                        val segmentPoints = window.filter { point ->
                            point.timestampMillis in segment.startTimestamp..segment.endTimestamp
                        }
                        if (segmentPoints.isEmpty()) {
                            return@mapNotNull null
                        }

                        val distanceMeters = segmentPoints.zipWithNext { first, second ->
                            GeoMath.distanceMeters(
                                first.latitude,
                                first.longitude,
                                second.latitude,
                                second.longitude,
                            )
                        }.sum()
                        val durationMillis = (segment.endTimestamp - segment.startTimestamp).coerceAtLeast(0L)
                        val speedSamples = segmentPoints.mapNotNull { it.speedMetersPerSecond?.toDouble() }
                        val avgSpeedMetersPerSecond = when {
                            speedSamples.isNotEmpty() -> speedSamples.average()
                            durationMillis <= 0L -> 0.0
                            else -> distanceMeters / (durationMillis / 1_000.0)
                        }
                        val maxSpeedMetersPerSecond = speedSamples.maxOrNull() ?: avgSpeedMetersPerSecond

                        AnalysisSegmentEntity(
                            segmentId = stableSegmentId(
                                startPointId = segmentPoints.first().pointId,
                                endPointId = segmentPoints.last().pointId,
                            ),
                            startPointId = segmentPoints.first().pointId,
                            endPointId = segmentPoints.last().pointId,
                            startTimestamp = segment.startTimestamp,
                            endTimestamp = segment.endTimestamp,
                            segmentType = segment.kind.name,
                            confidence = defaultSegmentConfidence(segment.kind),
                            distanceMeters = distanceMeters,
                            durationMillis = durationMillis,
                            avgSpeedMetersPerSecond = avgSpeedMetersPerSecond.toFloat(),
                            maxSpeedMetersPerSecond = maxSpeedMetersPerSecond.toFloat(),
                            analysisVersion = ANALYSIS_VERSION,
                        )
                    }
                    val persistedStayClusters = analysisResult.stayClusters.mapNotNull { stay ->
                        val ownerSegment = persistedSegments.firstOrNull { segment ->
                            segment.segmentType == SegmentKind.STATIC.name &&
                                stay.arrivalTime <= segment.endTimestamp &&
                                stay.departureTime >= segment.startTimestamp
                        } ?: return@mapNotNull null

                        StayClusterEntity(
                            stayId = stableStayId(
                                segmentId = ownerSegment.segmentId,
                                arrivalTime = stay.arrivalTime,
                                departureTime = stay.departureTime,
                            ),
                            segmentId = ownerSegment.segmentId,
                            centerLat = stay.centerLat,
                            centerLng = stay.centerLng,
                            radiusMeters = stay.radiusMeters,
                            arrivalTime = stay.arrivalTime,
                            departureTime = stay.departureTime,
                            confidence = stay.confidence,
                            analysisVersion = ANALYSIS_VERSION,
                        )
                    }
                    pointStorage.saveAnalysisResult(
                        analyzedUpToPointId = window.maxOf { it.pointId },
                        segments = persistedSegments,
                        stayClusters = persistedStayClusters,
                    )

                    val analyzedUpToPointId = window.maxOf { it.pointId }
                    val rawUploadedUpToPointId = uploadCursorStorage.load(UploadCursorType.RAW_POINT).lastUploadedId
                    if (persistedSegments.isNotEmpty() &&
                        RawPointRetentionPolicy.canDeleteAnalyzedRawPoints(
                            rawUploadedUpToPointId = rawUploadedUpToPointId,
                            analyzedUpToPointId = analyzedUpToPointId,
                        )
                    ) {
                        pointStorage.deleteRawPointsUpTo(analyzedUpToPointId)
                    }
                    HistoryStorage.upsertProjectedItems(
                        context = applicationContext,
                        projectedItems = analysisHistoryProjector.project(
                            segments = analysisResult.segments,
                            rawPoints = window,
                        ),
                    )
                    TrackUploadScheduler.kickLocalResultSyncPipeline(applicationContext)

                    val durationMs = System.currentTimeMillis() - analysisStartedAt
                    reportSlowRealtimeAnalysisIfNeeded(
                        durationMs = durationMs,
                        pointCount = window.size,
                        segmentCount = persistedSegments.size,
                        reason = reason,
                    )

                    runOnTrackingThread {
                        analysisInFlight = false
                        lastAnalysisAt = System.currentTimeMillis()
                        AutoTrackDiagnosticsStorage.markSessionSaved(
                            applicationContext,
                            "已分析 ${persistedSegments.size} 段，使用 ${window.size} 个点"
                        )
                        updateNotification(reason)
                        saveSnapshot()
                        TrackDataChangeNotifier.notifyHistoryChanged()
                    }
                }
            } catch (error: Exception) {
                val durationMs = System.currentTimeMillis() - analysisStartedAt
                Log.e(TAG, "Realtime analysis failed", error)
                DiagnosticLogger.error(
                    context = applicationContext,
                    source = "BackgroundTrackingService",
                    message = error.message?.takeIf { it.isNotBlank() } ?: "realtime analysis failed",
                    fingerprint = "realtime-analysis-failed",
                    payloadJson = JSONObject().apply {
                        put("durationMs", durationMs)
                        put("pointCount", windowSize)
                        put("afterPointId", afterPointIdForLog)
                        put("reason", reason)
                    }.toString(),
                )
                runOnTrackingThread {
                    analysisInFlight = false
                    AutoTrackDiagnosticsStorage.markEvent(applicationContext, "实时分析失败，已记录诊断日志")
                    updateNotification(reason)
                    saveSnapshot()
                }
            }
        }
    }

    private fun reportSlowRealtimeAnalysisIfNeeded(
        durationMs: Long,
        pointCount: Int,
        segmentCount: Int,
        reason: String,
    ) {
        if (durationMs < PERF_WARN_REALTIME_ANALYSIS_MS) return
        DiagnosticLogger.perfWarn(
            context = applicationContext,
            source = "BackgroundTrackingService",
            message = "realtime analysis took ${durationMs}ms",
            fingerprint = "realtime-analysis-slow",
            payloadJson = JSONObject().apply {
                put("durationMs", durationMs)
                put("pointCount", pointCount)
                put("segmentCount", segmentCount)
                put("reason", reason)
            }.toString(),
        )
    }

    private fun defaultSegmentConfidence(kind: SegmentKind): Float {
        return when (kind) {
            SegmentKind.STATIC -> 0.9f
            SegmentKind.DYNAMIC -> 0.85f
            SegmentKind.UNCERTAIN -> 0.5f
        }
    }

    private fun stableSegmentId(startPointId: Long, endPointId: Long): Long {
        return (startPointId shl 32) xor (endPointId and 0xFFFF_FFFFL)
    }

    private fun stableStayId(segmentId: Long, arrivalTime: Long, departureTime: Long): Long {
        return WorkerSafeIntegerIds.stableStayId(
            segmentId = segmentId,
            arrivalTime = arrivalTime,
            departureTime = departureTime,
        )
    }

    private fun saveSnapshot() {
        TrackingRuntimeSnapshotStorage.save(
            context = this,
            snapshot = TrackingRuntimeSnapshot(
                isEnabled = enabled,
                phase = currentPhase,
                samplingTier = phasePolicy.samplingTierFor(currentPhase),
                latestPoint = latestPoint,
                lastAnalysisAt = lastAnalysisAt,
                sessionId = currentSessionId,
                dayStartMillis = currentSessionId?.substringAfter("today-")?.toLongOrNull(),
            ),
        )
    }

    private fun buildNotification(reason: String? = null): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentText = buildString {
            append(phaseLabel(currentPhase))
            if (reason != null) {
                append(" · ")
                append(reason)
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(reason: String? = null) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(reason))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "连续轨迹采集",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun phaseLabel(phase: TrackingPhase): String {
        return when (phase) {
            TrackingPhase.IDLE -> "后台待命中"
            TrackingPhase.SUSPECT_MOVING -> "疑似移动，确认中"
            TrackingPhase.ACTIVE -> "正在连续记录"
            TrackingPhase.SUSPECT_STOPPING -> "疑似静止，观察中"
        }
    }

    private fun shouldIgnoreLocation(location: Location): Boolean {
        if (location.provider == LocationManager.PASSIVE_PROVIDER) {
            val accuracy = location.accuracy.takeIf { location.hasAccuracy() } ?: Float.MAX_VALUE
            if (accuracy > MAX_IDLE_ACCURACY_METERS) {
                return true
            }
        }

        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }
        val speed = location.speed.takeIf { location.hasSpeed() && it >= 0f }
        return when (currentPhase) {
            TrackingPhase.IDLE -> (accuracy ?: Float.MAX_VALUE) > MAX_IDLE_ACCURACY_METERS
            TrackingPhase.SUSPECT_MOVING -> (accuracy ?: Float.MAX_VALUE) > MAX_ACTIVE_ACCURACY_METERS
            TrackingPhase.ACTIVE, TrackingPhase.SUSPECT_STOPPING -> {
                val tooInaccurate = (accuracy ?: Float.MAX_VALUE) > MAX_ACTIVE_ACCURACY_METERS
                val impossibleSpeed = (speed ?: 0f) > MAX_ACTIVE_SPEED_METERS_PER_SECOND
                tooInaccurate || impossibleSpeed
            }
        }
    }

    private fun ignoredLocationDecision(
        phase: TrackingPhase,
        accuracyMeters: Float?,
        speedMetersPerSecond: Float?,
    ): String? {
        return when (phase) {
            TrackingPhase.IDLE -> if ((accuracyMeters ?: Float.MAX_VALUE) > MAX_IDLE_ACCURACY_METERS) {
                "已忽略：后台待命阶段定位精度较差"
            } else {
                null
            }
            TrackingPhase.SUSPECT_MOVING -> if ((accuracyMeters ?: Float.MAX_VALUE) > MAX_ACTIVE_ACCURACY_METERS) {
                "已忽略：确认移动阶段定位精度较差"
            } else {
                null
            }
            TrackingPhase.ACTIVE, TrackingPhase.SUSPECT_STOPPING -> when {
                (accuracyMeters ?: Float.MAX_VALUE) > MAX_ACTIVE_ACCURACY_METERS -> "已忽略：活跃定位精度较差"
                (speedMetersPerSecond ?: 0f) > MAX_ACTIVE_SPEED_METERS_PER_SECOND -> "已忽略：活跃定位速度异常"
                else -> null
            }
        }
    }

    private fun acceptedLocationDecision(
        phase: TrackingPhase,
        acceptedPointCount: Int,
        accuracyMeters: Float?,
    ): String {
        val accuracyLabel = accuracyMeters?.let { "精度 ${it.toInt()} 米" } ?: "精度未知"
        return when (phase) {
            TrackingPhase.IDLE -> "待命阶段收到可信定位（$accuracyLabel）"
            TrackingPhase.SUSPECT_MOVING -> "确认移动中，已采集第 $acceptedPointCount 个点（$accuracyLabel）"
            TrackingPhase.ACTIVE -> "记录中，已采集第 $acceptedPointCount 个点（$accuracyLabel）"
            TrackingPhase.SUSPECT_STOPPING -> "疑似静止观察中，仍收到定位（$accuracyLabel）"
        }
    }

    private fun inferMotionType(location: Location, speedMetersPerSecond: Float): String {
        return when {
            speedMetersPerSecond < 0.6f -> "STILL"
            speedMetersPerSecond < 2.2f -> "WALKING"
            speedMetersPerSecond < 6.5f -> "CYCLING"
            else -> "DRIVING"
        }
    }

    private fun inferredSpeed(
        previousPoint: TrackPoint?,
        currentPoint: TrackPoint,
        location: Location,
    ): Float {
        location.speed.takeIf { location.hasSpeed() && it >= 0f }?.let { return it }
        previousPoint ?: return 0f
        val durationSeconds = (currentPoint.timestampMillis - previousPoint.timestampMillis) / 1_000f
        if (durationSeconds <= 0f) return 0f
        return GeoMath.distanceMeters(previousPoint, currentPoint) / durationSeconds
    }

    private fun latestProvider(timestampMillis: Long): String {
        val currentLocation = lastLowPowerLocation
        return if (currentLocation != null && currentLocation.time == timestampMillis) {
            currentLocation.provider ?: "unknown"
        } else {
            "unknown"
        }
    }

    private fun Location.toTrackPoint(): TrackPoint {
        return TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = time,
            accuracyMeters = accuracy.takeIf { hasAccuracy() },
            altitudeMeters = altitude.takeIf { hasAltitude() },
            wgs84Latitude = latitude,
            wgs84Longitude = longitude,
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun runOnTrackingThread(block: () -> Unit) {
        if (Looper.myLooper() == trackingThread.looper) {
            block()
        } else {
            trackingHandler.post(block)
        }
    }
}
