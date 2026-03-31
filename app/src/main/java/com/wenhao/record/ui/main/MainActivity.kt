package com.wenhao.record.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.HistoryTransferCodec
import com.wenhao.record.data.tracking.AutoTrackDiagnostics
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.stability.CrashLogStore
import com.wenhao.record.tracking.BackgroundTrackingService
import com.wenhao.record.tracking.LocationSelectionUtils
import com.wenhao.record.ui.dashboard.DashboardUiController
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.history.HistoryController
import com.wenhao.record.ui.map.MapActivity
import com.wenhao.record.util.AppTaskExecutor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var dashboardUiController: DashboardUiController
    private lateinit var homeMapController: HomeMapPort
    private lateinit var historyController: HistoryController

    private val permissionHelper by lazy {
        PermissionHelper(
            activity = this,
            onRefreshGpsStatus = ::refreshGpsStatus,
            onLocateGranted = ::centerOnCurrentLocation,
            onRefreshDashboard = ::refreshDashboardContent,
            onStartBackgroundTracking = { BackgroundTrackingService.start(this) },
        )
    }

    private val exportHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            setHistoryTransferBusy(false)
        } else {
            exportHistoryToUri(uri)
        }
    }

    private val importHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            setHistoryTransferBusy(false)
        } else {
            importHistoryFromUri(uri)
        }
    }

    private var locationManager: LocationManager? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var centerOnNextFix = false
    private var lastDashboardSessionStart: Long? = null
    private var previewLocationCache: GeoCoordinate? = null
    private var previewLocationCachedAt = 0L
    private var historyTransferBusy = false
    private var currentTab by mutableStateOf(MainTab.RECORD)

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
                    dashboardMapState = homeMapController.renderState,
                    onRecordTabClick = { showTab(MainTab.RECORD) },
                    onHistoryTabClick = { showTab(MainTab.HISTORY) },
                    onLocateClick = ::handleLocateAction,
                    onHistoryOpen = { dayStartMillis ->
                        startActivity(MapActivity.createHistoryIntent(this, dayStartMillis))
                    },
                    onHistoryDelete = ::confirmDeleteHistoryDay,
                    onHistoryExport = ::requestHistoryExport,
                    onHistoryImport = ::requestHistoryImport,
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
    }

    private fun showTab(tab: MainTab) {
        currentTab = tab
        val isRecord = tab == MainTab.RECORD
        dashboardUiController.setRecordTabSelected(isRecord)
        historyController.setTabSelected(isRecord)

        if (!isRecord) {
            historyController.reload()
            historyController.updateContent()
            return
        }

        homeMapController.shouldRefit = true
        refreshDashboardContent()
        homeMapController.showPreviewLocationIfIdle(loadPreviewLocation())
    }

    private fun handleLocateAction() {
        when {
            homeMapController.hasActiveTrack() -> {
                homeMapController.focusActiveTrackOnLatestPoint(forceZoom = true)
            }

            homeMapController.hasTodayTracks() -> {
                homeMapController.fitTodayTracksToMap(forceSinglePointZoom = true)
            }

            else -> centerOnCurrentLocation()
        }
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
        val session = AutoTrackStorage.peekSession(this)
        val previewLocation = loadPreviewLocation()
        val todayHistoryItems = HistoryStorage.peek(this)
        val previousSessionStart = lastDashboardSessionStart
        lastDashboardSessionStart = session?.startTimestamp
        val dashboardState = currentDashboardState(session)
        val durationSeconds = session?.let {
            ((System.currentTimeMillis() - it.startTimestamp) / 1000L).toInt()
        } ?: 0

        dashboardUiController.render(session, dashboardState, durationSeconds)
        homeMapController.render(
            session = session,
            previewLocation = previewLocation,
            todayHistoryItems = todayHistoryItems,
        )
        renderDashboardDiagnosticsRefined()

        if (previousSessionStart != null && session == null) {
            homeMapController.shouldRefit = true
            historyController.reload()
            historyController.updateContent()
        }
    }

    private fun currentDashboardState(session: AutoTrackSession?): AutoTrackUiState {
        if (!permissionHelper.hasSmartTrackingBasePermissions() ||
            permissionHelper.needsBackgroundLocationPermission()
        ) {
            return AutoTrackUiState.WAITING_PERMISSION
        }

        val savedState = AutoTrackStorage.loadUiState(this)
        return if (session != null && savedState == AutoTrackUiState.IDLE) {
            AutoTrackUiState.TRACKING
        } else {
            savedState
        }
    }

    private fun renderDashboardDiagnosticsRefined() {
        val diagnostics = AutoTrackDiagnosticsStorage.load(this)
        val permissionSummary = buildPermissionSummarySafe()
        val eventSummary = buildTimedSummary(diagnostics.lastEventAt, diagnostics.lastEvent)
        val locationSummary = buildLocationSummary(diagnostics)
        val saveSummary = diagnostics.lastSavedSummary?.let { summary ->
            buildTimedSummary(diagnostics.lastSavedAt, summary)
        } ?: "还没有自动保存过有效行程"
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
            title = "记录诊断",
            body = body,
            compactBody = compactBody,
        )
    }

    private fun buildPermissionSummarySafe(): String = when {
        !permissionHelper.hasLocationPermission() -> "缺少定位权限"
        !permissionHelper.hasActivityRecognitionPermission() -> "缺少活动识别权限"
        permissionHelper.needsBackgroundLocationPermission() -> "缺少“始终允许定位”"
        permissionHelper.shouldRequestIgnoreBatteryOptimizations() -> {
            getString(R.string.battery_optimization_settings_title)
        }
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

    private fun requestHistoryExport() {
        if (historyTransferBusy) return
        val items = HistoryStorage.peek(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "当前没有可导出的历史记录", Toast.LENGTH_SHORT).show()
            return
        }

        setHistoryTransferBusy(true)
        val filename = "track-record-history-${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        }.json"
        exportHistoryLauncher.launch(filename)
    }

    private fun exportHistoryToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val items = HistoryStorage.load(this@MainActivity)
            if (items.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    with(this@MainActivity) {
                        setHistoryTransferBusy(false)
                    Toast.makeText(this, "当前没有可导出的历史记录", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val result = runCatching {
                val payload = HistoryTransferCodec.encode(items)
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(payload)
                } ?: error("Unable to open export stream")
            }
            AppTaskExecutor.runOnMain {
                with(this@MainActivity) {
                    setHistoryTransferBusy(false)
                Toast.makeText(
                    this,
                    if (result.isSuccess) "历史记录已导出" else "导出失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
                }
            }
        }
    }

    private fun requestHistoryImport() {
        if (historyTransferBusy) return
        setHistoryTransferBusy(true)
        importHistoryLauncher.launch(arrayOf("application/json", "text/plain", "text/*"))
    }

    private fun importHistoryFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val importedItems = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    HistoryTransferCodec.decode(reader.readText())
                } ?: emptyList()
            }.getOrDefault(emptyList())

            if (importedItems.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    with(this@MainActivity) {
                        setHistoryTransferBusy(false)
                    Toast.makeText(this, "导入失败，请确认备份文件格式", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val mergedItems = HistoryTransferCodec.merge(
                existingItems = HistoryStorage.load(this@MainActivity),
                importedItems = importedItems,
            )
            HistoryStorage.save(this@MainActivity, mergedItems)
            AppTaskExecutor.runOnMain {
                with(this@MainActivity) {
                    setHistoryTransferBusy(false)
                    homeMapController.shouldRefit = true
                    historyController.reload()
                    historyController.updateContent()
                    refreshDashboardContent()
                Toast.makeText(
                    this,
                    "已导入 ${importedItems.size} 条记录，当前共 ${mergedItems.size} 条",
                    Toast.LENGTH_SHORT,
                ).show()
                }
            }
        }
    }

    private fun setHistoryTransferBusy(isBusy: Boolean) {
        historyTransferBusy = isBusy
        historyController.setTransferBusy(isBusy)
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
        val previewLocation = loadLastKnownLocation()?.let(::toMapCoordinate)
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
        registerGnssCallback()
        refreshGpsStatus()
        historyController.reload()
        historyController.updateContent()
        homeMapController.shouldRefit = true
        refreshDashboardContent()
        permissionHelper.startBackgroundTrackingServiceIfReady()
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
