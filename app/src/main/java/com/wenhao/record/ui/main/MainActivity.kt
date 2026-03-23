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
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.baidu.mapapi.model.LatLng
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
import com.wenhao.record.map.CoordinateTransformUtils
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.stability.CrashLogStore
import com.wenhao.record.tracking.BackgroundTrackingService
import com.wenhao.record.tracking.LocationSelectionUtils
import com.wenhao.record.ui.dashboard.DashboardUiController
import com.wenhao.record.ui.history.HistoryController
import com.wenhao.record.ui.map.MapActivity
import com.wenhao.record.util.AppTaskExecutor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class DashboardTab {
        RECORD,
        HISTORY
    }

    private lateinit var dashboardUiController: DashboardUiController
    private lateinit var homeMapController: HomeMapController
    private var historyController: HistoryController? = null

    private val permissionHelper by lazy {
        PermissionHelper(
            activity = this,
            onRefreshGpsStatus = ::refreshGpsStatus,
            onLocateGranted = ::centerOnCurrentLocation,
            onRefreshDashboard = ::refreshDashboardContent,
            onStartBackgroundTracking = { BackgroundTrackingService.start(this) }
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
    private var previewLocationCache: LatLng? = null
    private var previewLocationCachedAt = 0L
    private var historyTransferBusy = false
    private var currentTab = DashboardTab.RECORD
    private var historyScreenStub: ViewStub? = null
    private var historyScreen: View? = null
    private var historyBottomNav: View? = null
    private var historyScreenTopPadding = 0
    private var historyBottomNavBottomPadding = 0
    private var systemBarTopInset = 0
    private var systemBarBottomInset = 0

    private val defaultLatLng = LatLng(39.9042, 116.4074)
    private val dataChangeListener = object : TrackDataChangeNotifier.Listener {
        override fun onDashboardDataChanged() {
            refreshDashboardContent()
        }

        override fun onHistoryDataChanged() {
            historyController?.let { controller ->
                controller.reload()
                controller.updateContent()
            }
            homeMapController.shouldRefit = true
            refreshDashboardContent()
        }
    }
    private val freshLocationListener = android.location.LocationListener(::handleLocationUpdate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        dashboardUiController = DashboardUiController(this)
        homeMapController = HomeMapController(this, dashboardUiController.mapView)
        historyScreenStub = findViewById(R.id.historyScreenStub)
        locationManager = getSystemService(LocationManager::class.java)

        configureHomeMap()
        initGnssStatusCallback()
        bindNavigation()
        refreshDashboardContent()
        showTab(DashboardTab.RECORD)
        refreshGpsStatus()
        permissionHelper.ensureSmartTrackingEnabled()
    }

    private fun bindNavigation() {
        dashboardUiController.bindNavigation(
            onRecordClick = { showTab(DashboardTab.RECORD) },
            onHistoryClick = { showTab(DashboardTab.HISTORY) },
            onLocateClick = {
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
        )
    }

    private fun configureHomeMap() {
        homeMapController.configure(
            previewLocation = loadPreviewLocation(forceRefresh = true),
            hasLocationPermission = permissionHelper.hasLocationPermission(),
            defaultLatLng = defaultLatLng
        )
    }

    private fun initGnssStatusCallback() {
        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onStarted() {
                dashboardUiController.updateGpsStatusBadge(
                    getString(R.string.dashboard_gps_searching),
                    R.color.dashboard_badge_yellow
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
                    usedInFixCount == 0 -> {
                        dashboardUiController.updateGpsStatusBadge(
                            getString(R.string.dashboard_gps_searching),
                            R.color.dashboard_badge_yellow
                        )
                    }

                    averageCn0 >= 20f -> {
                        dashboardUiController.updateGpsStatusBadge(
                            getString(R.string.dashboard_gps_ready),
                            R.color.dashboard_badge_green
                        )
                    }

                    else -> {
                        dashboardUiController.updateGpsStatusBadge(
                            getString(R.string.dashboard_gps_weak),
                            R.color.dashboard_badge_red
                        )
                    }
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
            todayHistoryItems = todayHistoryItems
        )
        renderDashboardDiagnosticsRefined()

        if (previousSessionStart != null && session == null) {
            homeMapController.shouldRefit = true
            historyController?.let { controller ->
                controller.reload()
                controller.updateContent()
            }
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
        } ?: getString(R.string.dashboard_diagnostics_saved_none)
        val crashSummary = CrashLogStore.latestSummary(this)

        val fullBody = buildString {
            append(getString(R.string.dashboard_diagnostics_permissions))
            append("：")
            append(permissionSummary)
            append('\n')
            append(getString(R.string.dashboard_diagnostics_service))
            append("：")
            append(diagnostics.serviceStatus)
            append('\n')
            append(getString(R.string.dashboard_diagnostics_event))
            append("：")
            append(eventSummary)
            append('\n')
            append(getString(R.string.dashboard_diagnostics_location))
            append("：")
            append(locationSummary)
            append('\n')
            append(getString(R.string.dashboard_diagnostics_saved))
            append("：")
            append(saveSummary)
            if (!crashSummary.isNullOrBlank()) {
                append('\n')
                append(getString(R.string.dashboard_diagnostics_crash))
                append("：")
                append(crashSummary)
            }
        }
        val compactBody = buildString {
            append(diagnostics.serviceStatus)
            append('\n')
            append(eventSummary)
            append('\n')
            append(
                if (diagnostics.lastLocationAt > 0L) {
                    buildTimedSummary(diagnostics.lastLocationAt, diagnostics.lastLocationDecision)
                } else {
                    getString(R.string.dashboard_diagnostics_waiting_signal)
                }
            )
            if (!crashSummary.isNullOrBlank()) {
                append('\n')
                append(crashSummary)
            }
        }
        dashboardUiController.updateDiagnostics(
            title = getString(R.string.dashboard_diagnostics_title),
            body = fullBody,
            compactBody = compactBody
        )
    }

    private fun showTab(tab: DashboardTab) {
        currentTab = tab
        val isRecord = tab == DashboardTab.RECORD
        dashboardUiController.setRecordContentVisible(isRecord)
        dashboardUiController.setRecordTabSelected(isRecord)
        historyController?.setVisible(!isRecord)
        historyController?.setTabSelected(isRecord)

        if (!isRecord) {
            ensureHistoryController().apply {
                reload()
                updateContent()
                setVisible(true)
                setTabSelected(false)
            }
            return
        }

        if (isRecord) {
            homeMapController.shouldRefit = true
            refreshDashboardContent()
            homeMapController.showPreviewLocationIfIdle(loadPreviewLocation())
        }
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

    private fun requestHistoryExport() {
        if (historyTransferBusy) return
        val items = HistoryStorage.peek(this)
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.history_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        setHistoryTransferBusy(true)
        val filename = "track-record-history-${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        }.json"
        exportHistoryLauncher.launch(filename)
    }

    private fun exportHistoryToUri(uri: Uri) {
        AppTaskExecutor.runOnIo {
            val items = HistoryStorage.load(this)
            if (items.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    setHistoryTransferBusy(false)
                    Toast.makeText(this, R.string.history_export_empty, Toast.LENGTH_SHORT).show()
                }
                return@runOnIo
            }

            val result = runCatching {
                val payload = HistoryTransferCodec.encode(items)
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(payload)
                } ?: error("Unable to open export stream")
            }
            AppTaskExecutor.runOnMain {
                setHistoryTransferBusy(false)
                Toast.makeText(
                    this,
                    if (result.isSuccess) R.string.history_export_success else R.string.history_export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun requestHistoryImport() {
        if (historyTransferBusy) return
        setHistoryTransferBusy(true)
        importHistoryLauncher.launch(arrayOf("application/json", "text/plain", "text/*"))
    }

    private fun importHistoryFromUri(uri: Uri) {
        AppTaskExecutor.runOnIo {
            val importedItems = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    HistoryTransferCodec.decode(reader.readText())
                } ?: emptyList()
            }.getOrDefault(emptyList())

            if (importedItems.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    setHistoryTransferBusy(false)
                    Toast.makeText(this, R.string.history_import_failed, Toast.LENGTH_SHORT).show()
                }
                return@runOnIo
            }

            val mergedItems = HistoryTransferCodec.merge(
                existingItems = HistoryStorage.load(this),
                importedItems = importedItems
            )
            HistoryStorage.save(this, mergedItems)
            AppTaskExecutor.runOnMain {
                setHistoryTransferBusy(false)
                homeMapController.shouldRefit = true
                historyController?.let { controller ->
                    controller.reload()
                    controller.updateContent()
                }
                refreshDashboardContent()
                Toast.makeText(
                    this,
                    getString(R.string.history_import_success, importedItems.size, mergedItems.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setHistoryTransferBusy(isBusy: Boolean) {
        historyTransferBusy = isBusy
        historyController?.setTransferBusy(isBusy)
    }

    private fun refreshGpsStatus() {
        when {
            !permissionHelper.hasLocationPermission() -> {
                dashboardUiController.updateGpsStatusBadge(
                    getString(R.string.dashboard_gps_no_permission),
                    R.color.dashboard_badge_gray
                )
            }

            !isLocationEnabled() -> {
                dashboardUiController.updateGpsStatusBadge(
                    getString(R.string.dashboard_gps_disabled),
                    R.color.dashboard_badge_red
                )
            }

            loadPreviewLocation() != null -> {
                dashboardUiController.updateGpsStatusBadge(
                    getString(R.string.dashboard_gps_ready),
                    R.color.dashboard_badge_green
                )
            }

            else -> {
                dashboardUiController.updateGpsStatusBadge(
                    getString(R.string.dashboard_gps_searching),
                    R.color.dashboard_badge_yellow
                )
            }
        }
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(android.R.id.content)
        val gpsStatusBadge = findViewById<View>(R.id.gpsStatusBadge)
        val diagnosticsCompact = findViewById<View>(R.id.layoutRecordDiagnosticsCompact)
        val locateButton = findViewById<View>(R.id.btnLocate)
        val dashboardPanel = findViewById<View>(R.id.dashboardPanel)
        val saveFeedbackCard = findViewById<View>(R.id.saveFeedbackCard)

        val gpsStatusLayout = gpsStatusBadge.layoutParams as FrameLayout.LayoutParams
        val gpsStatusStartMargin = gpsStatusLayout.marginStart
        val gpsStatusTopMargin = gpsStatusLayout.topMargin

        val diagnosticsCompactLayout = diagnosticsCompact.layoutParams as FrameLayout.LayoutParams
        val diagnosticsCompactStartMargin = diagnosticsCompactLayout.marginStart
        val diagnosticsCompactTopMargin = diagnosticsCompactLayout.topMargin

        val locateLayout = locateButton.layoutParams as FrameLayout.LayoutParams
        val locateEndMargin = locateLayout.marginEnd
        val locateBottomMargin = locateLayout.bottomMargin

        val saveFeedbackLayout = saveFeedbackCard.layoutParams as ViewGroup.MarginLayoutParams
        val saveFeedbackBottomMargin = saveFeedbackLayout.bottomMargin

        val dashboardPanelBottomPadding = dashboardPanel.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarTopInset = systemBars.top
            systemBarBottomInset = systemBars.bottom

            gpsStatusBadge.updateLayoutParams<FrameLayout.LayoutParams> {
                marginStart = gpsStatusStartMargin + systemBars.left
                topMargin = gpsStatusTopMargin + systemBars.top
            }

            diagnosticsCompact.updateLayoutParams<FrameLayout.LayoutParams> {
                marginStart = diagnosticsCompactStartMargin + systemBars.left
                topMargin = diagnosticsCompactTopMargin + systemBars.top
            }

            locateButton.updateLayoutParams<FrameLayout.LayoutParams> {
                marginEnd = locateEndMargin + systemBars.right
                bottomMargin = locateBottomMargin + systemBars.bottom
            }

            dashboardPanel.updatePadding(bottom = dashboardPanelBottomPadding + systemBars.bottom)
            saveFeedbackCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = saveFeedbackBottomMargin + systemBars.bottom
            }
            applyHistoryWindowInsets()

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun handleLocationUpdate(location: Location) {
        val previewLocation = convertToGcj02(location)
        previewLocationCache = previewLocation
        previewLocationCachedAt = System.currentTimeMillis()
        homeMapController.updateCurrentLocation(
            latLng = previewLocation,
            shouldCenter = centerOnNextFix
        )
        centerOnNextFix = false
        stopLocationUpdates()
    }

    private fun buildPermissionSummarySafe(): String {
        return when {
            !permissionHelper.hasLocationPermission() ->
                getString(R.string.permission_summary_location_missing)
            !permissionHelper.hasActivityRecognitionPermission() ->
                getString(R.string.permission_summary_activity_missing)
            permissionHelper.needsBackgroundLocationPermission() ->
                getString(R.string.permission_summary_background_missing)
            permissionHelper.needsNotificationPermission() ->
                getString(R.string.permission_summary_notification_limited)
            else ->
                getString(R.string.permission_summary_ready)
        }
    }

    private fun buildLocationSummary(diagnostics: AutoTrackDiagnostics): String {
        if (diagnostics.lastLocationAt <= 0L) {
            return getString(R.string.dashboard_diagnostics_no_fix)
        }

        return buildString {
            append(buildTimedSummary(diagnostics.lastLocationAt, diagnostics.lastLocationDecision))
            append("，")
            append(
                getString(
                    R.string.dashboard_diagnostics_points_collected,
                    diagnostics.acceptedPointCount
                )
            )
            diagnostics.lastLocationAccuracyMeters?.let { accuracy ->
                append("，")
                append(
                    getString(
                        R.string.dashboard_diagnostics_accuracy_estimate,
                        accuracy.toInt()
                    )
                )
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
            Manifest.permission.ACCESS_FINE_LOCATION
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

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation(): Location? {
        if (!permissionHelper.hasLocationPermission()) return null
        val manager = locationManager ?: return null
        return LocationSelectionUtils.loadBestLastKnownLocation(
            locationManager = manager,
            hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            maxAgeMs = 15 * 60 * 1000L
        )
    }

    private fun loadPreviewLocation(forceRefresh: Boolean = false): LatLng? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - previewLocationCachedAt < 5_000L) {
            return previewLocationCache
        }
        val previewLocation = loadLastKnownLocation()?.let(::convertToGcj02)
        previewLocationCache = previewLocation
        previewLocationCachedAt = now
        return previewLocation
    }

    private fun convertToGcj02(location: Location): LatLng {
        val coordinate = CoordinateTransformUtils.wgs84ToGcj02(
            latitude = location.latitude,
            longitude = location.longitude
        )
        return LatLng(coordinate.latitude, coordinate.longitude)
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

    private fun ensureHistoryController(): HistoryController {
        historyController?.let { return it }

        val rootView = historyScreen ?: historyScreenStub?.inflate() ?: findViewById(R.id.historyScreen)
        historyScreen = rootView
        historyScreenStub = null
        historyBottomNav = rootView.findViewById(R.id.historyPageBottomNav)
        historyScreenTopPadding = rootView.paddingTop
        historyBottomNavBottomPadding = historyBottomNav?.paddingBottom ?: 0
        applyHistoryWindowInsets()

        return HistoryController(this, rootView) { item: HistoryDayItem ->
            startActivity(MapActivity.createHistoryIntent(this, item.dayStartMillis))
        }.also { controller ->
            controller.bindNavigation(
                onRecordClick = { showTab(DashboardTab.RECORD) },
                onExportClick = ::requestHistoryExport,
                onImportClick = ::requestHistoryImport
            )
            controller.setTransferBusy(historyTransferBusy)
            controller.setVisible(currentTab == DashboardTab.HISTORY)
            controller.setTabSelected(currentTab == DashboardTab.RECORD)
            historyController = controller
        }
    }

    private fun applyHistoryWindowInsets() {
        historyScreen?.updatePadding(top = historyScreenTopPadding + systemBarTopInset)
        historyBottomNav?.updatePadding(bottom = historyBottomNavBottomPadding + systemBarBottomInset)
    }

    override fun onResume() {
        super.onResume()
        homeMapController.onResume()
        TrackDataChangeNotifier.addListener(dataChangeListener)
        registerGnssCallback()
        refreshGpsStatus()
        historyController?.let { controller ->
            controller.reload()
            controller.updateContent()
        }
        homeMapController.shouldRefit = true
        refreshDashboardContent()
        permissionHelper.startBackgroundTrackingServiceIfReady()
    }

    override fun onPause() {
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        unregisterGnssCallback()
        stopLocationUpdates()
        homeMapController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        dashboardUiController.onDestroy()
        unregisterGnssCallback()
        stopLocationUpdates()
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        homeMapController.onDestroy()
        super.onDestroy()
    }
}
