package com.wenhao.record.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TodayTrackDisplayCache
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import com.wenhao.record.stability.CrashLogStore
import com.wenhao.record.tracking.LocationSelectionUtils
import com.wenhao.record.tracking.TrackingPhase
import com.wenhao.record.ui.dashboard.DashboardViewModel
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.history.HistoryViewModel
import com.wenhao.record.ui.map.MapActivity
import com.wenhao.record.update.ApkDownloadInstaller
import com.wenhao.record.update.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TrackRecordMain"
    }

    private val apkDownloadInstaller by lazy { ApkDownloadInstaller(this) }

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
    private var bestFreshLocation: Location? = null
    private var freshLocationRequestStartedAt = 0L
    private var activeSessionPoints: List<TrackPoint> = emptyList()
    private var activeSessionLoadGeneration = 0L
    private var skipNextResumeContentRefresh = true

    private val defaultLatLng = GeoCoordinate(39.9042, 116.4074)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dataChangeListener = object : TrackDataChangeNotifier.Listener {
        override fun onDashboardDataChanged() {
            refreshDashboardContent()
        }

        override fun onHistoryDataChanged() {
            applyRefreshDecision(
                MainUiRefreshPolicy.forHistoryChanged(currentTab = currentTab()),
            )
        }

        override fun onDiagnosticsChanged() {
            renderDashboardDiagnosticsRefined()
            refreshRecordingHealthState(TrackingRuntimeSnapshotStorage.peek(this@MainActivity))
        }
    }
    private val freshLocationListener = android.location.LocationListener(::handleLocationUpdate)
    private val freshLocationTimeoutRunnable = Runnable {
        bestFreshLocation?.let(::applyFreshLocation) ?: stopLocationUpdates()
    }

    private var mainViewModel: MainViewModel? = null
    private var dashboardViewModel: DashboardViewModel? = null
    private var historyViewModel: HistoryViewModel? = null
    private var mapViewModel: MapViewModel? = null
    private var recordingHealthState by mutableStateOf(RecordingHealthUiState.EMPTY)

    private fun getMainViewModel(): MainViewModel? = mainViewModel
    private fun getDashboardViewModel(): DashboardViewModel? = dashboardViewModel
    private fun getHistoryViewModel(): HistoryViewModel? = historyViewModel
    private fun getMapViewModel(): MapViewModel? = mapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeUsageRecorder.hit(RuntimeUsageModule.UI_MAIN_ACTIVITY)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        locationManager = getSystemService(LocationManager::class.java)

        setContent {
            TrackRecordTheme {
                val mainVm: MainViewModel = viewModel(factory = MainViewModel.factory(application))
                val dashboardVm: DashboardViewModel = viewModel()
                val historyVm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(application))
                val mapVm: MapViewModel = viewModel(factory = MapViewModel.factory(application))

                mainViewModel = mainVm
                dashboardViewModel = dashboardVm
                historyViewModel = historyVm
                mapViewModel = mapVm

                val currentTab by mainVm.currentTab.collectAsStateWithLifecycle()
                val dashboardState by dashboardVm.panelState.collectAsStateWithLifecycle()
                val dashboardOverlayState by dashboardVm.overlayState.collectAsStateWithLifecycle()
                val historyState by historyVm.uiState.collectAsStateWithLifecycle()
                val aboutState by mainVm.aboutState.collectAsStateWithLifecycle()
                val mapboxAccessToken by mainVm.mapboxAccessToken.collectAsStateWithLifecycle()
                val dashboardMapState by mapVm.renderState.collectAsStateWithLifecycle()

                MainComposeScreen(
                    currentTab = currentTab,
                    dashboardState = dashboardState,
                    dashboardOverlayState = dashboardOverlayState,
                    historyState = historyState,
                    aboutState = aboutState,
                    mapboxAccessToken = mapboxAccessToken,
                    dashboardMapState = dashboardMapState,
                    recordingHealthState = recordingHealthState,
                    onRecordTabClick = { showTab(MainTab.RECORD) },
                    onHistoryTabClick = { showTab(MainTab.HISTORY) },
                    onAboutTabClick = { showTab(MainTab.ABOUT) },
                    onAboutBackClick = { showTab(MainTab.RECORD) },
                    onCheckUpdateClick = {
                        mainVm.checkForAppUpdate { info -> showUpdateConfirmDialog(info) }
                    },
                    onMapboxTokenChange = mainVm::updateMapboxTokenInput,
                    onMapboxTokenSaveClick = mainVm::saveMapboxToken,
                    onMapboxTokenClearClick = mainVm::clearMapboxToken,
                    onWorkerBaseUrlChange = mainVm::updateWorkerBaseUrlInput,
                    onUploadTokenChange = mainVm::updateUploadTokenInput,
                    onSampleUploadConfigSaveClick = mainVm::saveSampleUploadConfig,
                    onSampleUploadConfigClearClick = mainVm::clearSampleUploadConfig,
                    onWorkerConnectivityTestClick = mainVm::testWorkerConnectivity,
                    onSyncDiagnosticsRefreshClick = { mainVm.refreshSyncDiagnostics() },
                    onLocateClick = ::handleLocateAction,
                    onRecordingHealthPrimaryAction = ::handleRecordingHealthPrimaryAction,
                    onHistoryOpen = { dayStartMillis ->
                        startActivity(MapActivity.createHistoryIntent(this, dayStartMillis))
                    },
                    onHistoryDelete = ::confirmDeleteHistoryDay,
                )
            }
        }

        observeTodayDisplayTrack()
        initGnssStatusCallback()
        warmUpHistoryCache()
        refreshDashboardContent()
        mainViewModel?.setCurrentTab(MainTab.RECORD)
        syncRecordTabToCurrentLocation()
        refreshGpsStatus()
    }

    private fun showTab(tab: MainTab) {
        mainViewModel?.setCurrentTab(tab)
        val isRecord = tab == MainTab.RECORD
        dashboardViewModel?.setRecordTabSelected(isRecord)

        applyRefreshDecision(MainUiRefreshPolicy.forTabSelection(tab))
    }

    private fun currentTab(): MainTab {
        return mainViewModel?.currentTab?.value ?: MainTab.RECORD
    }

    private fun warmUpHistoryCache() {
        HistoryStorage.warmUp(applicationContext)
    }

    private fun refreshHistoryContent() {
        historyViewModel?.reload()
    }

    private fun applyRefreshDecision(decision: MainUiRefreshDecision) {
        if (decision.warmUpHistory) {
            warmUpHistoryCache()
        }
        if (decision.refitMap) {
            mapViewModel?.shouldRefit = true
        }
        if (decision.reloadHistory) {
            refreshHistoryContent()
        }
        if (decision.refreshDashboard) {
            refreshDashboardContent()
        }
    }

    private fun initGnssStatusCallback() {
        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onStarted() {
                dashboardViewModel?.updateGpsStatusBadge(
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
                    usedInFixCount == 0 -> dashboardViewModel?.updateGpsStatusBadge(
                        "正在搜索 GPS",
                        R.color.dashboard_badge_yellow,
                    )

                    averageCn0 >= 20f -> dashboardViewModel?.updateGpsStatusBadge(
                        "GPS 已就绪",
                        R.color.dashboard_badge_green,
                    )

                    else -> dashboardViewModel?.updateGpsStatusBadge(
                        "信号较弱",
                        R.color.dashboard_badge_red,
                    )
                }
            }
        }
    }

    private fun refreshDashboardContent() {
        lifecycleScope.launch(Dispatchers.IO) {
            TodayTrackDisplayCache.clearIfExpired(this@MainActivity)
        }
        val runtimeSnapshot = TrackingRuntimeSnapshotStorage.peek(this)
        val previewLocation = loadPreviewLocation()
        val todayHistoryItems = HistoryStorage.peek(this)
        val previousTrackingEnabled = lastDashboardTrackingEnabled
        lastDashboardTrackingEnabled = runtimeSnapshot.isEnabled
        val dashboardState = currentDashboardState(runtimeSnapshot)

        dashboardViewModel?.render(runtimeSnapshot, dashboardState, todayHistoryItems)
        mapViewModel?.render(
            runtimeSnapshot = runtimeSnapshot,
            previewLocation = previewLocation,
            todayHistoryItems = todayHistoryItems,
            activeSessionPoints = activeSessionPoints,
        )
        refreshRecordingHealthState(runtimeSnapshot)
        renderDashboardDiagnosticsRefined()
        refreshActiveSessionTrack(
            runtimeSnapshot = runtimeSnapshot,
            previewLocation = previewLocation,
            todayHistoryItems = todayHistoryItems,
        )

        if (previousTrackingEnabled && !runtimeSnapshot.isEnabled) {
            mapViewModel?.shouldRefit = true
            historyViewModel?.reload()
            historyViewModel?.updateContent()
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
            TrackingPhase.ACTIVE -> AutoTrackUiState.TRACKING
            TrackingPhase.IDLE -> AutoTrackUiState.IDLE
        }
    }

    private fun refreshRecordingHealthState(runtimeSnapshot: TrackingRuntimeSnapshot) {
        val diagnostics = AutoTrackDiagnosticsStorage.load(this)
        recordingHealthState = buildRecordingHealthUiState(
            RecordingHealthInputs(
                hasLocationPermission = permissionHelper.hasLocationPermission(),
                hasBackgroundLocationPermission = !permissionHelper.needsBackgroundLocationPermission(),
                hasNotificationPermission = !permissionHelper.needsNotificationPermission(),
                ignoresBatteryOptimizations = !permissionHelper.shouldRequestIgnoreBatteryOptimizations(),
                trackingEnabled = runtimeSnapshot.isEnabled,
                trackingActive = isTrackingActive(runtimeSnapshot),
                diagnosticsStatus = diagnostics.serviceStatus,
                diagnosticsEvent = diagnostics.lastEvent,
                latestPointTimestampMillis = runtimeSnapshot.latestPoint?.timestampMillis,
            )
        )
    }

    private fun isTrackingActive(runtimeSnapshot: TrackingRuntimeSnapshot): Boolean {
        return deriveTrackingActive(
            isEnabled = runtimeSnapshot.isEnabled,
            phase = runtimeSnapshot.phase,
        )
    }


    private fun observeTodayDisplayTrack() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TodayTrackDisplayCache.observeToday(this@MainActivity)
                    .collectLatest { points ->
                        applyTodayDisplayTrack(points)
                    }
            }
        }
    }

    private fun applyTodayDisplayTrack(points: List<TrackPoint>) {
        val runtimeSnapshot = TrackingRuntimeSnapshotStorage.peek(this)
        val shouldRenderActiveTrack = runtimeSnapshot.isEnabled && runtimeSnapshot.phase != TrackingPhase.IDLE
        val nextPoints = if (shouldRenderActiveTrack) points else emptyList()
        if (nextPoints == activeSessionPoints) {
            return
        }

        activeSessionPoints = nextPoints
        mapViewModel?.render(
            runtimeSnapshot = runtimeSnapshot,
            previewLocation = loadPreviewLocation(),
            todayHistoryItems = HistoryStorage.peek(this),
            activeSessionPoints = nextPoints,
        )
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
            mapViewModel?.render(
                runtimeSnapshot = runtimeSnapshot,
                previewLocation = previewLocation,
                todayHistoryItems = todayHistoryItems,
                activeSessionPoints = emptyList(),
            )
            return
        }

        activeSessionLoadGeneration += 1
        val generation = activeSessionLoadGeneration
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val loadedPoints = TodayTrackDisplayCache.loadToday(this@MainActivity)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (isFinishing || isDestroyed || generation != activeSessionLoadGeneration) {
                    return@withContext
                }
                if (loadedPoints == activeSessionPoints) {
                    return@withContext
                }
                activeSessionPoints = loadedPoints
                mapViewModel?.render(
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
        dashboardViewModel?.updateDiagnostics(
            title = "采点诊断",
            body = body,
            compactBody = compactBody,
        )
    }

    private fun handleRecordingHealthPrimaryAction() {
        handleRecordingHealthAction(recordingHealthState.primaryAction)
    }

    private fun handleRecordingHealthAction(action: RecordingHealthAction) {
        when (action) {
            RecordingHealthAction.REQUEST_LOCATION_PERMISSION ->
                permissionHelper.requestLocationPermissionForRepair()

            RecordingHealthAction.OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION ->
                permissionHelper.openAppSettings()

            RecordingHealthAction.REQUEST_NOTIFICATION_PERMISSION ->
                permissionHelper.requestNotificationPermissionForRepair()

            RecordingHealthAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS ->
                permissionHelper.openBatteryOptimizationSettings()

            RecordingHealthAction.START_BACKGROUND_TRACKING ->
                permissionHelper.ensureBackgroundTrackingEnabled()

            RecordingHealthAction.STOP_BACKGROUND_TRACKING ->
                permissionHelper.stopBackgroundTracking()

            RecordingHealthAction.SHOW_DIAGNOSTICS -> Unit

            RecordingHealthAction.NO_OP -> Unit
        }
    }

    private fun buildPermissionSummarySafe(): String = when {
        !permissionHelper.hasLocationPermission() -> "缺少定位权限"
        permissionHelper.needsBackgroundLocationPermission() -> "缺少后台定位权限"
        permissionHelper.needsNotificationPermission() -> "通知权限未开启"
        else -> "权限完整"
    }

    private fun buildLocationSummary(diagnostics: com.wenhao.record.data.tracking.AutoTrackDiagnostics): String {
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
                dashboardViewModel?.updateGpsStatusBadge("未授予定位权限", R.color.dashboard_badge_gray)
            }

            !isLocationEnabled() -> {
                dashboardViewModel?.updateGpsStatusBadge("定位服务未开启", R.color.dashboard_badge_red)
            }

            loadPreviewLocation() != null -> {
                dashboardViewModel?.updateGpsStatusBadge("GPS 已就绪", R.color.dashboard_badge_green)
            }

            else -> {
                dashboardViewModel?.updateGpsStatusBadge("正在搜索 GPS", R.color.dashboard_badge_yellow)
            }
        }
    }

    private fun confirmDeleteHistoryDay(dayStartMillis: Long) {
        val item = historyViewModel?.uiState?.value?.items?.firstOrNull { it.dayStartMillis == dayStartMillis } ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compose_history_delete_title)
            .setMessage(getString(R.string.compose_history_delete_message, item.displayTitle))
            .setNegativeButton(R.string.compose_history_delete_cancel, null)
            .setPositiveButton(R.string.compose_history_delete_confirm) { _, _ ->
                lifecycleScope.launch {
                    val deleted = historyViewModel?.deleteHistory(item) == true
                    if (deleted) {
                        refreshDashboardContent()
                        Toast.makeText(
                            this@MainActivity,
                            R.string.compose_history_delete_success,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.compose_history_delete_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            .show()
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
            mainViewModel?.setAboutStatusMessage("请先允许安装未知应用")
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri()
                )
            )
            return
        }

        mainViewModel?.setAboutStatusMessage("正在下载更新…")
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Starting APK download. version=${info.versionName}, url=${info.apkUrl}")
                val apkFile = apkDownloadInstaller.download(info.apkUrl, info.apkName)
                Log.i(
                    TAG,
                    "APK download finished. file=${apkFile.absolutePath}, bytes=${apkFile.length()}"
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    openDownloadedApk(apkFile)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to download APK from ${info.apkUrl}", error)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    mainViewModel?.setAboutStatusMessage("下载失败")
                }
            }
        }
    }

    private fun openDownloadedApk(apkFile: java.io.File) {
        mainViewModel?.setAboutStatusMessage("下载完成，正在打开安装器")
        val primaryIntent = apkDownloadInstaller.createInstallIntent(apkFile)
        if (startInstallerIntent(primaryIntent, "package-installer")) {
            return
        }

        val fallbackIntent = apkDownloadInstaller.createFallbackInstallIntent(apkFile)
        if (startInstallerIntent(fallbackIntent, "view-fallback")) {
            return
        }

        mainViewModel?.setAboutStatusMessage("打开安装器失败")
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
                mapViewModel?.updateCurrentLocation(
                    latLng = previewLocation,
                    shouldCenter = true,
                )
            }

            mapViewModel?.hasActiveTrack() == true -> {
                mapViewModel?.focusActiveTrackOnLatestPoint(forceZoom = true)
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
            mapViewModel?.centerOnPreviewLocation(it, zoom = 17.0)
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
        bestFreshLocation = null
        freshLocationRequestStartedAt = System.currentTimeMillis()
        mainHandler.postDelayed(
            freshLocationTimeoutRunnable,
            FreshLocationSelectionPolicy.MAX_WAIT_MILLIS,
        )
        providers.forEach { provider ->
            manager.requestLocationUpdates(provider, 1_500L, 3f, freshLocationListener, Looper.getMainLooper())
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
        mainHandler.removeCallbacks(freshLocationTimeoutRunnable)
        bestFreshLocation = null
        freshLocationRequestStartedAt = 0L
        locationManager?.removeUpdates(freshLocationListener)
    }

    private fun handleLocationUpdate(location: Location) {
        if (FreshLocationSelectionPolicy.shouldReplaceBest(bestFreshLocation, location)) {
            bestFreshLocation = location
        }
        val bestLocation = bestFreshLocation ?: location
        if (!FreshLocationSelectionPolicy.shouldFinish(
                bestLocation = bestLocation,
                requestStartedAt = freshLocationRequestStartedAt,
                now = System.currentTimeMillis(),
            )
        ) {
            return
        }
        applyFreshLocation(bestLocation)
    }

    private fun applyFreshLocation(location: Location) {
        val previewLocation = toMapCoordinate(location)
        previewLocationCache = previewLocation
        previewLocationCachedAt = System.currentTimeMillis()
        mapViewModel?.updateCurrentLocation(
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
        applyRefreshDecision(
            MainUiRefreshPolicy.forResume(
                currentTab = currentTab(),
                skipContentRefresh = skipNextResumeContentRefresh,
            )
        )
        skipNextResumeContentRefresh = false
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
        mapViewModel?.onClearedInternal()
        super.onDestroy()
    }
}
