package com.wenhao.record.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.tracking.AutoTrackDiagnostics
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.CoordinateTransformUtils
import com.wenhao.record.map.MapMarkerIconFactory
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.tracking.BackgroundTrackingService
import com.wenhao.record.tracking.LocationSelectionUtils
import com.wenhao.record.ui.dashboard.DashboardUiController
import com.wenhao.record.ui.history.HistoryController
import com.wenhao.record.ui.map.MapActivity
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.Gradient
import com.baidu.mapapi.map.HeatMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.WeightedLatLng
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private enum class DashboardTab {
        RECORD,
        HISTORY
    }

    private lateinit var dashboardUiController: DashboardUiController
    private lateinit var historyController: HistoryController
    private lateinit var aMap: BaiduMap

    private val permissionHelper by lazy {
        PermissionHelper(
            activity = this,
            onRefreshGpsStatus = ::refreshGpsStatus,
            onLocateGranted = ::centerOnCurrentLocation,
            onRefreshDashboard = ::refreshDashboardContent,
            onStartBackgroundTracking = { BackgroundTrackingService.start(this) }
        )
    }

    private var locationManager: LocationManager? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var homeMarker: Marker? = null
    private var liveStartMarker: Marker? = null
    private var liveCurrentMarker: Marker? = null
    private var activeTrackPolyline: Polyline? = null
    private val todayTrackPolylines = mutableListOf<Polyline>()
    private var todayHeatMap: HeatMap? = null
    private var centerOnNextFix = false
    private var lastDashboardSessionStart: Long? = null
    private var shouldRefitDashboardMap = true

    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val todayTrackPoints = mutableListOf<LatLng>()
    private val defaultLatLng = LatLng(39.9042, 116.4074)
    private val dataChangeListener = object : TrackDataChangeNotifier.Listener {
        override fun onDashboardDataChanged() {
            refreshDashboardContent()
        }

        override fun onHistoryDataChanged() {
            historyController.reload()
            historyController.updateContent()
            shouldRefitDashboardMap = true
            refreshDashboardContent()
        }
    }
    private val freshLocationListener = android.location.LocationListener(::handleLocationUpdate)

    private val mapView
        get() = dashboardUiController.mapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        dashboardUiController = DashboardUiController(this)
        historyController = HistoryController(this) { item: HistoryDayItem ->
            startActivity(MapActivity.createHistoryIntent(this, item.dayStartMillis))
        }
        locationManager = getSystemService(LocationManager::class.java)

        historyController.reload()
        configureHomeMap()
        initGnssStatusCallback()
        bindNavigation()
        refreshDashboardContent()
        historyController.updateContent()
        showTab(DashboardTab.RECORD)
        refreshGpsStatus()
        permissionHelper.ensureSmartTrackingEnabled()
    }

    private fun bindNavigation() {
        dashboardUiController.bindNavigation(
            onRecordClick = { showTab(DashboardTab.RECORD) },
            onHistoryClick = { showTab(DashboardTab.HISTORY) },
            onLocateClick = {
                if (currentTrackPoints.isNotEmpty()) {
                    fitActiveTrackToMap(forceSinglePointZoom = true)
                } else if (todayTrackPoints.isNotEmpty()) {
                    fitTodayTracksToMap(forceSinglePointZoom = true)
                } else {
                    centerOnCurrentLocation()
                }
            }
        )
        historyController.bindNavigation {
            showTab(DashboardTab.RECORD)
        }
    }

    private fun configureHomeMap() {
        aMap = mapView.map
        mapView.showZoomControls(false)
        mapView.showScaleControl(false)
        aMap.uiSettings.setCompassEnabled(false)
        aMap.uiSettings.setRotateGesturesEnabled(false)
        aMap.uiSettings.setOverlookingGesturesEnabled(false)
        aMap.setBaiduHeatMapEnabled(aMap.isSupportBaiduHeatMap())

        val previewLocation = loadPreviewLocation() ?: defaultLatLng
        updateMarker(previewLocation)
        aMap.setMapStatus(
            MapStatusUpdateFactory.newLatLngZoom(
                previewLocation,
                if (permissionHelper.hasLocationPermission()) 16f else 15f
            )
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
        val session = AutoTrackStorage.loadSession(this)
        val previousSessionStart = lastDashboardSessionStart
        lastDashboardSessionStart = session?.startTimestamp
        val dashboardState = currentDashboardState(session)
        val durationSeconds = session?.let {
            ((System.currentTimeMillis() - it.startTimestamp) / 1000L).toInt()
        } ?: 0

        dashboardUiController.render(session, dashboardState, durationSeconds)
        renderDashboardTrack(session)
        renderDashboardDiagnosticsRefined()

        if (previousSessionStart != null && session == null) {
            shouldRefitDashboardMap = true
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

    private fun renderDashboardTrack(session: AutoTrackSession?) {
        currentTrackPoints.clear()
        currentTrackPoints.addAll(session?.points.orEmpty())

        if (currentTrackPoints.isEmpty()) {
            clearActiveTrack()
            renderTodayTracks()
            loadPreviewLocation()?.let(::updateMarker)
            return
        }

        clearTodayTrackOverlays()
        removeTodayHeatMap()
        renderTrackPolyline(shouldFitMap = false)
        updateLiveTrackMarkers()
        updateMarker(currentTrackPoints.last().toLatLng())
        if (shouldRefitDashboardMap) {
            fitActiveTrackToMap(forceSinglePointZoom = false)
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
        }
        dashboardUiController.updateDiagnostics(
            title = getString(R.string.dashboard_diagnostics_title),
            body = fullBody,
            compactBody = compactBody
        )
    }

    private fun showTab(tab: DashboardTab) {
        val isRecord = tab == DashboardTab.RECORD
        dashboardUiController.setRecordContentVisible(isRecord)
        historyController.setVisible(!isRecord)
        dashboardUiController.setRecordTabSelected(isRecord)
        historyController.setTabSelected(isRecord)

        if (isRecord) {
            shouldRefitDashboardMap = true
            refreshDashboardContent()
            if (currentTrackPoints.isEmpty()) {
                loadPreviewLocation()?.let(::updateMarker)
            }
        }
    }

    private fun centerOnCurrentLocation() {
        if (!permissionHelper.hasLocationPermission()) {
            permissionHelper.requestLocatePermissionOrRun()
            return
        }

        if (!isLocationEnabled()) {
            refreshGpsStatus()
            android.widget.Toast.makeText(this, R.string.location_service_disabled, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        centerOnNextFix = true
        requestFreshLocationUpdates()

        loadPreviewLocation()?.let {
            updateMarker(it)
            aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(it, 17f))
        } ?: android.widget.Toast.makeText(this, R.string.location_unavailable, android.widget.Toast.LENGTH_SHORT).show()
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
        val historyScreen = findViewById<View>(R.id.historyScreen)
        val historyBottomNav = findViewById<View>(R.id.historyPageBottomNav)

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
        val historyScreenTopPadding = historyScreen.paddingTop
        val historyBottomNavBottomPadding = historyBottomNav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

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
            historyScreen.updatePadding(top = historyScreenTopPadding + systemBars.top)
            historyBottomNav.updatePadding(bottom = historyBottomNavBottomPadding + systemBars.bottom)

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun handleLocationUpdate(location: Location) {
        val gcj02LatLng = convertToGcj02(location)
        updateMarker(gcj02LatLng)
        if (centerOnNextFix) {
            centerOnNextFix = false
            aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(gcj02LatLng, 17f))
        }
        stopLocationUpdates()
    }

    private fun updateMarker(latLng: LatLng) {
        if (currentTrackPoints.isNotEmpty()) {
            homeMarker?.remove()
            homeMarker = null
            if (liveCurrentMarker == null) {
                liveCurrentMarker = aMap.addOverlay(
                    MarkerOptions()
                        .position(latLng)
                        .anchor(0.5f, 0.5f)
                        .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_end_marker))
                ) as Marker
            } else {
                liveCurrentMarker?.position = latLng
            }
            return
        }

        liveStartMarker?.remove()
        liveStartMarker = null
        liveCurrentMarker?.remove()
        liveCurrentMarker = null

        if (homeMarker == null) {
            homeMarker = aMap.addOverlay(
                MarkerOptions()
                    .position(latLng)
                    .anchor(0.5f, 0.5f)
                    .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_location))
            ) as Marker
        } else {
            homeMarker?.position = latLng
        }
    }

    private fun renderTrackPolyline(shouldFitMap: Boolean) {
        val latLngPoints = currentTrackPoints.map { it.toLatLng() }
        if (latLngPoints.size < 2) {
            activeTrackPolyline?.remove()
            activeTrackPolyline = null
            if (shouldFitMap) {
                fitActiveTrackToMap(forceSinglePointZoom = false)
            }
            return
        }

        if (activeTrackPolyline == null) {
            activeTrackPolyline = aMap.addOverlay(
                PolylineOptions()
                    .points(latLngPoints)
                    .color(Color.parseColor("#8B5CF6"))
                    .width(12)
            ) as Polyline
        } else {
            activeTrackPolyline?.points = latLngPoints
        }

        if (shouldFitMap) {
            fitActiveTrackToMap(forceSinglePointZoom = false)
        }
    }

    private fun clearActiveTrack() {
        activeTrackPolyline?.remove()
        activeTrackPolyline = null
        liveStartMarker?.remove()
        liveStartMarker = null
        liveCurrentMarker?.remove()
        liveCurrentMarker = null
    }

    private fun clearTodayTrackOverlays() {
        todayTrackPolylines.forEach { polyline -> polyline.remove() }
        todayTrackPolylines.clear()
        todayTrackPoints.clear()
    }

    private fun removeTodayHeatMap() {
        todayHeatMap?.removeHeatMap()
        todayHeatMap = null
    }

    private fun renderTodayTracks() {
        clearTodayTrackOverlays()
        removeTodayHeatMap()

        val todayHistoryItems = HistoryStorage.load(this).filter { item ->
            isSameDay(item.timestamp, System.currentTimeMillis())
        }

        todayHistoryItems.forEach { item ->
            val points = item.points.map { point -> point.toLatLng() }
            todayTrackPoints.addAll(points)
            if (points.size > 1) {
                val polyline = aMap.addOverlay(
                    PolylineOptions()
                        .points(points)
                        .color(Color.parseColor("#7A8B5CF6"))
                        .width(10)
                ) as Polyline
                todayTrackPolylines += polyline
            }
        }

        renderTodayHeatMap(todayHistoryItems.flatMap { item -> item.points })

        if (todayTrackPoints.isNotEmpty() && shouldRefitDashboardMap) {
            fitTodayTracksToMap(forceSinglePointZoom = false)
        }
    }

    private fun renderTodayHeatMap(points: List<TrackPoint>) {
        if (!aMap.isSupportBaiduHeatMap() || points.size < 3) {
            return
        }

        val weightedPoints = points.map { point ->
            WeightedLatLng(point.toLatLng())
        }
        val gradient = Gradient(
            intArrayOf(
                Color.parseColor("#33D9CCFF"),
                Color.parseColor("#8A8B5CF6"),
                Color.parseColor("#F26E47D7")
            ),
            floatArrayOf(0.2f, 0.6f, 1f)
        )
        todayHeatMap = HeatMap.Builder()
            .weightedData(weightedPoints)
            .radius(32)
            .opacity(0.72)
            .gradient(gradient)
            .build()
        todayHeatMap?.let(aMap::addHeatMap)
    }

    private fun updateLiveTrackMarkers() {
        val firstPoint = currentTrackPoints.firstOrNull()?.toLatLng() ?: return
        val lastPoint = currentTrackPoints.lastOrNull()?.toLatLng() ?: return

        if (liveStartMarker == null) {
            liveStartMarker = aMap.addOverlay(
                MarkerOptions()
                    .position(firstPoint)
                    .anchor(0.5f, 0.5f)
                    .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_start_marker))
            ) as Marker
        } else {
            liveStartMarker?.position = firstPoint
        }

        if (liveCurrentMarker == null) {
            liveCurrentMarker = aMap.addOverlay(
                MarkerOptions()
                    .position(lastPoint)
                    .anchor(0.5f, 0.5f)
                    .icon(MapMarkerIconFactory.fromDrawableResource(this, R.drawable.ic_route_end_marker))
            ) as Marker
        } else {
            liveCurrentMarker?.position = lastPoint
        }
    }

    private fun fitActiveTrackToMap(forceSinglePointZoom: Boolean) {
        mapView.post {
            val points = currentTrackPoints.map { it.toLatLng() }
            when {
                points.isEmpty() -> Unit
                points.size == 1 -> {
                    if (forceSinglePointZoom || centerOnNextFix) {
                        aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(points.first(), 17f))
                    }
                    shouldRefitDashboardMap = false
                }

                else -> {
                    val bounds = LatLngBounds.Builder().apply {
                        points.forEach(::include)
                    }.build()
                    aMap.setViewPadding(dpToPx(20), dpToPx(28), dpToPx(20), dpToPx(82))
                    aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds))
                    shouldRefitDashboardMap = false
                }
            }
        }
    }

    private fun fitTodayTracksToMap(forceSinglePointZoom: Boolean) {
        mapView.post {
            when {
                todayTrackPoints.isEmpty() -> Unit
                todayTrackPoints.size == 1 -> {
                    if (forceSinglePointZoom || shouldRefitDashboardMap) {
                        aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(todayTrackPoints.first(), 17f))
                    }
                    shouldRefitDashboardMap = false
                }

                else -> {
                    val bounds = LatLngBounds.Builder().apply {
                        todayTrackPoints.forEach(::include)
                    }.build()
                    aMap.setViewPadding(dpToPx(20), dpToPx(28), dpToPx(20), dpToPx(82))
                    aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds))
                    shouldRefitDashboardMap = false
                }
            }
        }
    }

    private fun isSameDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
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
            android.widget.Toast.makeText(this, R.string.location_service_disabled, android.widget.Toast.LENGTH_SHORT).show()
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

    private fun loadPreviewLocation(): LatLng? = loadLastKnownLocation()?.let(::convertToGcj02)

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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        TrackDataChangeNotifier.addListener(dataChangeListener)
        registerGnssCallback()
        refreshGpsStatus()
        historyController.reload()
        historyController.updateContent()
        shouldRefitDashboardMap = true
        refreshDashboardContent()
        permissionHelper.startBackgroundTrackingServiceIfReady()
    }

    override fun onPause() {
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        unregisterGnssCallback()
        stopLocationUpdates()
        mapView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        dashboardUiController.onDestroy()
        unregisterGnssCallback()
        stopLocationUpdates()
        clearActiveTrack()
        clearTodayTrackOverlays()
        removeTodayHeatMap()
        homeMarker?.remove()
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
