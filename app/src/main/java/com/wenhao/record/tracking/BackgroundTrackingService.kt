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

class BackgroundTrackingService : Service() {
    companion object {
        private const val TAG = "BackgroundTracking"
        const val ACTION_START = "com.wenhao.record.action.START_BACKGROUND_TRACKING"
        const val ACTION_STOP = "com.wenhao.record.action.STOP_BACKGROUND_TRACKING"

        private const val CHANNEL_ID = "manual_tracking"
        private const val NOTIFICATION_ID = 1107
        private const val ANALYSIS_VERSION = 1
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

    private val analysisRunner = TrackAnalysisRunner()
    private val analysisHistoryProjector = AnalysisHistoryProjector()
    private lateinit var locationManager: LocationManager
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
    private var lastAnalysisAt: Long? = null
    private var analysisInFlight = false
    private var acceptedPointCount = 0
    private var signalLost = false
    private var latestLocation: Location? = null
    private var lastGoodFixCandidate: TrackPoint? = null
    private var currentSessionId: String? = null

    override fun onCreate() {
        super.onCreate()
        RuntimeUsageRecorder.hit(RuntimeUsageModule.SERVICE_BACKGROUND_TRACKING)
        locationManager = getSystemService(LocationManager::class.java)
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
            if (currentPhase != TrackingPhase.ACTIVE) {
                enterPhase(TrackingPhase.ACTIVE, reason = "恢复手动记录")
            } else {
                requestLocationUpdatesForPhase(currentPhase)
            }
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
        initializePhaseAnchorFromLastKnownLocation()
        enterPhase(TrackingPhase.ACTIVE, reason = "手动开始记录")
        TrackingRuntimeSnapshotStorage.setEnabled(this, true)
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = phaseLabel(currentPhase),
            event = "手动记录已启动",
        )
    }

    private fun disableTracking() {
        if (!enabled) {
            stopSelf()
            return
        }
        enabled = false
        if (currentSessionId != null) {
            triggerAnalysis(reason = "手动结束记录")
        }
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
        currentPhase = TrackingPhase.IDLE
        phaseAnchorPoint = latestPoint
        saveSnapshot()
        TrackingRuntimeSnapshotStorage.setEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = "后台待命中",
            event = "手动记录已结束",
        )
        TrackDataChangeNotifier.notifyDashboardChanged()
        TrackDataChangeNotifier.notifyDiagnosticsChanged()
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

        requestLocationUpdatesForPhase(phase)
        updateNotification(reason)
        saveSnapshot()
        AutoTrackDiagnosticsStorage.markServiceStatus(
            this,
            status = phaseLabel(phase),
            event = reason,
        )
        Log.i(TAG, "Phase changed to $phase, reason=$reason")

        if (previousPhase == TrackingPhase.ACTIVE && phase == TrackingPhase.IDLE) {
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
            TrackingPhase.ACTIVE -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun stopLocationUpdates() {
        requestedConfig = null
        runCatching {
            locationManager.removeUpdates(locationListener)
        }
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
        latestLocation = lastKnownLocation
    }


    private fun samplingConfigFor(phase: TrackingPhase): SamplingConfig {
        return when (samplingTierFor(phase)) {
            SamplingTier.IDLE -> SamplingConfig(intervalMillis = 30_000L, minDistanceMeters = 30f)
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
        }
        latestLocation = location
        val netDistanceMeters = GeoMath.distanceMeters(anchorPoint, point)
        val inferredSpeedMetersPerSecond = inferredSpeed(
            previousPoint = previousPoint,
            currentPoint = point,
            location = location,
        )
        val motionType = inferMotionType(speedMetersPerSecond = inferredSpeedMetersPerSecond)
        val motionConfidence = defaultMotionConfidence(motionType)
        val noiseResult = if (recoveredFromSignalLost) {
            TrackNoiseResult(TrackNoiseAction.ACCEPT, acceptedPoint = point)
        } else if (currentPhase == TrackingPhase.ACTIVE) {
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

        saveSnapshot()
        if (currentPhase == TrackingPhase.ACTIVE && shouldTriggerRollingAnalysis(point.timestampMillis)) {
            triggerAnalysis(reason = "长时活跃窗口补算")
        }
    }

    private fun shouldTriggerRollingAnalysis(timestampMillis: Long): Boolean {
        val referenceTimestamp = lastAnalysisAt ?: phaseAnchorPoint?.timestampMillis ?: return false
        return timestampMillis - referenceTimestamp >= 8 * 60_000L
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
            samplingTier = samplingTierFor(currentPhase),
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
                samplingTier = samplingTierFor(currentPhase),
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
            TrackingPhase.ACTIVE -> "正在连续记录"
        }
    }

    private fun shouldIgnoreLocation(location: Location): Boolean {
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }
        val speed = location.speed.takeIf { location.hasSpeed() && it >= 0f }
        return when (currentPhase) {
            TrackingPhase.IDLE -> false
            TrackingPhase.ACTIVE -> {
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
            TrackingPhase.IDLE -> null
            TrackingPhase.ACTIVE -> when {
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
            TrackingPhase.ACTIVE -> "记录中，已采集第 $acceptedPointCount 个点（$accuracyLabel）"
        }
    }

    private fun inferMotionType(speedMetersPerSecond: Float): String {
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
        val currentLocation = latestLocation
        return if (currentLocation != null && currentLocation.time == timestampMillis) {
            currentLocation.provider ?: "unknown"
        } else {
            "unknown"
        }
    }

    private fun samplingTierFor(phase: TrackingPhase): SamplingTier {
        return when (phase) {
            TrackingPhase.IDLE -> SamplingTier.IDLE
            TrackingPhase.ACTIVE -> SamplingTier.ACTIVE
        }
    }

    private fun defaultMotionConfidence(motionType: String): Float {
        return when (motionType) {
            "STILL" -> 0.75f
            else -> 0.85f
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
