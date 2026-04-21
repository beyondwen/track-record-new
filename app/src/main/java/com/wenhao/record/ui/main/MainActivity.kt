package com.wenhao.record.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wenhao.record.BuildConfig
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.AutoTrackDiagnostics
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.ContinuousPointStorage
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.WorkerAppConfigResult
import com.wenhao.record.data.tracking.WorkerAppConfigService
import com.wenhao.record.data.tracking.WorkerConnectivityResult
import com.wenhao.record.data.tracking.WorkerConnectivityService
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.stability.CrashLogStore
import com.wenhao.record.tracking.LocationSelectionUtils
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.update.ApkDownloadInstaller
import com.wenhao.record.update.AppUpdateInfo
import com.wenhao.record.update.GithubReleaseUpdateService
import com.wenhao.record.update.UpdateCheckResult
import com.wenhao.record.ui.dashboard.DashboardUiController
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.history.HistoryController
import com.wenhao.record.ui.map.MapActivity
import com.wenhao.record.ui.map.MapboxTokenStorage
import com.wenhao.record.ui.map.isMapboxAccessTokenConfigured
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TrackRecordMain"
    }

    private val updateService by lazy {
        GithubReleaseUpdateService(
            owner = "beyondwen",
            repo = "track-record-new",
        )
    }
    private val workerConnectivityService by lazy { WorkerConnectivityService() }
    private val workerAppConfigService by lazy { WorkerAppConfigService() }
    private val apkDownloadInstaller by lazy { ApkDownloadInstaller(this) }
    private val continuousPointStorage by lazy {
        ContinuousPointStorage(TrackDatabase.getInstance(this).continuousTrackDao())
    }
    private lateinit var dashboardUiController: DashboardUiController
    private lateinit var homeMapController: HomeMapPort
    private lateinit var historyController: HistoryController

    private val permissionHelper by lazy {
        PermissionHelper(
            activity = this,
            onRefreshGpsStatus = ::refreshGpsStatus,
            onLocateGranted = ::centerOnCurrentLocation,
            onRefreshDashboard = ::refreshDashboardContent,
        )
    }

    private var locationManager: LocationManager? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var centerOnNextFix = false
    private var lastDashboardTrackingEnabled = false
    private var previewLocationCache: GeoCoordinate? = null
    private var previewLocationCachedAt = 0L
    private var currentTab by mutableStateOf(MainTab.RECORD)
    private var mapboxAccessToken by mutableStateOf("")
    private var aboutState by mutableStateOf(
        AboutUiState(appVersionLabel = "")
    )
    private var activeSessionPoints: List<TrackPoint> = emptyList()
    private var activeSessionLoadGeneration = 0L

    private val defaultLatLng = GeoCoordinate(39.9042, 116.4074)
    private val dataChangeListener = object : TrackDataChangeNotifier.Listener {
        override fun onDashboardDataChanged() {
            refreshDashboardContent()
        }

        override fun onHistoryDataChanged() {
            historyController.reload()
            historyController.updateContent()
            homeMapController.shouldRefit = true
            refreshDashboardContent()
        }
    }
    private val freshLocationListener = android.location.LocationListener(::handleLocationUpdate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        restoreAboutState()

        dashboardUiController = DashboardUiController(this)
        homeMapController = HomeMapController()
        historyController = HistoryController(this)
        locationManager = getSystemService(LocationManager::class.java)

        setContent {
            TrackRecordTheme {
                MainComposeScreen(
                    currentTab = currentTab,
                    dashboardState = dashboardUiController.panelState,
                    dashboardOverlayState = dashboardUiController.overlayState,
                    historyState = historyController.uiState,
                    aboutState = aboutState,
                    mapboxAccessToken = mapboxAccessToken,
                    dashboardMapState = homeMapController.renderState,
                    onRecordTabClick = { showTab(MainTab.RECORD) },
                    onHistoryTabClick = { showTab(MainTab.HISTORY) },
                    onAboutTabClick = { showTab(MainTab.ABOUT) },
                    onAboutBackClick = { showTab(MainTab.RECORD) },
                    onCheckUpdateClick = ::checkForAppUpdate,
                    onMapboxTokenChange = ::updateMapboxTokenInput,
                    onMapboxTokenSaveClick = ::saveMapboxToken,
                    onMapboxTokenClearClick = ::clearMapboxToken,
                    onWorkerBaseUrlChange = ::updateWorkerBaseUrlInput,
                    onUploadTokenChange = ::updateUploadTokenInput,
                    onSampleUploadConfigSaveClick = ::saveSampleUploadConfig,
                    onSampleUploadConfigClearClick = ::clearSampleUploadConfig,
                    onWorkerConnectivityTestClick = ::testWorkerConnectivity,
                    onLocateClick = ::handleLocateAction,
                    onHistoryOpen = { dayStartMillis ->
                        startActivity(MapActivity.createHistoryIntent(this, dayStartMillis))
                    },
                    onHistoryDelete = ::confirmDeleteHistoryDay,
                )
            }
        }

        configureHomeMap()
        initGnssStatusCallback()
        historyController.reload()
        historyController.updateContent()
        refreshDashboardContent()
        showTab(MainTab.RECORD)
        refreshGpsStatus()
        permissionHelper.ensureSmartTrackingEnabled()
        syncMapboxTokenFromWorkerIfNeeded()
    }

    private fun showTab(tab: MainTab) {
        currentTab = tab
        val isRecord = tab == MainTab.RECORD
        dashboardUiController.setRecordTabSelected(isRecord)

        if (tab == MainTab.HISTORY) {
            historyController.reload()
            historyController.updateContent()
            return
        }

        if (tab == MainTab.RECORD) {
            syncRecordTabToCurrentLocation()
            refreshDashboardContent()
        }
    }

    private fun buildVersionLabel(): String {
        return "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})"
    }

    private fun restoreAboutState() {
        val savedToken = MapboxTokenStorage.load(this)
        val uploadConfig = TrainingSampleUploadConfigStorage.load(this)
        mapboxAccessToken = savedToken
        aboutState = AboutUiState(
            appVersionLabel = buildVersionLabel(),
            mapboxTokenInput = savedToken,
            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
            workerBaseUrlInput = uploadConfig.workerBaseUrl,
            uploadTokenInput = uploadConfig.uploadToken,
            hasConfiguredSampleUpload = hasConfiguredSampleUpload(uploadConfig),
        )
    }

    private fun updateMapboxTokenInput(value: String) {
        aboutState = aboutState.copy(mapboxTokenInput = value)
    }

    private fun saveMapboxToken() {
        val savedToken = MapboxTokenStorage.save(this, aboutState.mapboxTokenInput)
        mapboxAccessToken = savedToken
        aboutState = aboutState.copy(
            mapboxTokenInput = savedToken,
            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
        )
        Toast.makeText(this, "Mapbox Token 已保存到当前设备", Toast.LENGTH_SHORT).show()
    }

    private fun clearMapboxToken() {
        MapboxTokenStorage.clear(this)
        mapboxAccessToken = ""
        aboutState = aboutState.copy(
            mapboxTokenInput = "",
            hasConfiguredMapboxToken = false,
        )
        Toast.makeText(this, "已清空当前设备上的 Mapbox Token", Toast.LENGTH_SHORT).show()
    }

    private fun updateWorkerBaseUrlInput(value: String) {
        aboutState = aboutState.copy(workerBaseUrlInput = value)
    }

    private fun updateUploadTokenInput(value: String) {
        aboutState = aboutState.copy(uploadTokenInput = value)
    }

    private fun saveSampleUploadConfig() {
        val newConfig = TrainingSampleUploadConfig(
            workerBaseUrl = aboutState.workerBaseUrlInput,
            uploadToken = aboutState.uploadTokenInput,
        )
        TrainingSampleUploadConfigStorage.save(this, newConfig)
        val savedConfig = TrainingSampleUploadConfigStorage.load(this)
        aboutState = aboutState.copy(
            workerBaseUrlInput = savedConfig.workerBaseUrl,
            uploadTokenInput = savedConfig.uploadToken,
            hasConfiguredSampleUpload = hasConfiguredSampleUpload(savedConfig),
        )
        aboutState = aboutState.copy(statusMessage = "Worker 上传配置已保存，正在同步 Mapbox Token…")
        Toast.makeText(this, "Worker 上传配置已保存", Toast.LENGTH_SHORT).show()
        syncMapboxTokenFromWorker(
            config = savedConfig,
            forceRefresh = true,
        )
    }

    private fun clearSampleUploadConfig() {
        TrainingSampleUploadConfigStorage.clear(this)
        aboutState = aboutState.copy(
            workerBaseUrlInput = "",
            uploadTokenInput = "",
            hasConfiguredSampleUpload = false,
        )
        Toast.makeText(this, "Worker 上传配置已清空", Toast.LENGTH_SHORT).show()
    }

    private fun hasConfiguredSampleUpload(config: TrainingSampleUploadConfig): Boolean {
        return config.workerBaseUrl.isNotBlank() && config.uploadToken.isNotBlank()
    }

    private fun syncMapboxTokenFromWorkerIfNeeded() {
        if (isMapboxAccessTokenConfigured(mapboxAccessToken)) {
            return
        }
        val config = TrainingSampleUploadConfigStorage.load(this)
        if (!hasConfiguredSampleUpload(config)) {
            return
        }
        syncMapboxTokenFromWorker(config, forceRefresh = false)
    }

    private fun syncMapboxTokenFromWorker(
        config: TrainingSampleUploadConfig,
        forceRefresh: Boolean,
    ) {
        if (!hasConfiguredSampleUpload(config)) {
            return
        }
        if (!forceRefresh && isMapboxAccessTokenConfigured(mapboxAccessToken)) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Syncing Mapbox token from worker ${config.workerBaseUrl}")
                when (val result = workerAppConfigService.load(config)) {
                    is WorkerAppConfigResult.Success -> {
                        val savedToken = MapboxTokenStorage.save(this@MainActivity, result.mapboxPublicToken)
                        launch(Dispatchers.Main) {
                            mapboxAccessToken = savedToken
                            aboutState = aboutState.copy(
                                mapboxTokenInput = savedToken,
                                hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
                                statusMessage = "已从 Worker 同步 Mapbox Token",
                            )
                        }
                    }

                    is WorkerAppConfigResult.Failure -> {
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(statusMessage = result.message)
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Mapbox token sync crashed", error)
                launch(Dispatchers.Main) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "同步 Mapbox Token 失败"
                    aboutState = aboutState.copy(statusMessage = message)
                }
            }
        }
    }

    private fun testWorkerConnectivity() {
        if (aboutState.isTestingWorkerConnectivity) return
        if (aboutState.isCheckingUpdate) {
            aboutState = aboutState.copy(statusMessage = "检查更新进行中，请稍后再测试")
            return
        }

        val workerBaseUrl = aboutState.workerBaseUrlInput.trim()
        if (workerBaseUrl.isBlank()) {
            aboutState = aboutState.copy(statusMessage = "请先填写 Worker 地址")
            return
        }

        aboutState = aboutState.copy(
            isTestingWorkerConnectivity = true,
            statusMessage = "正在检查 Worker…",
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Testing worker connectivity for $workerBaseUrl")
                val result = workerConnectivityService.check(workerBaseUrl)
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(
                        statusMessage = when (result) {
                            is WorkerConnectivityResult.Reachable -> result.message
                            is WorkerConnectivityResult.Unreachable -> result.message
                        }
                    )
                }
                when (result) {
                    is WorkerConnectivityResult.Reachable -> Log.i(TAG, "Worker connectivity check succeeded: ${result.message}")
                    is WorkerConnectivityResult.Unreachable -> Log.e(TAG, "Worker connectivity check failed: ${result.message}")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Worker connectivity check crashed", error)
                launch(Dispatchers.Main) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "Worker 检查失败"
                    aboutState = aboutState.copy(statusMessage = message)
                }
            } finally {
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(isTestingWorkerConnectivity = false)
                }
            }
        }
    }

    private fun checkForAppUpdate() {
        if (aboutState.isTestingWorkerConnectivity) {
            aboutState = aboutState.copy(statusMessage = "Worker 检查进行中，请稍后再检查更新")
            return
        }
        aboutState = aboutState.copy(isCheckingUpdate = true, statusMessage = null)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = updateService.checkForUpdate(currentVersionCode = BuildConfig.APP_VERSION_CODE)
            launch(Dispatchers.Main) {
                aboutState = aboutState.copy(isCheckingUpdate = false)
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        aboutState = aboutState.copy(statusMessage = "当前已是最新版本")
                    }
                    is UpdateCheckResult.Failure -> {
                        aboutState = aboutState.copy(statusMessage = result.message)
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        aboutState = aboutState.copy(statusMessage = "发现新版本：${result.info.versionName}")
                        showUpdateConfirmDialog(result.info)
                    }
                }
            }
        }
    }

    private fun showUpdateConfirmDialog(info: AppUpdateInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本")
            .setMessage("检测到新版本 ${info.versionName}，是否下载并安装？")
            .setNegativeButton("取消", null)
            .setPositiveButton("更新") { _, _ ->
                downloadAndInstallUpdate(info)
            }
            .show()
    }

    private fun downloadAndInstallUpdate(info: AppUpdateInfo) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            aboutState = aboutState.copy(statusMessage = "请先允许安装未知应用")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri()
                )
            )
            return
        }

        aboutState = aboutState.copy(statusMessage = "正在下载更新…")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting APK download. version=${info.versionName}, url=${info.apkUrl}")
                val apkFile = apkDownloadInstaller.download(info.apkUrl, info.apkName)
                Log.i(
                    TAG,
                    "APK download finished. file=${apkFile.absolutePath}, bytes=${apkFile.length()}"
                )
                launch(Dispatchers.Main) {
                    openDownloadedApk(apkFile)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to download APK from ${info.apkUrl}", error)
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(statusMessage = "下载失败")
                }
            }
        }
    }

    private fun openDownloadedApk(apkFile: File) {
        aboutState = aboutState.copy(statusMessage = "下载完成，正在打开安装器")
        val primaryIntent = apkDownloadInstaller.createInstallIntent(apkFile)
        if (startInstallerIntent(primaryIntent, "package-installer")) {
            return
        }

        val fallbackIntent = apkDownloadInstaller.createFallbackInstallIntent(apkFile)
        if (startInstallerIntent(fallbackIntent, "view-fallback")) {
            return
        }

        aboutState = aboutState.copy(statusMessage = "打开安装器失败")
        Toast.makeText(this, "打开安装器失败", Toast.LENGTH_SHORT).show()
    }

    private fun startInstallerIntent(intent: Intent, source: String): Boolean {
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved == null) {
            Log.w(TAG, "No installer activity resolved for $source")
            return false
        }

        return runCatching {
            Log.i(
                TAG,
                "Opening installer via $source: ${resolved.activityInfo.packageName}/${resolved.activityInfo.name}"
            )
            startActivity(intent)
        }.onFailure { error ->
            Log.e(TAG, "Failed to open package installer via $source", error)
        }.isSuccess
    }

    private fun syncRecordTabToCurrentLocation() {
        val previewLocation = loadPreviewLocation(forceRefresh = true)
        when {
            previewLocation != null -> {
                homeMapController.updateCurrentLocation(
                    latLng = previewLocation,
                    shouldCenter = true,
                )
            }

            homeMapController.hasActiveTrack() -> {
                homeMapController.focusActiveTrackOnLatestPoint(forceZoom = true)
            }
        }

        if (permissionHelper.hasLocationPermission() && isLocationEnabled()) {
            centerOnNextFix = true
            requestFreshLocationUpdates()
        }
    }

    private fun handleLocateAction() {
        centerOnCurrentLocation()
    }

    private fun configureHomeMap() {
        homeMapController.configure(
            previewLocation = loadPreviewLocation(forceRefresh = true),
            hasLocationPermission = permissionHelper.hasLocationPermission(),
            defaultCoordinate = defaultLatLng,
        )
    }

    private fun initGnssStatusCallback() {
        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onStarted() {
                dashboardUiController.updateGpsStatusBadge(
                    label = "正在搜索 GPS",
                    dotColorRes = R.color.dashboard_badge_yellow,
                )
            }

            override fun onStopped() {
                refreshGpsStatus()
            }

            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedInFixCount = 0
                var totalCn0DbHz = 0f
                for (index in 0 until status.satelliteCount) {
                    if (status.usedInFix(index)) {
                        usedInFixCount++
                        totalCn0DbHz += status.getCn0DbHz(index)
                    }
                }
                val averageCn0 = if (usedInFixCount > 0) totalCn0DbHz / usedInFixCount else 0f
                when {
                    usedInFixCount == 0 -> dashboardUiController.updateGpsStatusBadge(
                        "正在搜索 GPS",
                        R.color.dashboard_badge_yellow,
                    )

                    averageCn0 >= 20f -> dashboardUiController.updateGpsStatusBadge(
                        "GPS 已就绪",
                        R.color.dashboard_badge_green,
                    )

                    else -> dashboardUiController.updateGpsStatusBadge(
                        "信号较弱",
                        R.color.dashboard_badge_red,
                    )
                }
            }
        }
    }

    private fun refreshDashboardContent() {
        val runtimeSnapshot = TrackingRuntimeSnapshotStorage.peek(this)
        val previewLocation = loadPreviewLocation()
        val todayHistoryItems = HistoryStorage.peek(this)
        val previousTrackingEnabled = lastDashboardTrackingEnabled
        lastDashboardTrackingEnabled = runtimeSnapshot.isEnabled
        val dashboardState = currentDashboardState(runtimeSnapshot)

        dashboardUiController.render(runtimeSnapshot, dashboardState, todayHistoryItems)
        homeMapController.render(
            runtimeSnapshot = runtimeSnapshot,
            previewLocation = previewLocation,
            todayHistoryItems = todayHistoryItems,
            activeSessionPoints = activeSessionPoints,
        )
        renderDashboardDiagnosticsRefined()
        refreshActiveSessionTrack(
            runtimeSnapshot = runtimeSnapshot,
            previewLocation = previewLocation,
            todayHistoryItems = todayHistoryItems,
        )

        if (previousTrackingEnabled && !runtimeSnapshot.isEnabled) {
            homeMapController.shouldRefit = true
            historyController.reload()
            historyController.updateContent()
        }
    }

    private fun currentDashboardState(runtimeSnapshot: TrackingRuntimeSnapshot): AutoTrackUiState {
        if (!permissionHelper.canRunBackgroundTracking()) {
            return AutoTrackUiState.WAITING_PERMISSION
        }

        if (!runtimeSnapshot.isEnabled) {
            return AutoTrackUiState.IDLE
        }

        return when (runtimeSnapshot.phase) {
            com.wenhao.record.tracking.TrackingPhase.ACTIVE -> AutoTrackUiState.TRACKING
            com.wenhao.record.tracking.TrackingPhase.SUSPECT_MOVING -> AutoTrackUiState.PREPARING
            com.wenhao.record.tracking.TrackingPhase.SUSPECT_STOPPING -> AutoTrackUiState.PAUSED_STILL
            com.wenhao.record.tracking.TrackingPhase.IDLE -> AutoTrackUiState.IDLE
        }
    }

    private fun refreshActiveSessionTrack(
        runtimeSnapshot: TrackingRuntimeSnapshot,
        previewLocation: GeoCoordinate?,
        todayHistoryItems: List<com.wenhao.record.data.history.HistoryItem>,
    ) {
        val shouldLoad = runtimeSnapshot.isEnabled && runtimeSnapshot.phase != TrackingPhase.IDLE
        if (!shouldLoad) {
            if (activeSessionPoints.isEmpty()) return
            activeSessionPoints = emptyList()
            homeMapController.render(
                runtimeSnapshot = runtimeSnapshot,
                previewLocation = previewLocation,
                todayHistoryItems = todayHistoryItems,
                activeSessionPoints = emptyList(),
            )
            return
        }

        activeSessionLoadGeneration += 1
        val generation = activeSessionLoadGeneration
        lifecycleScope.launch(Dispatchers.IO) {
            val loadedPoints = continuousPointStorage
                .loadCurrentSessionPoints(limit = 2_048)
                .map { rawPoint ->
                    TrackPoint(
                        latitude = rawPoint.latitude,
                        longitude = rawPoint.longitude,
                        timestampMillis = rawPoint.timestampMillis,
                        accuracyMeters = rawPoint.accuracyMeters,
                        altitudeMeters = rawPoint.altitudeMeters,
                    )
                }
            launch(Dispatchers.Main) {
                if (isFinishing || isDestroyed || generation != activeSessionLoadGeneration) {
                    return@launch
                }
                if (loadedPoints == activeSessionPoints) {
                    return@launch
                }
                activeSessionPoints = loadedPoints
                homeMapController.render(
                    runtimeSnapshot = runtimeSnapshot,
                    previewLocation = previewLocation,
                    todayHistoryItems = todayHistoryItems,
                    activeSessionPoints = loadedPoints,
                )
            }
        }
    }

    private fun renderDashboardDiagnosticsRefined() {
        val diagnostics = AutoTrackDiagnosticsStorage.load(this)
        val permissionSummary = buildPermissionSummarySafe()
        val eventSummary = buildTimedSummary(diagnostics.lastEventAt, diagnostics.lastEvent)
        val locationSummary = buildLocationSummary(diagnostics)
        val saveSummary = diagnostics.lastSavedSummary?.let { summary ->
            buildTimedSummary(diagnostics.lastSavedAt, summary)
        } ?: "还没有完成并写入历史的有效轨迹分析"
        val crashSummary = CrashLogStore.latestSummary(this)

        val body = buildString {
            append("权限：").append(permissionSummary).append('\n')
            append("服务：").append(diagnostics.serviceStatus).append('\n')
            append("最近事件：").append(eventSummary).append('\n')
            append("最近定位：").append(locationSummary).append('\n')
            append("最近保存：").append(saveSummary)
            if (!crashSummary.isNullOrBlank()) {
                append('\n').append("最近异常：").append(crashSummary)
            }
        }
        val compactBody = buildString {
            append(diagnostics.serviceStatus).append('\n')
            append(eventSummary).append('\n')
            append(
                if (diagnostics.lastLocationAt > 0L) {
                    buildTimedSummary(diagnostics.lastLocationAt, diagnostics.lastLocationDecision)
                } else {
                    "等待定位信号"
                }
            )

            if (!crashSummary.isNullOrBlank()) {
                append('\n').append(crashSummary)
            }
        }
        dashboardUiController.updateDiagnostics(
            title = "采点诊断",
            body = body,
            compactBody = compactBody,
        )
    }

    private fun buildPermissionSummarySafe(): String = when {
        !permissionHelper.hasLocationPermission() -> "缺少定位权限"
        permissionHelper.needsBackgroundLocationPermission() -> "缺少后台定位权限"
        !permissionHelper.hasActivityRecognitionPermission() -> "缺少活动识别权限"
        permissionHelper.needsNotificationPermission() -> "通知权限未开启"
        else -> "权限完整"
    }

    private fun buildLocationSummary(diagnostics: AutoTrackDiagnostics): String {
        if (diagnostics.lastLocationAt <= 0L) {
            return "暂未收到定位点"
        }

        return buildString {
            append(buildTimedSummary(diagnostics.lastLocationAt, diagnostics.lastLocationDecision))
            append("，已采集 ").append(diagnostics.acceptedPointCount).append(" 个点")
            diagnostics.lastLocationAccuracyMeters?.let { accuracy ->
                append("，精度约 ").append(accuracy.toInt()).append(" 米")
            }
        }
    }

    private fun buildTimedSummary(timestamp: Long, summary: String): String {
        if (timestamp <= 0L) return summary
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "${hour}:${minute} $summary"
    }

    private fun refreshGpsStatus() {
        when {
            !permissionHelper.hasLocationPermission() -> {
                dashboardUiController.updateGpsStatusBadge("未授予定位权限", R.color.dashboard_badge_gray)
            }

            !isLocationEnabled() -> {
                dashboardUiController.updateGpsStatusBadge("定位服务未开启", R.color.dashboard_badge_red)
            }

            loadPreviewLocation() != null -> {
                dashboardUiController.updateGpsStatusBadge("GPS 已就绪", R.color.dashboard_badge_green)
            }

            else -> {
                dashboardUiController.updateGpsStatusBadge("正在搜索 GPS", R.color.dashboard_badge_yellow)
            }
        }
    }

    private fun confirmDeleteHistoryDay(dayStartMillis: Long) {
        val item = historyController.uiState.items.firstOrNull { it.dayStartMillis == dayStartMillis } ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compose_history_delete_title)
            .setMessage(getString(R.string.compose_history_delete_message, item.displayTitle))
            .setNegativeButton(R.string.compose_history_delete_cancel, null)
            .setPositiveButton(R.string.compose_history_delete_confirm) { _, _ ->
                historyController.deleteHistory(item)
                refreshDashboardContent()
                Toast.makeText(this, R.string.compose_history_delete_success, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun centerOnCurrentLocation() {
        if (!permissionHelper.hasLocationPermission()) {
            permissionHelper.requestLocatePermissionOrRun()
            return
        }

        if (!isLocationEnabled()) {
            refreshGpsStatus()
            Toast.makeText(this, R.string.location_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        centerOnNextFix = true
        requestFreshLocationUpdates()

        loadPreviewLocation(forceRefresh = true)?.let {
            homeMapController.centerOnPreviewLocation(it)
        } ?: Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocationUpdates() {
        val manager = locationManager ?: return
        val providers = collectEnabledProviders(manager)
        if (providers.isEmpty()) {
            refreshGpsStatus()
            Toast.makeText(this, R.string.location_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        stopLocationUpdates()
        providers.forEach { provider ->
            manager.requestLocationUpdates(provider, 1000L, 1f, freshLocationListener, Looper.getMainLooper())
        }
    }

    private fun collectEnabledProviders(manager: LocationManager): List<String> {
        val providers = mutableListOf<String>()
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers += LocationManager.GPS_PROVIDER
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers += LocationManager.NETWORK_PROVIDER
        }
        if (providers.isEmpty() && manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            providers += LocationManager.PASSIVE_PROVIDER
        }
        return providers
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(freshLocationListener)
    }

    private fun handleLocationUpdate(location: Location) {
        val previewLocation = toMapCoordinate(location)
        previewLocationCache = previewLocation
        previewLocationCachedAt = System.currentTimeMillis()
        homeMapController.updateCurrentLocation(
            latLng = previewLocation,
            shouldCenter = centerOnNextFix,
        )
        centerOnNextFix = false
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation(): Location? {
        if (!permissionHelper.hasLocationPermission()) return null
        val manager = locationManager ?: return null
        return LocationSelectionUtils.loadBestLastKnownLocation(
            locationManager = manager,
            hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
            maxAgeMs = 15 * 60 * 1000L,
        )
    }

    private fun loadPreviewLocation(forceRefresh: Boolean = false): GeoCoordinate? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - previewLocationCachedAt < 5_000L) {
            return previewLocationCache
        }
        val lastKnownLocation = loadLastKnownLocation()
        val previewLocation = lastKnownLocation?.let(::toMapCoordinate)
        previewLocationCache = previewLocation
        previewLocationCachedAt = now
        return previewLocation
    }

    private fun toMapCoordinate(location: Location): GeoCoordinate {
        return GeoCoordinate(
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssCallback() {
        if (!permissionHelper.hasLocationPermission()) return
        val manager = locationManager ?: return
        val callback = gnssStatusCallback ?: return
        manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun unregisterGnssCallback() {
        val manager = locationManager ?: return
        val callback = gnssStatusCallback ?: return
        manager.unregisterGnssStatusCallback(callback)
    }

    override fun onResume() {
        super.onResume()
        TrackDataChangeNotifier.addListener(dataChangeListener)
        permissionHelper.startBackgroundTrackingServiceIfReady()
        registerGnssCallback()
        refreshGpsStatus()
        historyController.reload()
        historyController.updateContent()
        homeMapController.shouldRefit = true
        refreshDashboardContent()
    }

    override fun onPause() {
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        unregisterGnssCallback()
        stopLocationUpdates()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterGnssCallback()
        stopLocationUpdates()
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        homeMapController.onCleared()
        super.onDestroy()
    }
}
