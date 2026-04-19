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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wenhao.record.R
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AnalysisHistoryProjector
import com.wenhao.record.data.tracking.ContinuousPointStorage
import com.wenhao.record.data.tracking.RawTrackPoint
import com.wenhao.record.data.tracking.SamplingTier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackUploadScheduler
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import com.wenhao.record.map.GeoMath
import com.wenhao.record.tracking.analysis.AnalyzedPoint
import com.wenhao.record.tracking.analysis.SegmentKind
import com.wenhao.record.tracking.analysis.TrackAnalysisRunner
import com.wenhao.record.ui.main.MainActivity
import com.wenhao.record.util.AppTaskExecutor
import kotlin.math.max

class BackgroundTrackingService : Service() {
    companion object {
        const val ACTION_START = "com.wenhao.record.action.START_BACKGROUND_TRACKING"
        const val ACTION_STOP = "com.wenhao.record.action.STOP_BACKGROUND_TRACKING"
        const val ACTION_RELOAD_DECISION_MODEL = "com.wenhao.record.action.RELOAD_DECISION_MODEL"

        private const val CHANNEL_ID = "smart_tracking"
        private const val NOTIFICATION_ID = 1107
        private const val ANALYSIS_VERSION = 1

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

        fun reloadDecisionModel(context: Context) {
            val intent = Intent(context, BackgroundTrackingService::class.java).apply {
                action = ACTION_RELOAD_DECISION_MODEL
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
    private lateinit var locationManager: LocationManager
    private lateinit var trackingThread: HandlerThread
    private lateinit var trackingHandler: Handler
    private lateinit var pointStorage: ContinuousPointStorage

    private val locationListener = LocationListener { location ->
        runOnTrackingThread {
            handleLocationUpdate(location)
        }
    }

    private var enabled = false
    private var currentPhase = TrackingPhase.IDLE
    private var requestedConfig: SamplingConfig? = null
    private var latestPoint: TrackPoint? = null
    private var phaseAnchorPoint: TrackPoint? = null
    private var stillSinceMillis: Long? = null
    private var lastAnalysisAt: Long? = null
    private var analysisInFlight = false

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        trackingThread = HandlerThread("continuous-tracking").apply { start() }
        trackingHandler = Handler(trackingThread.looper)
        pointStorage = ContinuousPointStorage(
            TrackDatabase.getInstance(this).continuousTrackDao(),
        )
        val snapshot = TrackingRuntimeSnapshotStorage.peek(this)
        enabled = snapshot.isEnabled
        currentPhase = snapshot.phase
        latestPoint = snapshot.latestPoint
        phaseAnchorPoint = snapshot.latestPoint
        lastAnalysisAt = snapshot.lastAnalysisAt
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> runOnTrackingThread(::enableTracking)
            ACTION_STOP -> runOnTrackingThread(::disableTracking)
            ACTION_RELOAD_DECISION_MODEL -> runOnTrackingThread {
                if (enabled) {
                    triggerAnalysis(reason = "手动触发分析")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        trackingThread.quitSafely()
        super.onDestroy()
    }

    private fun enableTracking() {
        if (enabled) {
            requestLocationUpdatesForPhase(currentPhase)
            saveSnapshot()
            return
        }
        enabled = true
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        enterPhase(TrackingPhase.IDLE, reason = "启用连续采点")
        TrackingRuntimeSnapshotStorage.setEnabled(this, true)
    }

    private fun disableTracking() {
        if (!enabled) {
            stopSelf()
            return
        }
        enabled = false
        stopLocationUpdates()
        currentPhase = TrackingPhase.IDLE
        phaseAnchorPoint = latestPoint
        stillSinceMillis = null
        saveSnapshot()
        TrackingRuntimeSnapshotStorage.setEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterPhase(phase: TrackingPhase, reason: String) {
        val previousPhase = currentPhase
        currentPhase = phase
        if (phase == TrackingPhase.IDLE || previousPhase != phase) {
            phaseAnchorPoint = latestPoint
        }
        requestLocationUpdatesForPhase(phase)
        updateNotification(reason)
        saveSnapshot()

        if (
            previousPhase == TrackingPhase.ACTIVE &&
            (phase == TrackingPhase.SUSPECT_STOPPING || phase == TrackingPhase.IDLE)
        ) {
            triggerAnalysis(reason = "活跃采样降频")
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

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter(locationManager::isProviderEnabled)
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

    private fun stopLocationUpdates() {
        requestedConfig = null
        runCatching {
            locationManager.removeUpdates(locationListener)
        }
    }

    private fun samplingConfigFor(phase: TrackingPhase): SamplingConfig {
        return when (phasePolicy.samplingTierFor(phase)) {
            SamplingTier.IDLE -> SamplingConfig(intervalMillis = 60_000L, minDistanceMeters = 60f)
            SamplingTier.SUSPECT -> SamplingConfig(intervalMillis = 15_000L, minDistanceMeters = 18f)
            SamplingTier.ACTIVE -> SamplingConfig(intervalMillis = 4_000L, minDistanceMeters = 6f)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        if (!enabled) return

        val point = location.toTrackPoint()
        val previousPoint = latestPoint
        val anchorPoint = phaseAnchorPoint ?: previousPoint ?: point
        val netDistanceMeters = GeoMath.distanceMeters(anchorPoint, point)
        val inferredSpeedMetersPerSecond = inferredSpeed(
            previousPoint = previousPoint,
            currentPoint = point,
            location = location,
        )
        val motionType = inferMotionType(location = location, speedMetersPerSecond = inferredSpeedMetersPerSecond)
        val motionConfidence = inferMotionConfidence(
            location = location,
            speedMetersPerSecond = inferredSpeedMetersPerSecond,
        )
        val stillDurationMillis = updateStillDuration(
            timestampMillis = point.timestampMillis,
            motionType = motionType,
            netDistanceMeters = netDistanceMeters,
            inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
        )

        latestPoint = point
        appendRawPoint(
            point = point,
            motionType = motionType,
            motionConfidence = motionConfidence,
            inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
        )

        val nextPhase = phasePolicy.nextPhase(
            current = currentPhase,
            motionType = motionType,
            motionConfidence = motionConfidence,
            netDistanceMeters = netDistanceMeters,
            inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
            stillDurationMillis = stillDurationMillis,
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

    private fun shouldTriggerRollingAnalysis(timestampMillis: Long): Boolean {
        val last = lastAnalysisAt ?: return false
        return timestampMillis - last >= 15 * 60_000L
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
        AppTaskExecutor.runOnIo {
            pointStorage.appendRawPoint(rawPoint)
            TrackUploadScheduler.kickRawPointSync(applicationContext)
        }
    }

    private fun triggerAnalysis(reason: String) {
        if (analysisInFlight) return
        analysisInFlight = true
        AppTaskExecutor.runOnIo {
            val window = pointStorage.loadPendingWindow(afterPointId = 0L, limit = 512)
            if (window.size < 3) {
                runOnTrackingThread {
                    analysisInFlight = false
                    updateNotification(reason)
                    saveSnapshot()
                }
                return@runOnIo
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
                    distanceMeters = distanceMeters.toFloat(),
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
            HistoryStorage.upsertProjectedItems(
                context = applicationContext,
                projectedItems = analysisHistoryProjector.project(
                    segments = analysisResult.segments,
                    rawPoints = window,
                ),
            )
            TrackUploadScheduler.kickAnalysisSync(applicationContext)
            TrackUploadScheduler.kickHistorySync(applicationContext)

            runOnTrackingThread {
                analysisInFlight = false
                lastAnalysisAt = System.currentTimeMillis()
                updateNotification(reason)
                saveSnapshot()
                TrackDataChangeNotifier.notifyHistoryChanged()
            }
        }
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
        val raw = (segmentId * 1_000_003L) xor arrivalTime xor departureTime
        return raw.coerceAtLeast(1L)
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
            TrackingPhase.IDLE -> "低频待命采点"
            TrackingPhase.SUSPECT_MOVING -> "疑似移动升频中"
            TrackingPhase.ACTIVE -> "连续移动采集中"
            TrackingPhase.SUSPECT_STOPPING -> "保守降频观察中"
        }
    }

    private fun inferMotionType(
        location: Location,
        speedMetersPerSecond: Float,
    ): String {
        return if (speedMetersPerSecond >= 0.8f || location.hasSpeed() && location.speed >= 0.8f) {
            "WALKING"
        } else {
            "STILL"
        }
    }

    private fun inferMotionConfidence(
        location: Location,
        speedMetersPerSecond: Float,
    ): Float {
        val accuracyPenalty = ((location.accuracy.takeIf { location.hasAccuracy() } ?: 30f) / 100f)
            .coerceIn(0.05f, 0.5f)
        val speedSignal = (speedMetersPerSecond / 2.5f).coerceIn(0f, 1f)
        return max(0.55f, (0.95f - accuracyPenalty) + (speedSignal * 0.2f)).coerceIn(0f, 0.98f)
    }

    private fun inferredSpeed(
        previousPoint: TrackPoint?,
        currentPoint: TrackPoint,
        location: Location,
    ): Float {
        if (location.hasSpeed()) {
            return location.speed
        }
        if (previousPoint == null) return 0f
        val timeDeltaMillis = currentPoint.timestampMillis - previousPoint.timestampMillis
        if (timeDeltaMillis <= 0L) return 0f
        val distanceMeters = GeoMath.distanceMeters(previousPoint, currentPoint)
        return distanceMeters / (timeDeltaMillis / 1000f)
    }

    private fun latestProvider(timestampMillis: Long): String {
        return latestPoint
            ?.takeIf { it.timestampMillis == timestampMillis }
            ?.let { "cached" }
            ?: "location"
    }

    private fun Location.toTrackPoint(): TrackPoint {
        return TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = time,
            accuracyMeters = accuracy.takeIf { hasAccuracy() },
            altitudeMeters = altitude.takeIf { hasAltitude() },
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun runOnTrackingThread(block: () -> Unit) {
        TrackingThreadDispatch.dispatch(
            handler = trackingHandler,
            currentLooper = Looper.myLooper(),
            block = block,
        )
    }
}
