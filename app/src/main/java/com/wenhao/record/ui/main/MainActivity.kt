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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wenhao.record.R
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.MapMarkerIconFactory
import com.wenhao.record.map.CoordinateTransformUtils
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.tracking.BackgroundTrackingService
import com.wenhao.record.ui.dashboard.DashboardUiController
import com.wenhao.record.ui.history.HistoryController
import com.wenhao.record.ui.map.MapActivity
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds

class MainActivity : AppCompatActivity() {

    private companion object {
        const val DASHBOARD_REFRESH_INTERVAL_MS = 3_000L
    }

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
    private var centerOnNextFix = false
    private var lastDashboardSessionStart: Long? = null

    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val defaultLatLng = LatLng(39.9042, 116.4074)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val dashboardRefreshRunnable = object : Runnable {
        override fun run() {
            refreshDashboardContent()
            uiHandler.postDelayed(this, DASHBOARD_REFRESH_INTERVAL_MS)
        }
    }
    private val freshLocationListener = android.location.LocationListener { location ->
        handleLocationUpdate(location)
    }

    private val mapView
        get() = dashboardUiController.mapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dashboardUiController = DashboardUiController(this)
        historyController = HistoryController(this) { item ->
            startActivity(MapActivity.createHistoryIntent(this, item.id))
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
                    "\u6b63\u5728\u641c\u7d22 GPS",
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
                            "\u6b63\u5728\u641c\u7d22 GPS",
                            R.color.dashboard_badge_yellow
                        )
                    }

                    averageCn0 >= 20f -> {
                        dashboardUiController.updateGpsStatusBadge(
                            "GPS \u5df2\u5c31\u7eea",
                            R.color.dashboard_badge_green
                        )
                    }

                    else -> {
                        dashboardUiController.updateGpsStatusBadge(
                            "\u4fe1\u53f7\u8f83\u5f31",
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

        if (previousSessionStart != null && session == null) {
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
            loadPreviewLocation()?.let(::updateMarker)
            return
        }

        renderTrackPolyline(shouldFitMap = false)
        updateLiveTrackMarkers()
        updateMarker(currentTrackPoints.last().toLatLng())
    }

    private fun showTab(tab: DashboardTab) {
        val isRecord = tab == DashboardTab.RECORD
        dashboardUiController.setRecordContentVisible(isRecord)
        historyController.setVisible(!isRecord)
        dashboardUiController.setRecordTabSelected(isRecord)
        historyController.setTabSelected(isRecord)

        if (isRecord) {
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
                    "\u672a\u6388\u4e88\u5b9a\u4f4d\u6743\u9650",
                    R.color.dashboard_badge_gray
                )
            }

            !isLocationEnabled() -> {
                dashboardUiController.updateGpsStatusBadge(
                    "\u5b9a\u4f4d\u672a\u5f00\u542f",
                    R.color.dashboard_badge_red
                )
            }

            loadPreviewLocation() != null -> {
                dashboardUiController.updateGpsStatusBadge(
                    "GPS \u5df2\u5c31\u7eea",
                    R.color.dashboard_badge_green
                )
            }

            else -> {
                dashboardUiController.updateGpsStatusBadge(
                    "\u6b63\u5728\u641c\u7d22 GPS",
                    R.color.dashboard_badge_yellow
                )
            }
        }
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
                }

                else -> {
                    val bounds = LatLngBounds.Builder().apply {
                        points.forEach(::include)
                    }.build()
                    aMap.setViewPadding(dpToPx(20), dpToPx(28), dpToPx(20), dpToPx(82))
                    aMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds))
                }
            }
        }
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
        val providers = collectEnabledProviders(manager)
        return providers.mapNotNull(manager::getLastKnownLocation).maxByOrNull(Location::getTime)
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
        registerGnssCallback()
        refreshGpsStatus()
        historyController.reload()
        historyController.updateContent()
        refreshDashboardContent()
        permissionHelper.startBackgroundTrackingServiceIfReady()
        uiHandler.removeCallbacks(dashboardRefreshRunnable)
        uiHandler.post(dashboardRefreshRunnable)
    }

    override fun onPause() {
        unregisterGnssCallback()
        uiHandler.removeCallbacks(dashboardRefreshRunnable)
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
        uiHandler.removeCallbacks(dashboardRefreshRunnable)
        stopLocationUpdates()
        clearActiveTrack()
        homeMarker?.remove()
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
